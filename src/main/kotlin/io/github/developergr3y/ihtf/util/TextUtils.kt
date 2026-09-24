package io.github.developergr3y.ihtf.util

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Optional

private val colourCode = Regex("§[0-9a-f]")

/** RGB value -> "§" code for Minecraft's 16 chat colours (fixed values, same in every version). */
private val legacyCodes = mapOf(
    0x000000 to '0', 0x0000AA to '1', 0x00AA00 to '2', 0x00AAAA to '3',
    0xAA0000 to '4', 0xAA00AA to '5', 0xFFAA00 to '6', 0xAAAAAA to '7',
    0x555555 to '8', 0x5555FF to '9', 0x55FF55 to 'a', 0x55FFFF to 'b',
    0xFF5555 to 'c', 0xFF55FF to 'd', 0xFFFF55 to 'e', 0xFFFFFF to 'f',
)

/**
 * Turns a chat component back into a "§"-coded string, keeping colours.
 * (Component.getString() throws the colours away, and we need them to know a fish's rarity.)
 */
fun Component.toLegacyString(): String {
    val out = StringBuilder()
    visit({ style: Style, text: String ->
        if (text.isNotEmpty()) {
            style.color?.value?.let { legacyCodes[it] }?.let { out.append('§').append(it) }
            out.append(text)
        }
        Optional.empty<Unit>()
    }, Style.EMPTY)
    return out.toString()
}

/** The last colour code written before [name] in [legacy], e.g. "§9" for "…You caught a §r§9Lavahorse…". */
fun colourBefore(legacy: String, name: String): String? {
    val text = legacy.replace("§k", "")
    val index = text.indexOf(name)
    if (index < 0) return null
    return colourCode.findAll(text.substring(0, index)).lastOrNull()?.value
}

/** The first colour code in [legacy], e.g. an item name's rarity colour. */
fun firstColour(legacy: String): String? = colourCode.find(legacy)?.value
