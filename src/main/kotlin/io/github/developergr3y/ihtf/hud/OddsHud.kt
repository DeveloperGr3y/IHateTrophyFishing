package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.config.OddsConfig
import io.github.developergr3y.ihtf.config.OddsFormat
import io.github.developergr3y.ihtf.odds.Boost
import io.github.developergr3y.ihtf.odds.OddsImport
import io.github.developergr3y.ihtf.odds.TrophyOdds
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.util.Location
import kotlin.math.roundToInt

/**
 * Your chance of a Gold or Diamond on the next trophy, from everything that boosts it. Anything not read yet is
 * flagged with what to open, so the numbers get more accurate as you go. Open your inventory for the breakdown.
 */
object OddsHud : HudElement("Trophy Odds") {
    private val config get() = IHateTrophyFishing.config.trackers.odds

    override val position get() = config.position

    override fun resetPosition() {
        config.position = OddsConfig().position
    }

    override val enabled get() = config.enabled

    override fun isVisible(inInventory: Boolean) =
        enabled && Visibility.allowed(config.where, config.showWhen, inInventory)

    override fun build(inInventory: Boolean): List<HudLine> {
        val frog = Location.area == "Lotus Atoll"
        val boosts = TrophyOdds.boosts(frog)
        val odds = TrophyOdds.odds(boosts, inWormhole = false)
        val gold = TrophyTier.GOLD
        val diamond = TrophyTier.DIAMOND
        val lines = mutableListOf<HudLine>()

        lines += HudLine("§e§lTrophy Odds §7${if (frog) "Frogs" else "Fish"}")
        lines += HudLine("${gold.formatted} ${format(odds.gold)}")
        lines += HudLine("${diamond.formatted} ${format(odds.diamond)}")
        if (OddsImport.froggles != null) {
            val wormhole = TrophyOdds.odds(boosts, inWormhole = true)
            lines += HudLine("§8In a Wormhole: ${gold.colour}${short(wormhole.gold)} §8· ${diamond.colour}${short(wormhole.diamond)}")
        }

        val unsynced = boosts.filter { it.syncStep != null }
        if (inInventory && config.showBreakdown) {
            lines += HudLine("§7Boosts §8(${gold.colour}Gold §8/ ${diamond.colour}Diamond§8)")
            boosts.forEach { lines += breakdownLine(it) }
        } else {
            unsynced.forEach { lines += HudLine("§e⚠ ${it.name}: ${it.syncStep}") }
        }
        return lines
    }

    private fun breakdownLine(boost: Boost): HudLine {
        val where = if (boost.wormholeOnly) " §8(Wormholes)" else ""
        return HudLine(
            when {
                boost.syncStep != null -> "§e? §7${boost.name} §8— §e${boost.syncStep}"
                boost.active -> "§a✔ §f${boost.name} ${percent(boost.gold, "§6")}§8/${percent(boost.diamond, "§b")}$where" +
                    (boost.tip?.takeIf { config.showTips }?.let { " §8· §7$it" } ?: "")
                else -> "§c✖ §7${boost.name}" + (boost.tip?.takeIf { config.showTips }?.let { " §8— §7$it" } ?: "")
            },
        )
    }

    private fun percent(value: Double, colour: String) = if (value > 0) "$colour+${trim(value)}%" else "§8-"

    private fun trim(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    /** "§f1 in 38 §7(2.63%)", depending on the Format setting. */
    private fun format(chance: Double): String {
        val oneIn = "§f1 in ${(100 / chance).roundToInt()}"
        val pct = if (chance >= 1) "%.2f%%".format(chance) else "%.3f%%".format(chance)
        return when (config.format) {
            OddsFormat.BOTH -> "$oneIn §7($pct)"
            OddsFormat.ONE_IN -> oneIn
            OddsFormat.PERCENT -> "§f$pct"
        }
    }

    private fun short(chance: Double) = when (config.format) {
        OddsFormat.PERCENT -> if (chance >= 1) "%.2f%%".format(chance) else "%.3f%%".format(chance)
        else -> "1 in ${(100 / chance).roundToInt()}"
    }
}
