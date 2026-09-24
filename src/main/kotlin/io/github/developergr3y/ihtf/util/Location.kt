package io.github.developergr3y.ihtf.util

import io.github.developergr3y.ihtf.trophy.stripFormatting
import net.minecraft.client.Minecraft

/**
 * Which SkyBlock island you're on, read from the "Area: Crimson Isle" line of Hypixel's tab list.
 * Checked once a second; the tab list only changes when you warp.
 */
object Location {
    private val areaLine = Regex("^\\s*Area: (.+?)\\s*$")
    private val trophyIslands = setOf("Crimson Isle", "Lotus Atoll")
    private const val CHECK_INTERVAL_MS = 1_000L

    /** e.g. "Crimson Isle", "Private Island"; null if there's no Area line (not on Hypixel, or tab widgets off). */
    var area: String? = null
        private set
    private var lastCheck = 0L

    /**
     * On the Crimson Isle or the Lotus Atoll. If the area is unknown (no tab list Area line), this is true,
     * so the mod keeps working for people who've turned Hypixel's tab widgets off.
     */
    val onTrophyIsland get() = area?.let { it in trophyIslands } ?: true

    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now

        val players = client.connection?.listedOnlinePlayers ?: run {
            area = null
            return
        }
        area = players.firstNotNullOfOrNull { info ->
            val line = info.tabListDisplayName?.string?.stripFormatting() ?: return@firstNotNullOfOrNull null
            areaLine.find(line)?.groupValues?.get(1)
        }
    }
}
