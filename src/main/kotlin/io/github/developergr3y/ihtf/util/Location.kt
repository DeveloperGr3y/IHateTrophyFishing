package io.github.developergr3y.ihtf.util

import io.github.developergr3y.ihtf.trophy.stripFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot

/**
 * Whether you're in SkyBlock, and which island. Both only change with the world, so they're checked once a second for
 * a little while after joining one, until both are known.
 *  - SkyBlock: Hypixel's scoreboard sidebar is titled "SKYBLOCK" (also "SKYBLOCK CO-OP", "SKYBLOCK GUEST").
 *    Everywhere else (other Hypixel lobbies, other servers, singleplayer) the mod stays out of the way.
 *  - Island: the "Area: Crimson Isle" line of the tab list.
 */
object Location {
    private val areaLine = Regex("^\\s*Area: (.+?)\\s*$")
    private val trophyIslands = setOf("Crimson Isle", "Lotus Atoll")
    private const val CHECK_INTERVAL_MS = 1_000L
    private const val SEARCH_MS = 20_000L

    /** In Hypixel SkyBlock right now. Nothing shows or counts outside it. */
    var onSkyBlock = false
        private set

    /** e.g. "Crimson Isle", "Private Island"; null if unknown (not in SkyBlock, or tab widgets turned off). */
    var area: String? = null
        private set
    private var lastCheck = 0L
    private var lastLevel: Any? = null
    private var searchUntil = 0L

    /**
     * On the Crimson Isle or the Lotus Atoll. Inside SkyBlock, an unknown area (no tab list Area line) counts as
     * yes, so the mod keeps working for people who've turned Hypixel's tab widgets off.
     */
    val onTrophyIsland get() = onSkyBlock && (area?.let { it in trophyIslands } ?: true)

    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (client.level !== lastLevel) {
            lastLevel = client.level
            onSkyBlock = false
            area = null
            searchUntil = now + SEARCH_MS
        }
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        if ((onSkyBlock && area != null) || now > searchUntil) return
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
