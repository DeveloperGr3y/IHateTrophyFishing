package io.github.developergr3y.ihtf.hud

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.random.Random

/** Shared bits for the flashy Dopamine Enhancers displays. */
object Effects {
    /** Hue (0-1, wraps) to a bright opaque colour, pastel enough to read on lava. */
    fun rainbow(hue: Float): Int {
        val h = ((hue % 1f) + 1f) % 1f * 6f
        val f = h - h.toInt()
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(1f, f, 0f)
            1 -> Triple(1f - f, 1f, 0f)
            2 -> Triple(0f, 1f, f)
            3 -> Triple(0f, 1f - f, 1f)
            4 -> Triple(f, 0f, 1f)
            else -> Triple(1f, 0f, 1f - f)
        }
        fun channel(v: Float) = (155 + v * 100).toInt()
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** Draws [text] with one scrolling rainbow colour per letter, starting at x = 0. */
    fun rainbowText(graphics: GuiGraphicsExtractor, font: Font, text: String, now: Long, y: Int = 0) {
        var x = 0
        text.forEachIndexed { i, c ->
            val letter = c.toString()
            graphics.text(font, letter, x, y, rainbow((now % 2000) / 2000f + i * 0.08f), true)
            x += font.width(letter)
        }
    }

    /** A few "✦" sparkles scattered over a width x height box, re-shuffled 4 times a second. */
    fun sparkles(graphics: GuiGraphicsExtractor, font: Font, now: Long, width: Int, height: Int, count: Int = 4) {
        val random = Random(now / 250)
        repeat(count) {
            val x = random.nextInt(-6, width + 6)
            val y = random.nextInt(-8, height)
            graphics.text(font, "✦", x, y, rainbow(random.nextFloat()), true)
        }
    }

    /** Small random offset for shaking, changes every 40ms. */
    fun shake(now: Long, seed: Int, amount: Float = 3f) = (Random(now / 40 * 31 + seed).nextFloat() - 0.5f) * amount

    /** Replaces the alpha of an ARGB colour (0-1). */
    fun withAlpha(colour: Int, alpha: Float) = ((alpha * 255).toInt().coerceIn(8, 255) shl 24) or (colour and 0xFFFFFF)
}
