package io.github.developergr3y.ihtf.features

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.config.RouletteMode
import io.github.developergr3y.ihtf.trophy.Trophies
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.util.Sounds
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/**
 * CS:GO case-opening style roulette for Gold and Diamond catches: a strip of trophy cards scrolls past a centre
 * marker, slows down, and always stops on the trophy you actually caught. Hypixel has already decided the tier by
 * the time the chat message arrives, so this is pure showmanship.
 *
 * With `hideMessage` on, the catch message is held back while the strip spins and shown once it lands, so chat
 * doesn't spoil the result.
 */
object Roulette {
    const val CARD_WIDTH = 44
    const val CARD_GAP = 4
    const val STEP = CARD_WIDTH + CARD_GAP

    data class Card(val key: String, val tier: TrophyTier)

    /** [deferredMessage] = re-adds the hidden chat message; called once, when the spin lands. */
    class Spin(val key: String, val tier: TrophyTier, private val deferredMessage: (() -> Unit)? = null) {
        val isDiamond = tier == TrophyTier.DIAMOND
        val spinMs = if (isDiamond) 4_000L else 2_500L
        val holdMs = if (isDiamond) 2_200L else 1_500L
        val totalMs get() = spinMs + holdMs

        private val cardCount = if (isDiamond) 45 else 30
        val winnerIndex = cardCount - 5
        val cards: List<Card> = buildCards()

        /** Where on the winning card the marker stops, so it doesn't always land dead centre. */
        private val landOffset = (Random.nextFloat() - 0.5f) * CARD_WIDTH * 0.7f

        var startedAt = 0L
        private var revealed = false

        /** How far along the strip the centre marker is, in pixels; fast at first, easing out to a stop. */
        fun scroll(now: Long): Float {
            val t = ((now - startedAt).toFloat() / spinMs).coerceIn(0f, 1f)
            val target = winnerIndex * STEP + CARD_WIDTH / 2f + landOffset
            return target * (1f - (1f - t).pow(4))
        }

        fun landed(now: Long) = now - startedAt >= spinMs

        /** Puts the held-back chat message back. Safe to call more than once. */
        fun revealMessage() {
            if (revealed) return
            revealed = true
            deferredMessage?.invoke()
        }

        private fun buildCards(): List<Card> {
            val frog = Trophies.isFrog(key)
            val pool = Trophies.all.values.filter { it.isFrog == frog }.map { it.key }.ifEmpty { listOf(key) }
            return List(cardCount) { i ->
                if (i == winnerIndex) Card(key, tier) else Card(pool.random(), randomFillerTier())
            }
        }

        /** Mostly Bronze and Silver, with the odd Gold or Diamond scrolling past to tease you. */
        private fun randomFillerTier() = when (Random.nextInt(100)) {
            in 0 until 55 -> TrophyTier.BRONZE
            in 55 until 85 -> TrophyTier.SILVER
            in 85 until 96 -> TrophyTier.GOLD
            else -> TrophyTier.DIAMOND
        }
    }

    private val config get() = IHateTrophyFishing.config.dopamine.roulette

    var current: Spin? = null
        private set
    private val queue = ArrayDeque<Spin>()
    private var lastCard = -1
    private var landedPlayed = false

    /**
     * [firstOfTier] = you had never caught this tier of this trophy before.
     * [showMessage] = puts the original chat message back into chat.
     *
     * @return true if the message is now held by the roulette, so the caller should cancel it.
     * False means no spin started (or hiding is off) and the message should go through as normal.
     */
    fun onCatch(key: String, tier: TrophyTier, firstOfTier: Boolean, showMessage: (() -> Unit)? = null): Boolean {
        if (!IHateTrophyFishing.config.dopamine.enabled) return false
        val mode = when (tier) {
            TrophyTier.DIAMOND -> config.diamonds
            TrophyTier.GOLD -> config.golds
            else -> return false
        }
        if (mode == RouletteMode.OFF || (mode == RouletteMode.FIRST_ONLY && !firstOfTier)) return false
        val deferred = showMessage.takeIf { config.hideMessage }
        val started = start(Spin(key, tier, deferred))
        return started && deferred != null
    }

    /** /ihtf roulette [gold]: spin on a random trophy fish, whatever the settings say. */
    fun test(tier: TrophyTier) {
        val key = Trophies.all.values.filter { !it.isFrog }.random().key
        start(Spin(key, tier))
    }

    /** @return false if the spin was dropped because the queue is full. */
    private fun start(spin: Spin): Boolean {
        when {
            current == null -> begin(spin)
            queue.size < 3 -> queue.addLast(spin)
            else -> return false
        }
        return true
    }

    private fun begin(spin: Spin) {
        spin.startedAt = System.currentTimeMillis()
        current = spin
        lastCard = -1
        landedPlayed = false
    }

    /** Shows every held-back message and stops all spins, e.g. on disconnect or world change. */
    fun flush() {
        current?.revealMessage()
        queue.forEach { it.revealMessage() }
        queue.clear()
        current = null
    }

    fun tick() {
        val spin = current ?: return
        val now = System.currentTimeMillis()
        if (now - spin.startedAt > spin.totalMs) {
            spin.revealMessage() // in case a lag spike skipped the landing tick
            current = null
            queue.removeFirstOrNull()?.let { begin(it) }
            return
        }

        if (!spin.landed(now)) {
            // Tick every time a card passes the marker, like the real thing.
            val card = floor(spin.scroll(now) / STEP).toInt()
            if (card != lastCard) {
                lastCard = card
                playSound("block.note_block.hat", 1.9f, 0.45f)
            }
        } else if (!landedPlayed) {
            landedPlayed = true
            spin.revealMessage()
            if (spin.isDiamond) {
                playSound("ui.toast.challenge_complete", 1f, 0.9f)
                playSound("entity.firework_rocket.twinkle", 1f, 0.8f)
            } else {
                playSound("entity.player.levelup", 1.3f, 0.8f)
            }
        }
    }

    private fun playSound(name: String, pitch: Float, volume: Float) {
        if (config.sounds) Sounds.play(name, pitch, volume)
    }
}
