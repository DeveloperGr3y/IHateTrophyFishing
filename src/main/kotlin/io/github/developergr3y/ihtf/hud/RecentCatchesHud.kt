package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.config.CurrentlyTargetingConfig
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.trophy.Trophies
import io.github.developergr3y.ihtf.trophy.TrophyTier

/**
 * "Currently Targeting": every trophy fish caught in the last few minutes.
 * Handy when stacking conditions for several fish at once, to see which ones are actually coming in.
 * Recent catches are kept in memory only, so this starts empty each time you launch the game.
 */
object RecentCatchesHud : HudElement("Currently Targeting") {
    private val config get() = IHateTrophyFishing.config.trackers.currentlyTargeting

    override val position get() = config.position

    override fun resetPosition() {
        config.position = CurrentlyTargetingConfig().position
    }

    override fun isVisible(inInventory: Boolean) = config.enabled

    override fun build(inInventory: Boolean): List<HudLine> {
        val now = System.currentTimeMillis()
        val windowMs = config.windowMinutes * 60_000L
        val profile = Storage.profile()
        val recent = Tracker.recentCatches.filter { now - it.time <= windowMs && !config.hideOwned.hides(it.key, profile) }

        val lines = mutableListOf(HudLine("§e§lCurrently Targeting §7(last ${config.windowMinutes}m)"))
        if (recent.isEmpty()) {
            lines += HudLine("§7No trophies caught in the last ${config.windowMinutes}m.")
            return lines
        }

        // Most recently caught first.
        for ((key, catches) in recent.groupBy { it.key }.entries.sortedByDescending { e -> e.value.maxOf { it.time } }) {
            val countColour = if (Tracker.isFlashing(key)) "§a" else "§7"
            val best = catches.maxOf { it.tier }
            val bestText = if (best > TrophyTier.BRONZE) " §8· best ${best.formatted}" else ""
            val ago = TrackerHud.formatDuration(now - catches.maxOf { it.time })
            lines += HudLine("${Trophies.colouredName(key, profile)} ${countColour}x${catches.size}$bestText §8· $ago ago", iconKey = key)
        }
        return lines
    }
}
