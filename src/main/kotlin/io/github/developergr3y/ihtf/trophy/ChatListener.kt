package io.github.developergr3y.ihtf.trophy

import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.odds.OddsImport
import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.util.Compat
import io.github.developergr3y.ihtf.util.colourBefore
import io.github.developergr3y.ihtf.util.toLegacyString
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

/**
 * Reads Hypixel's chat messages.
 *
 * We listen on ALLOW_GAME because Fabric gives every mod the *original* message there, before any mod
 * (e.g. one that rewrites trophy messages, or hides bronze and duplicate catches)
 * changes or hides it. The only thing we ever hide is a Gold/Diamond catch while its roulette is spinning
 * (Roulette's "Hide Message" option); it is added back to chat as soon as the spin lands.
 */
object ChatListener {
    /**
     * Colour codes and Hypixel's icon are stripped first. Examples:
     *   "TROPHY FISH! You caught a Karate Fish BRONZE!"
     *   "♔ TROPHY FROG! You caught a Common Frog BRONZE!"
     *   "♔ TROPHY FROG! You caught Common Frog BRONZE x3!"   (several at once)
     *
     * Anchored to the whole line (only Hypixel's icon may come first), so other players' messages don't count,
     * e.g. "Guild > [MVP+] Someone: TROPHY FISH! You caught a Skeleton Fish DIAMOND! (63)" from a chat-sharing mod.
     */
    private val trophyCatch =
        Regex("^(?:\\S+ )?TROPHY (?:FISH|FROG)! You caught (?:an? )?(.+?) (BRONZE|SILVER|GOLD|DIAMOND)(?: x(\\d+))?!$")

    /** e.g. "You are playing on profile: Apple" or "You are playing on profile: Apple (Co-op)". */
    private val profile = Regex("^You are playing on profile: (\\w+)")

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            overlay || onMessage(message)
        }
    }

    /** @return false to hide the message (the roulette will show it once the spin lands). */
    private fun onMessage(message: Component): Boolean {
        val text = message.string.stripFormatting().trim()

        profile.find(text)?.let {
            Storage.setProfile(it.groupValues[1])
            return true
        }

        OddsImport.onChat(text)

        trophyCatch.find(text)?.let {
            val name = it.groupValues[1].trim()
            val tier = TrophyTier.fromName(it.groupValues[2]) ?: return true
            val amount = it.groupValues[3].toIntOrNull() ?: 1
            // Hypixel colours the name by item rarity, e.g. "§r§5Karate Fish".
            val colour = colourBefore(message.toLegacyString(), name)
            val hidden = Tracker.onCatch(name, tier, colour, amount) {
                Compat.chat.addClientSystemMessage(message)
            }
            return !hidden
        }
        return true
    }
}
