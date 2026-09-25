package io.github.developergr3y.ihtf.odds

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.hud.OddsHud
import io.github.developergr3y.ihtf.trophy.stripFormatting
import io.github.developergr3y.ihtf.util.Compat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Reads everything that boosts Gold / Diamond odds, the same way the rest of the mod reads trophy data:
 *  - your rod (Charm) and helmet (Froggles), from the items themselves;
 *  - Marigold's and Gemma's shops (Midas Lure, Radiant Fisher), /pets and the Hunting Box, while they're open;
 *  - which pet is out, from the summon / Autopet chat messages and the tab list's Pet widget.
 */
object OddsImport {
    private val charm = Regex("\\bCharm (VI|IV|V|III|II|I)\\b")
    private val romanValues = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6)

    private val petName = Regex("^\\[Lvl (\\d+)] (.+?)(?: ✦)?$")
    private val heldItemLine = Regex("Held Item: (.+?)\\s*$")
    private val petBoost = Regex("chance of catching GOLD and DIAMOND (?:tier )?Trophy (Fish|Frogs?) by ([\\d.]+)%")
    private val perkTier = Regex("(?:Tier|Level):? (\\d+)\\s*/\\s*10")
    private val perkPercent = Regex("tier Trophies by ([\\d.]+)%")
    private val frogAttribute = Regex("Trophy Frogs are \\+?([\\d.]+)% more likely to be (GOLD|DIAMOND)")

    private val summoned = Regex("^You summoned your (.+?)(?: ✦)?!$")
    private val despawned = Regex("^You despawned your (.+?)(?: ✦)?!$")
    private val autopet = Regex("^Autopet equipped your \\[Lvl (\\d+)] (.+?)(?: ✦)?! VIEW RULE$")

    /** The helmet you're wearing, if it's Golden or Diamond Froggles. Checked live, not saved. */
    var froggles: String? = null
        private set

    private var lastItemCheck = 0L
    private var lastSnapshot: String? = null

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        val now = System.currentTimeMillis()
        if (now - lastItemCheck >= 1_000) {
            lastItemCheck = now
            readEquipment(player.mainHandItem, player.getItemBySlot(EquipmentSlot.HEAD))
            readTabList(client)
        }
        readMenu()
    }

    private fun readEquipment(hand: ItemStack, head: ItemStack) {
        val data = TrophyOdds.data
        if (hand.`is`(Items.FISHING_ROD)) {
            val level = lore(hand).firstNotNullOfOrNull { charm.find(it) }?.let { romanValues[it.groupValues[1]] } ?: 0
            if (data.charm != level) update { data.charm = level }
        }
        val helmet = head.hoverName.string.stripFormatting()
        val wearing = listOf("Diamond Froggles", "Golden Froggles").firstOrNull { it in helmet }
        if (wearing != froggles) {
            froggles = wearing
            OddsHud.refresh()
        }
    }

    /** Hypixel's tab list Pet widget: a line like "[Lvl 100] Spinosaurus". */
    private fun readTabList(client: Minecraft) {
        val players = client.connection?.listedOnlinePlayers ?: return
        for (info in players) {
            val line = info.tabListDisplayName?.string?.stripFormatting()?.trim() ?: continue
            val match = petName.find(line) ?: continue
            setActivePet(match.groupValues[2], match.groupValues[1].toInt())
            return
        }
    }

    fun onChat(text: String) {
        summoned.find(text)?.let { return setActivePet(it.groupValues[1], null) }
        autopet.find(text)?.let { return setActivePet(it.groupValues[2], it.groupValues[1].toInt()) }
        despawned.find(text)?.let { if (TrophyOdds.data.activePet == it.groupValues[1]) update { TrophyOdds.data.activePet = "" } }
    }

    private fun setActivePet(name: String, level: Int?) {
        val data = TrophyOdds.data
        val pet = data.pets[name]
        if (data.activePet == name && (level == null || pet == null || pet.level == level)) return
        update {
            data.activePet = name
            // Level-ups change the ability a little; the value itself is re-read next time /pets is opened.
            if (level != null && pet != null) pet.level = level
        }
    }

    private fun readMenu() {
        val screen = Compat.screen as? AbstractContainerScreen<*>
        if (screen == null) {
            lastSnapshot = null
            return
        }
        val items = screen.menu.slots.filter { it.container !is Inventory && !it.item.isEmpty }.map { it.item }
        val title = screen.title.string.stripFormatting()
        val snapshot = title + items.joinToString { it.hoverName.string + lore(it).size }
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot

        val data = TrophyOdds.data
        var changed = false
        var inHuntingBox = title.contains("Hunting Box", ignoreCase = true)

        for (stack in items) {
            val name = stack.hoverName.string.stripFormatting().trim()
            val lore = lore(stack)
            val text = lore.joinToString(" ")

            petName.find(name)?.takeIf { text.contains("Click to summon") || text.contains("Click to despawn") }?.let { match ->
                val pet = PetInfo().apply {
                    level = match.groupValues[1].toInt()
                    heldItem = lore.firstNotNullOfOrNull { heldItemLine.find(it) }?.groupValues?.get(1)
                    petBoost.find(text)?.let {
                        val value = it.groupValues[2].toDouble()
                        if (it.groupValues[1] == "Fish") fishBoost = value else frogBoost = value
                    }
                }
                val petKey = match.groupValues[2]
                val active = text.contains("Click to despawn")
                // Several pets can share a name; keep the one that's out, otherwise the highest level.
                val existing = data.pets[petKey]
                if (active || existing == null || (data.activePet != petKey && pet.level > existing.level)) {
                    data.pets[petKey] = pet
                    changed = true
                }
                if (active && data.activePet != petKey) {
                    data.activePet = petKey
                    changed = true
                }
            }

            if (name.startsWith("Midas Lure") || name.startsWith("Radiant Fisher")) {
                val percent = perkTier.find(text)?.let { it.groupValues[1].toDouble() * 2 }
                    ?: perkPercent.find(text)?.groupValues?.get(1)?.toDouble()
                if (percent != null) {
                    IHateTrophyFishing.logger.info("Read $name as $percent% from: $text")
                    if (name.startsWith("Midas")) data.midasLure = percent else data.radiantFisher = percent
                    changed = true
                }
            }

            frogAttribute.find(text)?.let {
                inHuntingBox = true
                val value = it.groupValues[1].toDouble()
                if (it.groupValues[2] == "GOLD") data.goldenFrog = value else data.diamondFrog = value
                changed = true
            }
        }
        // Attributes you have no shards in don't show, so opening the Hunting Box means they're 0.
        if (inHuntingBox) {
            if (data.goldenFrog == null) data.goldenFrog = 0.0
            if (data.diamondFrog == null) data.diamondFrog = 0.0
            changed = true
        }
        if (changed) {
            Storage.markDirty()
            OddsHud.refresh()
        }
    }

    private fun lore(stack: ItemStack) =
        stack.get(DataComponents.LORE)?.lines()?.map { it.string.stripFormatting() } ?: emptyList()

    private inline fun update(block: () -> Unit) {
        block()
        Storage.markDirty()
        OddsHud.refresh()
    }
}
