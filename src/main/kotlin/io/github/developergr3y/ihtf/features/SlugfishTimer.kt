package io.github.developergr3y.ihtf.features

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.trophy.stripFormatting
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import io.github.developergr3y.ihtf.util.Sounds
import net.minecraft.util.CommonColors
import net.minecraft.world.entity.Entity

/**
 * Slugfish only counts if enough time passes between casting and reeling in
 * (20s normally, 10s with a Slug pet). Started life as my standalone Slugfish Timer mod.
 *
 *  1. When your bobber appears, a timer starts.
 *  2. Hypixel shows a "!!!" name tag above the bobber when a fish bites.
 *  3. If a fish bites AFTER the timer is done, a loud ding plays (3 bell hits) and "REEL!" shows.
 *     Bites before the timer are ignored, because reeling those won't give a Slugfish.
 *
 * It's alert-only and never touches your inputs.
 */
object SlugfishTimer {
    private const val DEFAULT_SOUND = "block.note_block.bell"

    /** The client sees the bobber slightly after the server starts counting, so add a small margin. */
    private const val SAFETY_MARGIN_MS = 300L

    /** How close (in blocks) the "!!!" tag must be to your bobber, so other players' bites are ignored. */
    private const val BITE_RADIUS_SQ = 3.0 * 3.0

    /** Ticks between each hit of the ding. */
    private const val DING_GAP_TICKS = 3
    private const val DING_REPEATS = 3

    private val config get() = IHateTrophyFishing.config.helpers.slugfish

    private var currentHook: Any? = null
    private var castAtMs = 0L
    private var biting = false
    private var dingedThisBite = false
    private var dingsRemaining = 0
    private var dingCooldown = 0

    fun tick(client: Minecraft) {
        playQueuedDings(client)

        val hook = if (config.enabled) client.player?.fishing else null

        if (hook !== currentHook) {
            // New cast (or rod reeled in): reset everything.
            currentHook = hook
            castAtMs = System.currentTimeMillis()
            biting = false
            dingedThisBite = false
        }
        if (hook == null || client.level == null) return

        biting = isBiting(client, hook)
        if (!biting) {
            dingedThisBite = false // ready for the next bite on the same cast
            return
        }

        if (timerDone() && !dingedThisBite) {
            dingedThisBite = true
            queueDing()
        }
    }

    fun queueDing() {
        dingsRemaining = DING_REPEATS
        dingCooldown = 0
    }

    /** Hypixel marks a bite with a "!!!" name tag (an invisible armor stand) just above the bobber. */
    private fun isBiting(client: Minecraft, hook: Entity): Boolean {
        for (entity in client.level!!.entitiesForRendering()) {
            val name = entity.customName ?: continue
            if (name.string.stripFormatting().contains("!!!") && entity.distanceToSqr(hook) <= BITE_RADIUS_SQ) {
                return true
            }
        }
        return false
    }

    private fun playQueuedDings(client: Minecraft) {
        if (dingsRemaining <= 0) return
        if (dingCooldown-- > 0) return

        // The chosen sound (default: note block bell at pitch 1.6, a bright "ding"). Falls back to the bell if
        // the name isn't a valid sound id.
        if (!Sounds.play(config.sound, config.pitch)) Sounds.play(DEFAULT_SOUND, config.pitch)
        dingsRemaining--
        dingCooldown = DING_GAP_TICKS
    }

    private fun timerDone() = remainingMs() <= 0

    private fun remainingMs() = config.seconds * 1000L + SAFETY_MARGIN_MS - (System.currentTimeMillis() - castAtMs)

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        if (currentHook == null) return
        val client = Minecraft.getInstance()

        val (text, colour, scale) = when {
            !timerDone() -> Triple(String.format("Slugfish: %.1fs", remainingMs() / 1000.0), CommonColors.YELLOW, 1.25f)
            biting -> Triple("REEL!", CommonColors.GREEN, 3.0f)
            else -> Triple("Ready - wait for bite", CommonColors.WHITE, 1.25f)
        }

        graphics.pose().pushMatrix()
        graphics.pose().translate(graphics.guiWidth() / 2.0f, graphics.guiHeight() / 2.0f + 14)
        graphics.pose().scale(scale)
        graphics.centeredText(client.font, text, 0, 0, colour)
        graphics.pose().popMatrix()
    }
}
