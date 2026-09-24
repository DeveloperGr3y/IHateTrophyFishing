package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.features.Roulette
import io.github.developergr3y.ihtf.trophy.Trophies
import io.github.developergr3y.ihtf.trophy.TrophyTier
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.floor
import kotlin.math.sin

/** Draws the CS:GO-style case-opening strip above the crosshair while a [Roulette] spin is running. */
object RouletteOverlay {
    private const val CARD_HEIGHT = 50
    private const val ICON_SCALE = 1.75f

    private fun tierColour(tier: TrophyTier) = when (tier) {
        TrophyTier.BRONZE -> 0xFFCD7F32.toInt()
        TrophyTier.SILVER -> 0xFFC0C0C0.toInt()
        TrophyTier.GOLD -> 0xFFFFAA00.toInt()
        TrophyTier.DIAMOND -> 0xFF55FFFF.toInt()
    }

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val spin = Roulette.current ?: return
        val now = System.currentTimeMillis()
        val font = Minecraft.getInstance().font
        val step = Roulette.STEP
        val cardWidth = Roulette.CARD_WIDTH

        val centreX = graphics.guiWidth() / 2
        val panelWidth = minOf(graphics.guiWidth() - 20, step * 7)
        val left = centreX - panelWidth / 2
        val right = left + panelWidth
        val top = graphics.guiHeight() / 2 - CARD_HEIGHT - 40 // above the crosshair
        val bottom = top + CARD_HEIGHT
        val landed = spin.landed(now)

        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, 0xD0101010.toInt())

        // The strip of cards, clipped to the panel.
        val scroll = spin.scroll(now)
        graphics.enableScissor(left, top, right, bottom)
        val first = floor((scroll - panelWidth / 2f) / step).toInt() - 1
        for (i in first..first + panelWidth / step + 2) {
            val card = spin.cards.getOrNull(i) ?: continue
            val x = (centreX + i * step - scroll).toInt()
            drawCard(graphics, card, x, top, cardWidth)
        }
        graphics.disableScissor()

        // Winning card pulses once it lands.
        if (landed) {
            val x = (centreX + spin.winnerIndex * step - scroll).toInt()
            val pulse = (sin(now / 90.0) * 0.5 + 0.5).toFloat()
            graphics.outline(x - 1, top - 1, cardWidth + 2, CARD_HEIGHT + 2, Effects.withAlpha(tierColour(spin.tier), 0.4f + 0.6f * pulse))
        }

        // Yellow centre marker.
        val marker = 0xFFFFD700.toInt()
        graphics.fill(centreX - 1, top - 4, centreX + 1, bottom + 4, marker)
        graphics.centeredText(font, "▼", centreX, top - 11, marker)
        graphics.centeredText(font, "▲", centreX, bottom + 4, marker)

        if (landed) drawResult(graphics, spin, now, centreX, bottom + 16)
    }

    private fun drawCard(graphics: GuiGraphicsExtractor, card: Roulette.Card, x: Int, top: Int, width: Int) {
        val colour = tierColour(card.tier)
        graphics.fill(x, top, x + width, top + CARD_HEIGHT, 0xFF2A2A2E.toInt())
        graphics.fill(x, top, x + width, top + CARD_HEIGHT, Effects.withAlpha(colour, 0.18f)) // tier tint
        graphics.fill(x, top + CARD_HEIGHT - 4, x + width, top + CARD_HEIGHT, colour) // tier bar

        val icon = TrophyIcons.icon(card.key)
        if (icon != null) {
            val pose = graphics.pose()
            pose.pushMatrix()
            pose.translate(x + (width - 16 * ICON_SCALE) / 2, top + 8f)
            pose.scale(ICON_SCALE)
            graphics.item(icon, 0, 0)
            pose.popMatrix()
        } else {
            // No Hypixel resource pack: show the trophy's initials instead.
            val name = Trophies[card.key]?.name ?: card.key
            val initials = name.split(" ", "-").filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.take(3)
            graphics.centeredText(Minecraft.getInstance().font, initials, x + width / 2, top + 18, colour)
        }
    }

    private fun drawResult(graphics: GuiGraphicsExtractor, spin: Roulette.Spin, now: Long, centreX: Int, y: Int) {
        val font = Minecraft.getInstance().font
        val pose = graphics.pose()
        val sinceLanding = now - spin.startedAt - spin.spinMs
        val title = if (spin.isDiamond) "DIAMOND!" else "GOLD!"

        pose.pushMatrix()
        pose.translate(centreX.toFloat(), y.toFloat())
        // Short shake on landing for Diamonds.
        if (spin.isDiamond && sinceLanding < 400) pose.translate(Effects.shake(now, 3, 4f), Effects.shake(now, 4, 4f))
        pose.scale(2.5f)
        val titleWidth = font.width(title)
        pose.translate(-titleWidth / 2f, 0f)
        if (spin.isDiamond) {
            Effects.rainbowText(graphics, font, title, now)
        } else {
            val shimmer = if ((now / 120) % 2 == 0L) 0xFFFFAA00.toInt() else 0xFFFFE066.toInt()
            graphics.text(font, title, 0, 0, shimmer, true)
        }
        pose.popMatrix()

        val name = Trophies.colouredName(spin.key, Storage.profile())
        pose.pushMatrix()
        pose.translate(centreX.toFloat(), y + font.lineHeight * 2.5f + 4)
        pose.scale(1.25f)
        graphics.centeredText(font, name, 0, 0, -1)
        pose.popMatrix()

        if (spin.isDiamond) {
            pose.pushMatrix()
            pose.translate(centreX - titleWidth * 1.5f, y - 6f)
            Effects.sparkles(graphics, font, now, (titleWidth * 3f).toInt(), (font.lineHeight * 4.5f).toInt(), count = 8)
            pose.popMatrix()
        }
    }
}
