package io.github.developergr3y.ihtf.util

import io.github.developergr3y.ihtf.trophy.stripFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot

/**
 * Whether you're in SkyBlock, and which island. Checked once a second.
 *  - SkyBlock: Hypixel's scoreboard sidebar is titled "SKYBLOCK" (also "SKYBLOCK CO-OP", "SKYBLOCK GUEST").
 *    Everywhere else (other Hypixel lobbies, other servers, singleplayer) the mod stays out of the way.
 *  - Island: the "Area: Crimson Isle" line of the tab list.
 */
object Location {
    private val areaLine = Regex("^\\s*Area: (.+?)\\s*$")
    private val trophyIslands = setOf("Crimson Isle", "Lotus Atoll")
    private const val CHECK_INTERVAL_MS = 1_000L

    /** In Hypixel SkyBlock right now. Nothing shows or counts outside it. */
    var onSkyBlock = false
        private set

    /** e.g. "Crimson Isle", "Private Island"; null if unknown (not in SkyBlock, or tab widgets turned off). */
    var area: String? = null
        private set
    private var lastCheck = 0L

    /**
     * On the Crimson Isle or the Lotus Atoll. Inside SkyBlock, an unknown area (no tab list Area line) counts as
     * yes, so the mod keeps working for people who've turned Hypixel's tab widgets off.
     */
    val onTrophyIsland get() = onSkyBlock && (area?.let { it in trophyIslands } ?: true)

    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now

        val title = client.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)?.displayName?.string
        onSkyBlock = title?.stripFormatting()?.uppercase()?.contains("SKYBLOCK") == true

        val players = client.connection?.listedOnlinePlayers
        area = if (!onSkyBlock || players == null) {
            null
        } else {
            players.firstNotNullOfOrNull { info ->
                val line = info.tabListDisplayName?.string?.stripFormatting() ?: return@firstNotNullOfOrNull null
                areaLine.find(line)?.groupValues?.get(1)
            }
        }
    }
}
