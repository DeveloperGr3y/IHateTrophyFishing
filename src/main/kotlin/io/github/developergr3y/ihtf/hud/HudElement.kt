package io.github.developergr3y.ihtf.hud

import com.google.gson.annotations.Expose
import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.tracker.Tracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Where a display sits on screen (in GUI pixels) and how big it is. Saved in the config. */
class HudPosition(
    @Expose @JvmField var x: Int = 5,
    @Expose @JvmField var y: Int = 5,
    @Expose @JvmField var scale: Float = 1f,
)

/**
 * One line of a display. Lines with [onClick] are buttons while your inventory is open.
 * [iconKey] draws that trophy's icon before the text; [indent] leaves the same gap without an icon
 * (so sub-lines line up with the trophy names).
 */
class HudLine(
    val text: String,
    val iconKey: String? = null,
    val indent: Boolean = iconKey != null,
    val onClick: (() -> Unit)? = null,
)

/**
 * Base for the on-screen displays. A display only says *what* to show ([build]);
 * this class handles position/scale, drawing, hover + click, and caching.
 *
 * Lines are rebuilt when something changes (a catch, view switch, reset…) and otherwise only every
 * "Refresh Interval" seconds, so numbers like catches per hour don't flicker every frame.
 */
abstract class HudElement(val label: String) {
    abstract val position: HudPosition

    /** Put this display back to its default position and size. */
    abstract fun resetPosition()

    /** Turned on in the config (the GUI editor shows every enabled display, wherever you are). */
    abstract val enabled: Boolean

    /** Enabled, and its Where / Show When settings allow it right now. */
    abstract fun isVisible(inInventory: Boolean): Boolean

    protected abstract fun build(inInventory: Boolean): List<HudLine>

    private var cached: List<HudLine> = emptyList()
    private var builtAt = 0L
    private var builtInInventory = false
    private var needsRebuild = true

    /** Size of the last drawn display, in unscaled pixels. */
    var width = 0
        protected set
    var height = 0
        protected set

    fun refresh() {
        needsRebuild = true
    }

    private fun lines(inInventory: Boolean): List<HudLine> {
        val now = System.currentTimeMillis()
        val refreshMs = IHateTrophyFishing.config.trackers.trophyFish.refreshSeconds * 1000L
        val flashEnded = builtAt < Tracker.flashEndsAt && Tracker.flashEndsAt <= now
        if (needsRebuild || inInventory != builtInInventory || now - builtAt >= refreshMs || flashEnded) {
            cached = build(inInventory)
            builtAt = now
            builtInInventory = inInventory
            needsRebuild = false
        }
        return cached
    }

    /** Draws the text lines from [build]. Displays with their own animations (e.g. the streak counter) override this. */
    open fun draw(graphics: GuiGraphicsExtractor, inInventory: Boolean, mouseX: Double = -1.0, mouseY: Double = -1.0) {
        val font = Minecraft.getInstance().font
        val lines = lines(inInventory)
        val lineHeight = rowHeight()
        val showIcons = IHateTrophyFishing.config.trackers.trophyFish.showIcons
        fun textX(line: HudLine) = PADDING + if (showIcons && line.indent) ICON_SPACE else 0

        width = (lines.maxOfOrNull { textX(it) + font.width(it.text) } ?: 0) + PADDING
        height = lines.size * lineHeight + PADDING * 2 - 1
        val hovered = if (inInventory) lineIndexAt(mouseX, mouseY) else -1

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(position.x.toFloat(), position.y.toFloat())
        pose.scale(position.scale)
        lines.forEachIndexed { i, line ->
            val rowY = PADDING + i * lineHeight
            val y = rowY + (lineHeight - font.lineHeight) / 2 // text centred in the row
            val x = textX(line)
            if (i == hovered && line.onClick != null) {
                graphics.fill(x - 1, y - 1, x + font.width(line.text) + 1, y + font.lineHeight, HOVER)
            }
            if (showIcons) line.iconKey?.let { TrophyIcons.icon(it) }?.let { icon ->
                // Items are 16px; shrink to roughly one line of text.
                pose.pushMatrix()
                pose.translate(PADDING.toFloat(), rowY.toFloat())
                pose.scale(ICON_SCALE)
                graphics.item(icon, 0, 0)
                pose.popMatrix()
            }
            graphics.text(font, line.text, x, y, -1, true)
        }
        pose.popMatrix()
    }

    /** Rows are a bit taller when icons are shown, so the 12px icons don't overlap. */
    private fun rowHeight() =
        Minecraft.getInstance().font.lineHeight + if (IHateTrophyFishing.config.trackers.trophyFish.showIcons) 3 else 1

    fun contains(mouseX: Double, mouseY: Double) =
        mouseX >= position.x && mouseY >= position.y &&
            mouseX <= position.x + width * position.scale &&
            mouseY <= position.y + height * position.scale

    private fun lineIndexAt(mouseX: Double, mouseY: Double): Int {
        if (!contains(mouseX, mouseY)) return -1
        val localY = (mouseY - position.y) / position.scale - PADDING
        val index = (localY / rowHeight()).toInt()
        return if (localY >= 0 && index in cached.indices) index else -1
    }

    /** Runs the clicked line's action. Returns true if a button was clicked. */
    fun click(mouseX: Double, mouseY: Double): Boolean {
        val action = cached.getOrNull(lineIndexAt(mouseX, mouseY))?.onClick ?: return false
        action()
        refresh()
        return true
    }

    companion object {
        const val PADDING = 3
        private const val HOVER = 0x40FFFFFF
        private const val ICON_SCALE = 0.75f
        private const val ICON_SPACE = 14
    }
}
