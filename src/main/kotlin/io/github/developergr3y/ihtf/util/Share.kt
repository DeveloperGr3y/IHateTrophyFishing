package io.github.developergr3y.ihtf.util

import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * [Share to Party] [Share to Guild] [Share to All] buttons for chat messages. Clicking one only fills in your chat
 * box (/pc, /gc or /ac); nothing is sent until you press Enter.
 */
object Share {
    fun buttons(text: String): Component = Component.literal("")
        .append(button("§d[Share to Party]", "/pc $text"))
        .append(Component.literal(" "))
        .append(button("§2[Share to Guild]", "/gc $text"))
        .append(Component.literal(" "))
        .append(button("§f[Share to All]", "/ac $text"))

    private fun button(label: String, command: String) = Component.literal(label).withStyle {
        it.withClickEvent(ClickEvent.SuggestCommand(command))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§7Puts this in your chat box so you can send it:\n§f$command")))
    }
}
