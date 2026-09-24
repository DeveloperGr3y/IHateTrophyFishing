package io.github.developergr3y.ihtf.tracker

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.data.Period
import io.github.developergr3y.ihtf.data.ProfileData
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.features.Roulette
import io.github.developergr3y.ihtf.features.Streak
import io.github.developergr3y.ihtf.hud.HudManager
import io.github.developergr3y.ihtf.trophy.FishCounts
import io.github.developergr3y.ihtf.trophy.Trophies
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.util.Location
import net.minecraft.client.Minecraft

enum class TrackerView(private val label: String) {
    SESSION("Session"),
    TODAY("Today"),
    TOTAL("Total"),
    ;

    val next get() = entries[(ordinal + 1) % entries.size]

    override fun toString() = label
}

/** One catch, kept in memory for the "Currently Targeting" display. */
data class RecentCatch(val key: String, val tier: TrophyTier, val time: Long)

/**
 * Counts catches and "active fishing time".
 *
 * Time only counts while you are actually fishing on the Crimson Isle or Lotus Atoll: it keeps running while
 * your bobber is in lava (or water, for Trophy Frogs; configurable), and for up to the AFK timeout after that
 * (covers reeling in and recasting). After that it pauses,
 * so catches per hour aren't dragged down by time spent AFK or doing something else.
 * It can also be paused by hand from the tracker's inventory buttons.
 */
object Tracker {
    /** How long a newly caught fish stays highlighted green, like SkyHanni's trophy fish display. */
    const val FLASH_MS = 5_000L

    private var lastActivity = 0L
    private var lastTick = 0L

    var manuallyPaused = false
        private set

    /** Last catch time per fish, for the green flash. */
    private val lastCatch = mutableMapOf<String, Long>()
    val recentCatches = ArrayDeque<RecentCatch>()

    val isAfk get() = System.currentTimeMillis() - lastActivity > afkTimeoutMs
    val isPaused get() = manuallyPaused || isAfk

    private val afkTimeoutMs get() = IHateTrophyFishing.config.trackers.trophyFish.afkTimeoutSeconds * 1000L

    fun onCatch(displayName: String, tier: TrophyTier, colour: String?, amount: Int = 1) {
        val key = Trophies.key(displayName)
        val now = System.currentTimeMillis()
        val profile = Storage.profile()
        profile.names[key] = displayName
        if (colour != null) profile.colours[key] = colour
        // Checked before counting this catch: was it the first of this tier? (for the roulette's "first only")
        val firstOfTier = (profile.lifetime[key]?.get(tier) ?: 0) == 0
        profile.lifetime.getOrPut(key) { FishCounts() }.add(tier, amount)
        profile.pity[key]?.onCatch(tier, amount)
        for (period in profile.activePeriods()) {
            period.fish.getOrPut(key) { FishCounts() }.add(tier, amount)
        }
        lastActivity = now
        lastCatch[key] = now
        repeat(amount) { recentCatches.addLast(RecentCatch(key, tier, now)) }
        while (recentCatches.size > 500) recentCatches.removeFirst()
        Storage.markDirty()
        HudManager.refreshAll()
        Streak.onCatch(tier, amount)
        Roulette.onCatch(key, tier, firstOfTier)
    }

    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTick
        lastTick = now
        if (client.player == null) return // not in a world yet: no profile to count time for
        // Only trophy fishing counts, so water fishing on your private island doesn't drag your rates down.
        if (!Location.onTrophyIsland) return

        val hook = client.player?.fishing
        val countWater = IHateTrophyFishing.config.trackers.trophyFish.countWaterFishing
        if (hook != null && (hook.isInLava || (countWater && hook.isInWater))) lastActivity = now

        // Ignore huge gaps (game frozen, world loading) so they never count as fishing time.
        if (elapsed in 1..5_000 && !isPaused) {
            for (period in Storage.profile().activePeriods()) period.activeMillis += elapsed
            Storage.markDirty()
        }
    }

    fun isFlashing(key: String) = System.currentTimeMillis() - (lastCatch[key] ?: 0L) < FLASH_MS

    /** When the newest flash ends, so displays can redraw without the green at that moment. */
    val flashEndsAt get() = (lastCatch.values.maxOrNull() ?: 0L) + FLASH_MS

    fun period(view: TrackerView): Period {
        val profile = Storage.profile()
        return when (view) {
            TrackerView.SESSION -> profile.session
            TrackerView.TODAY -> profile.today()
            TrackerView.TOTAL -> profile.total
        }
    }

    fun togglePause() {
        manuallyPaused = !manuallyPaused
        if (!manuallyPaused) lastActivity = System.currentTimeMillis()
        HudManager.refreshAll()
    }

    fun resetSession() {
        Storage.profile().session = Period()
        Storage.markDirty()
        HudManager.refreshAll()
    }

    /** Catches per hour of active fishing time, or null if there isn't enough time recorded yet. */
    fun perHour(count: Int, period: Period): Double? {
        if (period.activeMillis < 1_000) return null
        return count / (period.activeMillis / 3_600_000.0)
    }

    /** Before this much active time and this many trophies, estimates are too noisy to show. */
    private const val MIN_ESTIMATE_MS = 5 * 60_000L
    private const val MIN_ESTIMATE_CATCHES = 3

    /**
     * How many catches' worth of weight your all-time mix gets against this period's mix.
     * Higher = steadier between catches, but slower to notice you're targeting a fish. 50 keeps a rare fish's
     * estimate within roughly ±20% while fishing normally, and adapts to targeting within about an hour.
     */
    private const val PRIOR_WEIGHT = 50.0

    /**
     * Expected catches per hour of one trophy, for "Time until pity". Null while there isn't enough data yet.
     *
     * A single fish's own rate is too jumpy (a few catches → the estimate climbs every minute between them), so:
     *   rate = (all trophies of that kind per hour in this period) × (that trophy's share of them)
     * The overall rate uses every catch, so it settles within minutes. The share starts from your all-time mix
     * (e.g. Odger's counts) and moves towards this period's mix as you catch more, so targeting one fish shows up.
     * Fish and frogs are estimated separately, since you catch them in different places.
     */
    fun estimatedPerHour(key: String, period: Period, profile: ProfileData): Double? {
        val frog = Trophies.isFrog(key)
        val periodCatches = period.fish.filterKeys { Trophies.isFrog(it) == frog }
        val periodTotal = periodCatches.values.sumOf { it.total }
        if (period.activeMillis < MIN_ESTIMATE_MS || periodTotal < MIN_ESTIMATE_CATCHES) return null

        val overallRate = perHour(periodTotal, period) ?: return null
        val periodCount = periodCatches[key]?.total ?: 0

        val lifetimeCatches = profile.lifetime.filterKeys { Trophies.isFrog(it) == frog }
        val lifetimeTotal = lifetimeCatches.values.sumOf { it.total }
        val share = if (lifetimeTotal > 0) {
            val priorShare = (lifetimeCatches[key]?.total ?: 0).toDouble() / lifetimeTotal
            (periodCount + PRIOR_WEIGHT * priorShare) / (periodTotal + PRIOR_WEIGHT)
        } else {
            periodCount.toDouble() / periodTotal
        }
        return (overallRate * share).takeIf { it > 0.0 }
    }
}
