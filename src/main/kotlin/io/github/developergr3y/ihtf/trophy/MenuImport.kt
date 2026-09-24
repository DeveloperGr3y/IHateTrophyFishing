package io.github.developergr3y.ihtf.trophy

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.features.achievements.Achievements
import io.github.developergr3y.ihtf.hud.HudManager
import io.github.developergr3y.ihtf.util.Compat
import io.github.developergr3y.ihtf.util.firstColour
import io.github.developergr3y.ihtf.util.toLegacyString
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory

/**
 * Reads trophy data from Hypixel menus while you have them open:
 *
 *  - Collection menus (Odger's "Trophy Fish", Researcher Ribery's frogs): each trophy has lore lines like
 *    "Gold ✔ (1)" or "Diamond ✖". Gives all-time counts, including catches from before this mod.
 *  - /pity menus (e.g. "Lotus Atoll Pity"): lore lines like "Progress to GOLD: 37/100". Gives exact pity progress.
 *
 * Menus are recognised by those lines on known trophies rather than by title, so it keeps working if a title changes.
 */
object MenuImport {
    private val tierLine = Regex("^\\s*(Bronze|Silver|Gold|Diamond) ([✔✖])(?: \\(([\\d,]+)\\))?")
    private val pityLine = Regex("^\\s*Progress to (GOLD|DIAMOND): ([\\d,]+)/([\\d,]+)")

    /** What we last imported from the open menu; its items arrive over a few ticks, so only re-apply on change. */
    private var lastSnapshot: String? = null

    fun tick() {
        val screen = Compat.screen as? AbstractContainerScreen<*>
        if (screen == null) {
            lastSnapshot = null
            return
        }

        val counts = mutableMapOf<String, FishCounts>()
        val pity = mutableMapOf<String, PityProgress>()
        val colours = mutableMapOf<String, String>()

        for (slot in screen.menu.slots) {
            if (slot.container is Inventory) continue
            val stack = slot.item
            if (stack.isEmpty) continue
            val name = stack.hoverName.string.stripFormatting().trim()
            val key = Trophies.key(name)
            if (Trophies[key] == null) continue
            val lore = stack.get(DataComponents.LORE)?.lines()?.map { it.string.stripFormatting() } ?: continue

            readCounts(lore)?.let { counts[key] = it }
            readPity(lore)?.let { pity[key] = it }
            firstColour(stack.hoverName.toLegacyString())?.let { colours[key] = it }
        }
        if (counts.isEmpty() && pity.isEmpty()) return

        val snapshot = counts.entries.joinToString { (k, c) -> "$k:" + TrophyTier.entries.joinToString(",") { c[it].toString() } } +
            pity.entries.joinToString { (k, p) -> "$k:${p.gold}/${p.goldTotal},${p.diamond}/${p.diamondTotal}" }
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot

        apply(counts, pity, colours)
    }

    private fun readCounts(lore: List<String>): FishCounts? {
        val counts = FishCounts()
        var found = false
        for (line in lore) {
            val match = tierLine.find(line) ?: continue
            val tier = TrophyTier.fromName(match.groupValues[1]) ?: continue
            found = true
            counts[tier] = if (match.groupValues[2] == "✖") 0 else match.groupValues[3].toCount() ?: 1
        }
        return counts.takeIf { found }
    }

    private fun readPity(lore: List<String>): PityProgress? {
        val pity = PityProgress()
        var found = false
        for (line in lore) {
            val match = pityLine.find(line) ?: continue
            val progress = match.groupValues[2].toCount() ?: continue
            val total = match.groupValues[3].toCount() ?: continue
            found = true
            if (match.groupValues[1] == "GOLD") {
                pity.gold = progress
                pity.goldTotal = total
            } else {
                pity.diamond = progress
                pity.diamondTotal = total
            }
        }
        return pity.takeIf { found }
    }

    private fun String.toCount() = replace(",", "").toIntOrNull()

    private fun apply(counts: Map<String, FishCounts>, pity: Map<String, PityProgress>, colours: Map<String, String>) {
        val profile = Storage.profile()
        profile.colours.putAll(colours)

        val changedCounts = counts.count { (key, value) ->
            val existing = profile.lifetime[key]
            (existing == null || !existing.sameAs(value)).also { if (it) profile.lifetime[key] = value }
        }
        val newlySynced = counts.keys.map { if (Trophies.isFrog(it)) "frog" else "fish" }.toSet() - profile.collectionsSynced
        profile.collectionsSynced += newlySynced
        profile.pity.putAll(pity)

        Storage.markDirty()
        HudManager.refreshAll()
        Achievements.onSynced()
        for (kind in newlySynced) {
            IHateTrophyFishing.chat("§aSynced which trophy ${if (kind == "frog") "frogs" else "fish"} you own ✔")
        }
        if (changedCounts > 0 && newlySynced.isEmpty()) IHateTrophyFishing.chat("Updated $changedCounts trophy counts.")
        if (pity.isNotEmpty()) {
            val kind = if (pity.keys.all { Trophies.isFrog(it) }) "trophy frog" else "trophy fish"
            IHateTrophyFishing.chat("§aSynced $kind pity progress (${pity.size}) ✔")
        }
    }
}
