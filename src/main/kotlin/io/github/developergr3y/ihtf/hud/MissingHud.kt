package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.config.MissingConfig
import io.github.developergr3y.ihtf.data.ProfileData
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.trophy.SyncStatus
import io.github.developergr3y.ihtf.trophy.Trophies
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.trophy.pityStatuses
import io.github.developergr3y.ihtf.util.Location

/**
 * A to-do list of the trophies you haven't caught yet, one tier at a time (each tier counts on its own, like
 * Odger's menu), with where to catch each one and how close you are to pity. Fish on the Crimson Isle, frogs on
 * the Lotus Atoll, both anywhere else.
 */
object MissingHud : HudElement("Missing Trophies") {
    private val config get() = IHateTrophyFishing.config.trackers.missing

    override val position get() = config.position

    override fun resetPosition() {
        config.position = MissingConfig().position
    }

    override val enabled get() = config.enabled

    override fun isVisible(inInventory: Boolean) =
        enabled && Visibility.allowed(config.where, config.showWhen, inInventory)

    override fun build(inInventory: Boolean): List<HudLine> {
        val tier = config.tier
        val profile = Storage.profile()
        val lines = mutableListOf<HudLine>()

        val tabs = TrophyTier.entries.joinToString(" ") { if (it == tier) "${it.colour}§l[${it.displayName}]" else "§8${it.displayName}" }
        lines += if (inInventory) {
            HudLine("§e§lMissing $tabs") { button ->
                config.tier = if (button == 1) tier.previous else tier.next
                IHateTrophyFishing.saveConfig()
            }
        } else {
            HudLine("§e§lMissing $tabs")
        }
        if (inInventory) lines += HudLine("§8Click the tiers to change (right-click goes back)")

        val kinds = when (Location.area) {
            "Crimson Isle" -> listOf(false)
            "Lotus Atoll" -> listOf(true)
            else -> listOf(false, true)
        }
        for (frog in kinds) {
            val kindName = if (frog) "frogs" else "fish"
            if (!profile.collectionSynced(frog)) {
                lines += HudLine("${tier.formatted} §7${kindName.replaceFirstChar { it.uppercase() }}")
                lines += HudLine("§eTo see what you're missing, ${SyncStatus.ownedStep(frog)}")
                continue
            }

            val all = Trophies.all.values.filter { it.isFrog == frog }.sortedWith(compareBy({ it.rank }, { it.name }))
            val missing = all.filter { (profile.lifetime[it.key]?.get(tier) ?: 0) == 0 }
            lines += HudLine("${tier.formatted} §7${kindName.replaceFirstChar { it.uppercase() }}  §f${all.size - missing.size} / ${all.size}")
            if (missing.isEmpty()) {
                lines += HudLine("§a✔ All ${tier.displayName} $kindName caught!")
                continue
            }
            for (info in missing) {
                val parts = mutableListOf(Trophies.colouredName(info.key, profile))
                if (config.showHints && info.hint.isNotEmpty()) parts += "§8${info.hint}"
                if (config.showPity && tier >= TrophyTier.GOLD) pityText(info.key, tier, profile)?.let { parts += it }
                lines += HudLine(parts.joinToString(" §8· "), iconKey = info.key)
            }
        }
        return lines
    }

    /** "pity in 37 ~2h 10m", using the same numbers as the Trophy Tracker. */
    private fun pityText(key: String, tier: TrophyTier, profile: ProfileData): String? {
        val pity = pityStatuses(key, profile, showBoth = true).firstOrNull { it.tier == tier } ?: return null
        if (pity.catchesLeft == 0) return "§anext catch!"
        val period = Tracker.period(IHateTrophyFishing.config.trackers.trophyFish.view)
        val estimate = Tracker.estimatedPerHour(key, period, profile)
            ?.let { " §e~" + TrackerHud.formatDuration((pity.catchesLeft / it * 3_600_000).toLong()) }
            .orEmpty()
        return "§7pity in §f${pity.catchesLeft}$estimate"
    }
}
