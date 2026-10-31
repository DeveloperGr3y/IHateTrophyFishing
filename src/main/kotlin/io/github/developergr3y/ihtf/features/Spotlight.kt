package io.github.developergr3y.ihtf.features

import io.github.developergr3y.ihtf.hud.RouletteOverlay
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Diamond Spotlight: while a Diamond's roulette spins, the whole HUD (chat, tab list, scoreboard, hotbar, other mods'
 * displays) is left out and the world behind is shaded, so the reel is the only thing on screen.
 * The HUD hook (GuiMixin) asks [active] every frame and draws [render] instead of the HUD.
 * Taking damage ends it for that spin, so you can see your health; the reel carries on as usual.
 */
object Spotlight {
    private const val SHADE_ALPHA = 0x70
    private const val FADE_IN_MS = 300L
    private const val FADE_OUT_MS = 500L

    /** The spin the player was hurt during, which then stays out of the spotlight. */
    private var interrupted: Roulette.Spin? = null
    private var lastHealth = -1f

    @JvmStatic
    val active: Boolean
        get() {
            val spin = Roulette.current ?: return false
            return spin.spotlight && spin !== interrupted
        }

    fun tick() {
        val player = Minecraft.getInstance().player
        val health = player?.health ?: -1f
        val spin = Roulette.current
        if (spin != null && spin.spotlight && lastHealth >= 0 && health in 0f..<lastHealth) interrupted = spin
        lastHealth = health
    }

    /** Drawn instead of the HUD: the shade, then the reel. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        val spin = Roulette.current ?: return
        val elapsed = System.currentTimeMillis() - spin.startedAt
        val fade = minOf(elapsed.toFloat() / FADE_IN_MS, (spin.totalMs - elapsed).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
        val alpha = (SHADE_ALPHA * fade).toInt()
        if (alpha > 0) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha shl 24)
        RouletteOverlay.render(graphics, deltaTracker)
    }
}
