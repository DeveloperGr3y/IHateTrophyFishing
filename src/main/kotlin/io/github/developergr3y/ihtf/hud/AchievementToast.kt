package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.features.achievements.Achievement
import io.github.developergr3y.ihtf.features.achievements.Rarity
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** "Achievement unlocked" pop-up that slides down from the top of the screen. Several unlocks queue up. */
object AchievementToast {
    private const val SLIDE_MS = 300L
    private const val SUMMARY_MS = 8_000L

    /** Rarer achievements stay up longer. */
    private fun showMs(rarity: Rarity) = when (rarity) {
        Rarity.COMMON, Rarity.UNCOMMON -> 6_000L
        Rarity.RARE, Rarity.EPIC -> 7_000L
        Rarity.LEGENDARY, Rarity.MYTHIC -> 9_000L
        Rarity.DIVINE -> 12_000L
    }

    private class Toast(val title: String, val name: String, val description: String, val rarity: Rarity, val showMs: Long) {
        var startedAt = 0L
    }

    private val queue = ArrayDeque<Toast>()
    private var current: Toast? = null

    fun show(achievement: Achievement) = enqueue(
        Toast(
            "ACHIEVEMENT UNLOCKED · ${achievement.rarity.label.uppercase()}", achievement.name, achievement.description,
            achievement.rarity, showMs(achievement.rarity),
        ),
    )

    fun showSummary(count: Int) = enqueue(
        Toast("ACHIEVEMENTS UNLOCKED", "$count achievements", "From your existing progress. /ihtf achievements", Rarity.RARE, SUMMARY_MS),
    )

    private fun enqueue(toast: Toast) {
        if (queue.size < 10) queue.addLast(toast)
    }

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val now = System.currentTimeMillis()
        var toast = current
        if (toast == null || now - toast.startedAt > toast.showMs) {
            toast = queue.removeFirstOrNull() ?: run {
                current = null
                return
            }
            toast.startedAt = now
            current = toast
        }

        val font = Minecraft.getInstance().font
        val elapsed = now - toast.startedAt
        // Slide in, hold, slide out.
        val slide = when {
            elapsed < SLIDE_MS -> elapsed.toFloat() / SLIDE_MS
            elapsed > toast.showMs - SLIDE_MS -> (toast.showMs - elapsed).toFloat() / SLIDE_MS
            else -> 1f
        }.coerceIn(0f, 1f)

        val width = maxOf(font.width(toast.title), font.width(toast.name) * 3 / 2, font.width(toast.description)) + 20
        val height = 44
        val left = (graphics.guiWidth() - width) / 2
        val top = (-height + (height + 8) * slide).toInt()
        val colour = toast.rarity.rgb

        graphics.fill(left, top, left + width, top + height, 0xE0141018.toInt())
        graphics.fill(left, top, left + width, top + 2, colour)
        graphics.outline(left, top, width, height, Effects.withAlpha(colour, 0.6f))

        graphics.centeredText(font, toast.title, left + width / 2, top + 6, Effects.withAlpha(colour, 0.85f))

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(left + width / 2f, top + 17f)
        pose.scale(1.5f)
        val nameWidth = font.width(toast.name)
        pose.translate(-nameWidth / 2f, 0f)
        if (toast.rarity == Rarity.DIVINE) {
            Effects.rainbowText(graphics, font, toast.name, now)
        } else {
            graphics.text(font, toast.name, 0, 0, colour, true)
        }
        pose.popMatrix()

        graphics.centeredText(font, toast.description, left + width / 2, top + 32, 0xFFAAAAAA.toInt())
    }
}
