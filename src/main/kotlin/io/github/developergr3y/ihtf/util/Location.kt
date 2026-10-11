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

    // From the scoreboard and tab list, for when Hypixel's own location packet isn't available.
    private var sidebarSkyBlock = false
    private var tabArea: String? = null

    /** In Hypixel SkyBlock right now. Nothing shows or counts outside it. */
    val onSkyBlock get() = HypixelLocation.serverType?.let { it == "SKYBLOCK" } ?: sidebarSkyBlock

    /**
     * e.g. "Crimson Isle", "Private Island", "Dungeon"; null if unknown. Hypixel's location packet when we have it
     * (always right, even in dungeons, whose tab list has no Area line), otherwise the tab list's "Area:" line.
     */
    val area: String? get() = if (HypixelLocation.serverType != null) HypixelLocation.map else tabArea
    private var lastCheck = 0L
    private var lastLevel: Any? = null
    private var searchUntil = 0L

    /**
     * On the Crimson Isle or the Lotus Atoll. With Hypixel's location packet that's exact. Without it, an unknown area
     * (no tab list Area line) counts as yes, so the mod keeps working for people who've turned tab widgets off.
     */
    val onTrophyIsland: Boolean
        get() {
            if (!onSkyBlock) return false
            if (HypixelLocation.serverType != null) return HypixelLocation.map in trophyIslands
            return tabArea?.let { it in trophyIslands } ?: true
        }

    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (client.level !== lastLevel) {
            lastLevel = client.level
            sidebarSkyBlock = false
            tabArea = null
            searchUntil = now + SEARCH_MS
        }
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        if ((sidebarSkyBlock && tabArea != null) || now > searchUntil) return
        lastCheck = now

        val title = client.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)?.displayName?.string
        sidebarSkyBlock = title?.stripFormatting()?.uppercase()?.contains("SKYBLOCK") == true

        val players = client.connection?.listedOnlinePlayers
        tabArea = if (!sidebarSkyBlock || players == null) {
            null
        } else {
            players.firstNotNullOfOrNull { info ->
                val line = info.tabListDisplayName?.string?.stripFormatting() ?: return@firstNotNullOfOrNull null
                areaLine.find(line)?.groupValues?.get(1)
            }
        }
    }
}
