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
 *  - Marigold's and Gemma's shops (Midas Lure, Radiant Fisher), /pets and the Attribute Menu (or Hunting Box),
 *    while they're open;
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

    /** When your pet or helmet last changed; swap tips wait a while after that. */
    var setupSince = System.currentTimeMillis()
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
            if (data.charm != level) {
                if (data.charm == null) {
                    IHateTrophyFishing.chat("§aSynced Charm from your rod (${if (level > 0) "Charm " + TrophyOdds.roman(level) else "none"}) ✔")
                }
                update { data.charm = level }
            }
        }
        val helmet = head.hoverName.string.stripFormatting()
        val wearing = listOf("Diamond Froggles", "Golden Froggles").firstOrNull { it in helmet }
        if (wearing != froggles) {
            froggles = wearing
            setupSince = System.currentTimeMillis()
            if (wearing != null && data.ownedFroggles.add(wearing)) Storage.markDirty()
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
        despawned.find(text)?.let {
            if (TrophyOdds.data.activePet != it.groupValues[1]) return
            setupSince = System.currentTimeMillis()
            update { TrophyOdds.data.activePet = "" }
        }
    }

    private fun setActivePet(name: String, level: Int?) {
        val data = TrophyOdds.data
        val pet = data.pets[name]
        if (data.activePet == name && (level == null || pet == null || pet.level == level)) return
        setupSince = System.currentTimeMillis()
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
        val messages = mutableListOf<String>()
        var petsChanged = false
        var inAttributes = title.contains("Attribute", ignoreCase = true) || title.contains("Hunting Box", ignoreCase = true)
        val frogBefore = data.goldenFrog to data.diamondFrog

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
                val keep = active || existing == null || (data.activePet != petKey && pet.level > existing.level)
                if (keep && (existing == null || !existing.sameAs(pet))) {
                    data.pets[petKey] = pet
                    petsChanged = true
                    if (active) {
                        messages += "§aSynced your $petKey (Lvl ${pet.level}${pet.heldItem?.let { ", $it" } ?: ""}) ✔"
                    }
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
                    val midas = name.startsWith("Midas")
                    val before = if (midas) data.midasLure else data.radiantFisher
                    if (before != percent) {
                        if (midas) data.midasLure = percent else data.radiantFisher = percent
                        changed = true
                        val perk = if (midas) "Midas Lure" else "Radiant Fisher"
                        val tier = (percent / 2).toInt()
                        val boost = if (midas) "§6+${percent.toInt()}% Gold" else "§b+${percent.toInt()}% Diamond"
                        messages += "§aSynced $perk ${if (tier > 0) TrophyOdds.roman(tier) else "(not bought)"} §7($boost§7) §a✔"
                    }
                }
            }

            frogAttribute.find(text)?.let {
                inAttributes = true
                val value = it.groupValues[1].toDouble()
                if (it.groupValues[2] == "GOLD") data.goldenFrog = value else data.diamondFrog = value
            }
        }
        // Attributes you have no shards in don't show, so opening the Attribute Menu or Hunting Box means they're 0.
        if (inAttributes) {
            if (data.goldenFrog == null) data.goldenFrog = 0.0
            if (data.diamondFrog == null) data.diamondFrog = 0.0
            if ((data.goldenFrog to data.diamondFrog) != frogBefore) {
                changed = true
                messages += "§aSynced frog shards §7(Golden Frog §6+${TrophyOdds.fmt(data.goldenFrog!!)}%§7, Diamond Frog §b+${TrophyOdds.fmt(data.diamondFrog!!)}%§7) §a✔"
            }
        }
        if (petsChanged) {
            changed = true
            if (messages.none { it.startsWith("§aSynced your") }) messages += "§aSynced your pets ✔"
        }
        if (changed) {
            Storage.markDirty()
            OddsHud.refresh()
        }
        messages.forEach { IHateTrophyFishing.chat(it) }
    }

    private fun PetInfo.sameAs(other: PetInfo) =
        level == other.level && heldItem == other.heldItem && fishBoost == other.fishBoost && frogBoost == other.frogBoost

    private fun lore(stack: ItemStack) =
        stack.get(DataComponents.LORE)?.lines()?.map { it.string.stripFormatting() } ?: emptyList()

    private inline fun update(block: () -> Unit) {
        block()
        Storage.markDirty()
        OddsHud.refresh()
    }
}
