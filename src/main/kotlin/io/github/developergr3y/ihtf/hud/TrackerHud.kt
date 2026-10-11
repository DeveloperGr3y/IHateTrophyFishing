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
                val name = "${Trophies.colouredName(key, profile)} ${countColour}x${counts.total} ${formatRate(rate)}"
                // The "/h" above is what you've actually caught; time until pity uses the steadier estimate.
                val estimate = Tracker.estimatedPerHour(key, period, profile)
                val pities = pityStatuses(key, profile, config.showBothPities).map { pity ->
                    val time = when {
                        pity.catchesLeft == 0 -> "§anext catch!"
                        estimate == null -> "§7calculating…"
                        else -> "§e~" + formatDuration((pity.catchesLeft / estimate * 3_600_000).toLong())
                    }
                    pity to time
                }
                if (config.compact) {
                    // "Flyfish x136 (23.9/h) · ◆ 92 ~3h 52m", with the tier as a coloured diamond.
                    val pityText = pities.joinToString("") { (pity, time) ->
                        val left = if (pity.catchesLeft == 0) "" else "§f${pity.catchesLeft} "
                        " §8· ${pity.tier.colour}◆ $left$time"
                    }
                    lines += HudLine(name + pityText, iconKey = key)
                } else {
                    lines += HudLine(name, iconKey = key)
                    for ((pity, time) in pities) {
                        lines += HudLine(" ${pity.tier.formatted} §7pity in §f${pity.catchesLeft} §7· Time until pity: $time", indent = true)
                    }
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

    /** Shown at the top while your inventory is open. */
    private fun buttons(view: TrackerView) = buildList {
        add(HudLine("§7[Click to show §a[${view.next}]§7]") {
            config.view = view.next
            IHateTrophyFishing.saveConfig()
        })
        add(HudLine(if (config.compact) "§7[Click for §a[Full]§7]" else "§7[Click for §a[Compact]§7]") {
            config.compact = !config.compact
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
