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
 */
object Roulette {
    const val CARD_WIDTH = 44
    const val CARD_GAP = 4
    const val STEP = CARD_WIDTH + CARD_GAP

    data class Card(val key: String, val tier: TrophyTier)

    class Spin(val key: String, val tier: TrophyTier) {
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

        /** How far along the strip the centre marker is, in pixels; fast at first, easing out to a stop. */
        fun scroll(now: Long): Float {
            val t = ((now - startedAt).toFloat() / spinMs).coerceIn(0f, 1f)
            val target = winnerIndex * STEP + CARD_WIDTH / 2f + landOffset
            return target * (1f - (1f - t).pow(4))
        }

        fun landed(now: Long) = now - startedAt >= spinMs

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

    /** [firstOfTier] = you had never caught this tier of this trophy before. */
    fun onCatch(key: String, tier: TrophyTier, firstOfTier: Boolean) {
        if (!IHateTrophyFishing.config.dopamine.enabled) return
        val mode = when (tier) {
            TrophyTier.DIAMOND -> config.diamonds
            TrophyTier.GOLD -> config.golds
            else -> return
        }
        if (mode == RouletteMode.OFF || (mode == RouletteMode.FIRST_ONLY && !firstOfTier)) return
        start(Spin(key, tier))
    }

    /** /ihtf roulette [gold]: spin on a random trophy fish, whatever the settings say. */
    fun test(tier: TrophyTier) {
        val key = Trophies.all.values.filter { !it.isFrog }.random().key
        start(Spin(key, tier))
    }

    private fun start(spin: Spin) {
        if (current == null) begin(spin) else if (queue.size < 3) queue.addLast(spin)
    }

    private fun begin(spin: Spin) {
        spin.startedAt = System.currentTimeMillis()
        current = spin
        lastCard = -1
        landedPlayed = false
    }

    fun tick() {
        val spin = current ?: return
        val now = System.currentTimeMillis()
        if (now - spin.startedAt > spin.totalMs) {
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
