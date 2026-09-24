package io.github.developergr3y.ihtf.features

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.trophy.FishCounts
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.util.Compat
import io.github.developergr3y.ihtf.util.Sounds
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * osu!-style trophy streak: trophies caught in a row, as long as each one comes within [GRACE_MS] of the last.
 *
 * The rules are deliberately fixed (not settings) so everyone's streaks are comparable. 15s was picked from real
 * catch gaps: the median gap is ~5s but 1 in 10 is over ~11-15s (a few casts without a trophy), so 15s forgives
 * normal bad luck while 25 and 50 stay milestones you have to earn.
 */
object Streak {
    const val GRACE_MS = 15_000L

    /** Below this the counter stays hidden, so it isn't flickering "x1" all the time. */
    const val SHOW_FROM = 3

    /** Streaks at least this long are posted to chat when they end. */
    const val ANNOUNCE_FROM = 10

    private val milestones = listOf(10, 25, 50, 100, 250, 500, 1000)

    private val config get() = IHateTrophyFishing.config.dopamine.streak
    val isActive get() = IHateTrophyFishing.config.dopamine.enabled && config.enabled

    var count = 0
        private set
    var lastCatchAt = 0L
        private set
    private var startedAt = 0L
    private var tiers = FishCounts()

    /** When the last streak ended, and how long it was (for the "COMBO BREAK" flash). */
    var brokenAt = 0L
        private set
    var brokenCount = 0
        private set

    /** When this streak passed your all-time best (for the "NEW BEST!" flash). */
    var newBestAt = 0L
        private set
    private var beatBestThisStreak = false

    val best get() = Storage.profile().bestStreak

    fun onCatch(tier: TrophyTier, amount: Int) {
        if (!isActive) return
        val now = System.currentTimeMillis()
        if (count > 0 && now - lastCatchAt > GRACE_MS) end(now)
        if (count == 0) {
            startedAt = now
            tiers = FishCounts()
            beatBestThisStreak = false
        }

        val before = count
        count += amount
        tiers.add(tier, amount)
        lastCatchAt = now

        val profile = Storage.profile()
        val previousBest = profile.bestStreak
        if (count > previousBest) {
            profile.bestStreak = count
            Storage.markDirty()
            // Only celebrate beating a best that meant something, and only once per streak.
            if (!beatBestThisStreak && previousBest >= ANNOUNCE_FROM) {
                beatBestThisStreak = true
                newBestAt = now
                playSound("ui.toast.challenge_complete", 1f, 0.8f)
            }
        }

        val milestone = milestones.lastOrNull { it in (before + 1)..count }
        when {
            milestone != null -> playSound("entity.player.levelup", 0.9f + milestones.indexOf(milestone) * 0.1f, 0.8f)
            // osu!-style hit sound that climbs in pitch as the streak grows.
            count >= SHOW_FROM -> playSound("block.note_block.pling", 0.7f + minOf(count, 100) / 100f * 1.3f, 0.4f)
        }
    }

    fun tick(client: Minecraft) {
        if (count == 0) return
        // Turned off, or left the game: drop the streak quietly.
        if (!isActive || client.player == null) {
            reset()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCatchAt > GRACE_MS) end(now)
    }

    private fun end(now: Long) {
        if (count >= SHOW_FROM) {
            brokenAt = now
            brokenCount = count
            playSound("entity.item.break", 0.7f, 0.9f)
        }
        if (count >= ANNOUNCE_FROM) announce()
        reset()
    }

    private fun reset() {
        count = 0
        beatBestThisStreak = false
    }

    /** Posts the finished streak to your own chat, with buttons that pre-fill /pc or /gc so you can share it. */
    private fun announce() {
        val duration = formatDuration(lastCatchAt - startedAt)
        val rare = listOf(TrophyTier.GOLD, TrophyTier.DIAMOND)
            .filter { tiers[it] > 0 }
            .joinToString(", ") { "${tiers[it]} ${it.displayName}" }
        val rareText = if (rare.isEmpty()) "" else " ($rare)"
        val bestText = if (beatBestThisStreak) " New personal best!" else ""

        val shareText = "[IHTF] I just hit a $count trophy streak!$rareText in $duration.$bestText"
        val message = Component.literal(
            "§6[IHTF] §eStreak over: §f§l$count trophies in a row!§r§7$rareText in $duration · Best: $best" +
                (if (beatBestThisStreak) " §a§lNEW BEST!" else "") + " ",
        )
            .append(shareButton("§d[Share to Party]", "/pc $shareText"))
            .append(Component.literal(" "))
            .append(shareButton("§2[Share to Guild]", "/gc $shareText"))
        Compat.chat.addClientSystemMessage(message)
    }

    private fun shareButton(label: String, command: String) = Component.literal(label).withStyle {
        it.withClickEvent(ClickEvent.SuggestCommand(command))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§7Puts this in your chat box so you can send it:\n§f$command")))
    }

    private fun playSound(name: String, pitch: Float, volume: Float) {
        if (config.sounds) Sounds.play(name, pitch, volume)
    }

    /** 42s -> "42s", 252s -> "4m 12s", 4000s -> "1h 6m". */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
        }
    }
}
