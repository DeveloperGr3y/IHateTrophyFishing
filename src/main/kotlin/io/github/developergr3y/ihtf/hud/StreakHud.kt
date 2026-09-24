package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.config.StreakConfig
import io.github.developergr3y.ihtf.features.Streak
import io.github.developergr3y.ihtf.util.Compat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.Locale
import kotlin.math.sin

/**
 * The osu!-style streak counter. Gets flashier as the streak grows:
 *
 *    1-9  white        10+  yellow, pops on every catch     25+  orange, wobbles
 *   50+   red, ON FIRE!  100+ rainbow                      250+ rainbow, shakes, sparkles, UNSTOPPABLE
 *
 * Drawn by hand every frame (the animations need it), so it overrides [draw] instead of using text lines.
 */
object StreakHud : HudElement("Streak") {
    private const val POP_MS = 180L
    private const val BREAK_MS = 2_500L
    private const val NEW_BEST_MS = 3_000L
    private const val PREVIEW_COUNT = 47
    private const val CAPTION = "TROPHY STREAK"

    private val levelColours = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFF55.toInt(), 0xFFFFAA00.toInt(), 0xFFFF5555.toInt())
    private val thresholds = listOf(10, 25, 50, 100, 250)

    private val config get() = IHateTrophyFishing.config.dopamine.streak

    override val position get() = config.position

    override fun resetPosition() {
        config.position = StreakConfig().position
    }

    override val enabled get() = Streak.isActive

    override fun isVisible(inInventory: Boolean): Boolean {
        if (!enabled || !Visibility.allowed(ShowWhere.TROPHY_ISLANDS, ShowWhen.FISHING_OR_ROD, inInventory)) return false
        return Streak.count >= Streak.SHOW_FROM || isBreaking(System.currentTimeMillis())
    }

    override fun build(inInventory: Boolean) = emptyList<HudLine>()

    private fun isBreaking(now: Long) = Streak.count < Streak.SHOW_FROM && now - Streak.brokenAt < BREAK_MS

    /** 0 = under 10, 1 = 10+, 2 = 25+, 3 = 50+, 4 = 100+, 5 = 250+. */
    private fun level(count: Int) = thresholds.count { count >= it }

    override fun draw(graphics: GuiGraphicsExtractor, inInventory: Boolean, mouseX: Double, mouseY: Double) {
        val now = System.currentTimeMillis()
        val font = Minecraft.getInstance().font
        // In the GUI editor, show an example streak so there's something to drag around.
        val preview = Compat.screen is HudEditScreen && Streak.count < Streak.SHOW_FROM

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(screenX.toFloat(), screenY.toFloat())
        pose.scale(position.scale)
        if (!preview && isBreaking(now)) {
            drawBreak(graphics, font, now)
        } else {
            drawStreak(graphics, font, now, if (preview) PREVIEW_COUNT else Streak.count, animate = !preview)
        }
        pose.popMatrix()
    }

    private fun drawStreak(graphics: GuiGraphicsExtractor, font: Font, now: Long, count: Int, animate: Boolean) {
        val pose = graphics.pose()

        // Small caption so it's obvious what the number is.
        graphics.text(font, CAPTION, 0, 0, 0xFF888888.toInt(), true)
        val top = font.lineHeight + 1
        pose.pushMatrix()
        pose.translate(0f, top.toFloat())

        val level = level(count)
        val text = "x$count"
        val scale = 2f + level * 0.25f
        val textWidth = font.width(text).toFloat()
        val textHeight = font.lineHeight.toFloat()

        val sinceCatch = now - Streak.lastCatchAt
        val pop = if (animate && level >= 1 && sinceCatch < POP_MS) 1f + 0.35f * (1f - sinceCatch.toFloat() / POP_MS) else 1f

        // Pop, wobble and shake all happen around the centre of the number.
        pose.pushMatrix()
        pose.translate(textWidth * scale / 2, textHeight * scale / 2)
        if (animate && level >= 5) pose.translate(Effects.shake(now, 1), Effects.shake(now, 2))
        if (animate && level >= 2) pose.rotate(sin(now / 110.0).toFloat() * 0.035f * (level - 1))
        pose.scale(scale * pop)
        pose.translate(-textWidth / 2, -textHeight / 2)
        drawColoured(graphics, font, text, level, now)
        pose.popMatrix()

        var right = (textWidth * scale).toInt() + 4
        label(level)?.let { label ->
            pose.pushMatrix()
            pose.translate(right.toFloat(), (textHeight * scale - textHeight * 1.25f) / 2)
            pose.scale(1.25f)
            drawColoured(graphics, font, label, level, now)
            pose.popMatrix()
            right += (font.width(label) * 1.25f).toInt()
        }

        if (animate && level >= 5) Effects.sparkles(graphics, font, now, right, (textHeight * scale).toInt())

        // Under the number: a countdown when the streak is about to end, otherwise your best.
        val bestY = (textHeight * scale).toInt() + 3
        val remaining = Streak.GRACE_MS - (now - Streak.lastCatchAt)
        val (line, colour) = when {
            animate && remaining in 0..Streak.WARN_MS -> {
                // Pulses faster as it runs out.
                val flash = sin(now / (40.0 + remaining / 50.0)) > 0
                "Ends in " + String.format(Locale.ROOT, "%.1fs", remaining / 1000.0) to
                    (if (flash) 0xFFFF5555.toInt() else 0xFFFFAAAA.toInt())
            }
            animate && now - Streak.newBestAt < NEW_BEST_MS ->
                "NEW BEST!" to (if ((now / 150) % 2 == 0L) 0xFFFFAA00.toInt() else 0xFFFFFFFF.toInt())
            else -> "Best: ${Streak.best}" to 0xFFAAAAAA.toInt()
        }
        graphics.text(font, line, 0, bestY, colour, true)
        pose.popMatrix()

        width = maxOf(right, font.width(line), font.width(CAPTION))
        height = top + bestY + font.lineHeight
    }

    private fun label(level: Int) = when {
        level >= 5 -> "UNSTOPPABLE"
        level >= 3 -> "ON FIRE!"
        else -> null
    }

    private fun drawColoured(graphics: GuiGraphicsExtractor, font: Font, text: String, level: Int, now: Long) {
        if (level < 4) graphics.text(font, text, 0, 0, levelColours[level], true) else Effects.rainbowText(graphics, font, text, now)
    }

    private fun drawBreak(graphics: GuiGraphicsExtractor, font: Font, now: Long) {
        val pose = graphics.pose()
        val fade = 1f - (now - Streak.brokenAt).toFloat() / BREAK_MS
        val colour = ((fade * 255).toInt().coerceIn(8, 255) shl 24) or 0xFF5555

        pose.pushMatrix()
        pose.scale(1.5f)
        graphics.text(font, "COMBO BREAK", 0, 0, colour, true)
        pose.popMatrix()

        val countText = "x${Streak.brokenCount}"
        val y = (font.lineHeight * 1.5f).toInt() + 2
        pose.pushMatrix()
        pose.translate(0f, y.toFloat())
        pose.scale(2.5f)
        graphics.text(font, countText, 0, 0, colour, true)
        pose.popMatrix()

        width = maxOf((font.width("COMBO BREAK") * 1.5f).toInt(), (font.width(countText) * 2.5f).toInt())
        height = y + (font.lineHeight * 2.5f).toInt()
    }
}
