package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.Locale

/** Drag a display to move it, scroll over it to resize. Saved when the screen closes. */
class HudEditScreen : Screen(Component.literal("Move IHateTrophyFishing displays")) {
    private var dragging: HudElement? = null
    private var grabX = 0.0
    private var grabY = 0.0

    private val editable get() = HudManager.elements.filter { it.enabled }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val hovered = elementAt(mouseX.toDouble(), mouseY.toDouble())
        for (element in editable) {
            element.draw(graphics, inInventory = false)
            val pos = element.position
            val w = (element.width * pos.scale).toInt()
            val h = (element.height * pos.scale).toInt()
            val colour = if (element == hovered || element == dragging) -1 else 0xFF888888.toInt()
            val x = element.screenX
            val y = element.screenY
            graphics.outline(x - 1, y - 1, w + 2, h + 2, colour)
            if (element == hovered) {
                val scale = String.format(Locale.ROOT, "%.1f", pos.scale)
                graphics.text(font, "${element.label} (x$scale)", x, y + h + 3, 0xFFAAAAAA.toInt(), true)
            }
        }
        graphics.centeredText(font, "Drag to move · Scroll to resize · Esc to save", width / 2, 10, -1)
    }

    private fun elementAt(mouseX: Double, mouseY: Double) = editable.lastOrNull { it.contains(mouseX, mouseY) }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val element = elementAt(event.x(), event.y())
        if (event.button() == 0 && element != null) {
            dragging = element
            grabX = event.x() - element.screenX
            grabY = event.y() - element.screenY
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val element = dragging ?: return super.mouseDragged(event, dragX, dragY)
        val pos = element.position
        val maxX = width - (element.width * pos.scale).toInt()
        val maxY = height - (element.height * pos.scale).toInt()
        pos.centred = false // once dragged, it stays where you put it
        pos.x = (event.x() - grabX).toInt().coerceIn(0, maxOf(0, maxX))
        pos.y = (event.y() - grabY).toInt().coerceIn(0, maxOf(0, maxY))
        return true
    }

    // Save as soon as a move or resize finishes, not only when the editor closes.
    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging != null) IHateTrophyFishing.saveConfig()
        dragging = null
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val pos = elementAt(mouseX, mouseY)?.position ?: return false
        pos.scale = (pos.scale + scrollY.toFloat() * 0.1f).coerceIn(0.5f, 3.0f)
        IHateTrophyFishing.saveConfig()
        return true
    }

    override fun isPauseScreen() = false

    override fun removed() {
        IHateTrophyFishing.saveConfig()
        super.removed()
    }
}
