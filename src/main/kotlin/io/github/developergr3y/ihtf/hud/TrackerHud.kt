package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.config.TrackerConfig
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.tracker.TrackerView
import io.github.developergr3y.ihtf.trophy.SyncStatus
import io.github.developergr3y.ihtf.trophy.Trophies
import io.github.developergr3y.ihtf.trophy.pityStatuses
import java.util.Locale

/** The main tracker: catches per hour per trophy fish / frog, pity counts and time until pity. */
object TrackerHud : HudElement("Trophy Tracker") {
    private const val RESET_COOLDOWN_MS = 3_000L

    private val config get() = IHateTrophyFishing.config.trackers.trophyFish
    private var lastReset = 0L

    override val position get() = config.position

    override fun resetPosition() {
        config.position = TrackerConfig().position
    }

    override val enabled get() = config.enabled

    override fun isVisible(inInventory: Boolean) =
        enabled && Visibility.allowed(config.where, config.showWhen, inInventory)

    override fun build(inInventory: Boolean): List<HudLine> {
        val view = config.view
        val period = Tracker.period(view)
        val profile = Storage.profile()
        val lines = mutableListOf<HudLine>()

        if (inInventory) lines += buttons(view)

        lines += HudLine("§e§lTrophy Tracker §7[§a$view§7]")
        val state = when {
            Tracker.manuallyPaused -> " §c(Paused!)"
            Tracker.isAfk -> " §7(AFK)"
            else -> ""
        }
        lines += HudLine("§7Active time: §b${formatDuration(period.activeMillis)}$state")

        // Fish first, then frogs; within each, by rarity with the rarest last.
        val caught = period.fish.entries
            .filter { it.value.total > 0 }
            .sortedWith(compareBy({ Trophies.isFrog(it.key) }, { Trophies.rank(it.key, profile) }, { profile.names[it.key] ?: it.key }))
        if (caught.isEmpty()) lines += HudLine("§7No trophies caught yet.")

        for ((isFrog, group) in caught.groupBy { Trophies.isFrog(it.key) }) {
            val total = group.sumOf { it.value.total }
            lines += HudLine("§7Trophy ${if (isFrog) "frogs" else "fish"}: §f$total ${formatRate(Tracker.perHour(total, period))}")
            val shown = group.filterNot { config.hideOwned.hides(it.key, profile) }
            val hidden = group.size - shown.size
            for ((key, counts) in shown) {
                val rate = Tracker.perHour(counts.total, period)
                val countColour = if (Tracker.isFlashing(key)) "§a" else "§7"
                lines += HudLine("${Trophies.colouredName(key, profile)} ${countColour}x${counts.total} ${formatRate(rate)}", iconKey = key)
                // The "/h" above is what you've actually caught; time until pity uses the steadier estimate.
                val estimate = Tracker.estimatedPerHour(key, period, profile)
                for (pity in pityStatuses(key, profile, config.showBothPities)) {
                    val time = when {
                        pity.catchesLeft == 0 -> "§anext catch!"
                        estimate == null -> "§7calculating…"
                        else -> "§e~" + formatDuration((pity.catchesLeft / estimate * 3_600_000).toLong())
                    }
                    lines += HudLine(" ${pity.tier.formatted} §7pity in §f${pity.catchesLeft} §7· Time until pity: $time", indent = true)
                }
            }
            if (hidden > 0) lines += HudLine("§8$hidden hidden (already have ${config.hideOwned.tierName})")
        }

        // Spell out exactly what to open for any kind (fish / frogs) we don't have data for yet.
        for (frog in caught.map { Trophies.isFrog(it.key) }.distinct()) {
            SyncStatus.hints(profile, frog).forEach { lines += HudLine(it) }
        }
        return lines
    }

    /** Shown at the top while your inventory is open, like SkyHanni's trackers. */
    private fun buttons(view: TrackerView) = buildList {
        add(HudLine("§7[Click to show §a[${view.next}]§7]") {
            config.view = view.next
            IHateTrophyFishing.saveConfig()
        })
        add(
            if (Tracker.manuallyPaused) HudLine("§a[Click to resume]") { Tracker.togglePause() }
            else HudLine("§e[Click to pause]") { Tracker.togglePause() },
        )
        if (view == TrackerView.SESSION) {
            add(HudLine("§c[Click to reset]") {
                val now = System.currentTimeMillis()
                if (now - lastReset > RESET_COOLDOWN_MS) {
                    lastReset = now
                    Tracker.resetSession()
                    IHateTrophyFishing.chat("Session reset.")
                }
            })
        }
    }

    private fun formatRate(perHour: Double?) =
        if (perHour == null) "" else "§8(" + String.format(Locale.ROOT, "%.1f", perHour) + "/h)"

    /** 45s -> "<1m", 30 min -> "30m", 84 min -> "1h 24m", 53 h -> "2d 5h". */
    fun formatDuration(millis: Long): String {
        val minutes = millis / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "<1m"
            hours < 1 -> "${minutes}m"
            days < 1 -> "${hours}h ${minutes % 60}m"
            else -> "${days}d ${hours % 24}h"
        }
    }
}
