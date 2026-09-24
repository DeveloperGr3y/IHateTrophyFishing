package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.util.Compat
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier

/**
 * Draws all displays. Normally they're part of the HUD; while your inventory or a chest is open they're
 * drawn on top of that screen instead, so their buttons can be clicked.
 */
object HudManager {
    val elements: List<HudElement> = listOf(TrackerHud, RecentCatchesHud, StreakHud)

    fun refreshAll() = elements.forEach { it.refresh() }

    fun resetPositions() {
        elements.forEach { it.resetPosition() }
        IHateTrophyFishing.saveConfig()
    }

    fun register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(IHateTrophyFishing.MOD_ID, "displays"),
        ) { graphics, _ -> renderHud(graphics) }

        // Screen-specific events have to be registered each time a screen opens.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register

            ScreenEvents.afterExtract(screen).register { _, graphics, mouseX, mouseY, _ ->
                for (element in elements) {
                    if (element.isVisible(inInventory = true)) {
                        element.draw(graphics, inInventory = true, mouseX.toDouble(), mouseY.toDouble())
                    }
                }
            }

            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                if (event.button() != 0) return@register true
                val clicked = elements.any { it.isVisible(inInventory = true) && it.click(event.x(), event.y()) }
                !clicked // returning false stops the click reaching the inventory
            }
        }
    }

    private fun renderHud(graphics: GuiGraphicsExtractor) {
        val screen = Compat.screen
        // Container screens draw the displays themselves (above); the editor draws its own copies.
        if (screen is AbstractContainerScreen<*> || screen is HudEditScreen) return
        for (element in elements) {
            if (element.isVisible(inInventory = false)) element.draw(graphics, inInventory = false)
        }
    }
}
