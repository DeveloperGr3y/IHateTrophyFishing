package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.features.achievements.Achievement
import io.github.developergr3y.ihtf.features.achievements.Rarity
import io.github.developergr3y.ihtf.util.Sounds
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The achievement unlock moment. Every rarity adds to the one below it:
 *
 *   Common     banner pops in with a bounce + a rising three-note chime
 *   Uncommon   + sparkle burst
 *   Rare       + screen-edge flash in the rarity colour + level-up sound
 *   Epic       + shimmer across the banner, name types itself out
 *   Legendary  + totem-of-undying animation, confetti rain, fireworks
 *   Mythic     + drumroll first, then the name unscrambles into rainbow
 *   Divine     full takeover: screen dims, huge title, totem, fireworks, beacon, heavy confetti
 *
 * Several unlocks queue up. Sounds and one-off effects are scheduled when a banner starts.
 */
object AchievementToast {
    private const val POP_MS = 380L
    private const val EXIT_MS = 300L
    private const val PRELUDE_MS = 1_400L // Mythic / Divine drumroll before the reveal
    private const val CONFETTI_MS = 4_500L

    private val config get() = IHateTrophyFishing.config.dopamine.achievements

    private class Toast(
        val achievement: Achievement?,
        val count: Int = 0,
        val rarest: Achievement? = null,
    ) {
        val rarity = achievement?.rarity ?: rarest?.rarity ?: Rarity.RARE
        val prelude = if (achievement != null && rarity >= Rarity.MYTHIC) PRELUDE_MS else 0L
        val countUpMs = if (achievement == null) minOf(2_500L, count * 90L) else 0L
        val showMs = prelude + countUpMs + when {
            achievement == null -> 5_000L
            rarity <= Rarity.UNCOMMON -> 6_000L
            rarity <= Rarity.EPIC -> 7_000L
            rarity <= Rarity.MYTHIC -> 9_000L
            else -> 12_000L
        }
        var startedAt = 0L
        val sinceReveal get() = System.currentTimeMillis() - startedAt - prelude
    }

    private class Confetti(
        val x: Float, val speed: Float, val sway: Float, val phase: Float,
        val colour: Int, val size: Int, val born: Long,
    )

    private val queue = ArrayDeque<Toast>()
    private var current: Toast? = null
    private val confetti = mutableListOf<Confetti>()
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

    // --- public API ---

    fun show(achievement: Achievement) {
        if (config.popups) enqueue(Toast(achievement)) else sound("entity.player.levelup", 1f, 0.6f)
    }

    fun showSummary(count: Int, rarest: Achievement?) {
        if (config.popups) enqueue(Toast(null, count, rarest)) else sound("entity.player.levelup", 1f, 0.6f)
    }

    private fun enqueue(toast: Toast) {
        if (queue.size < 12) queue.addLast(toast)
    }

    /** Runs scheduled sounds / effects. Called every client tick. */
    fun tick() {
        val now = System.currentTimeMillis()
        val due = scheduled.filter { it.first <= now }
        scheduled.removeAll(due)
        due.forEach { it.second() }
    }

    // --- when a banner starts: schedule its sounds and one-off effects ---

    private fun begin(toast: Toast, now: Long) {
        toast.startedAt = now
        val reveal = now + toast.prelude
        fun at(offset: Long, action: () -> Unit) = scheduled.add(reveal + offset to action)

        if (toast.achievement == null) {
            // Summary: a tick per number as it counts up, then a chime.
            val step = if (toast.count > 0) toast.countUpMs / toast.count else 0L
            for (i in 1..toast.count) {
                val pitch = 0.8f + 1.2f * i / toast.count
                scheduled.add(now + step * i to { sound("block.note_block.hat", pitch, 0.5f) })
            }
            chimeAt(now + toast.countUpMs, extraNote = true)
            scheduled.add(now + toast.countUpMs + 250 to { sound("entity.player.levelup", 1.1f, 0.8f) })
            return
        }

        val rarity = toast.rarity
        if (toast.prelude > 0) drumroll(now, toast.prelude)

        when {
            rarity == Rarity.DIVINE -> {
                at(0) { sound("block.beacon.activate", 1f, 1f) }
                at(0) { sound("item.totem.use", 1f, 0.7f) }
                at(250) { sound("ui.toast.challenge_complete", 1f, 1f) }
                at(300) { sound("entity.firework_rocket.launch", 1f, 1f) }
                at(800) { sound("entity.firework_rocket.large_blast", 1f, 1f) }
                at(1_100) { sound("entity.firework_rocket.twinkle", 1f, 1f) }
                at(1_500) { sound("entity.firework_rocket.large_blast", 1.2f, 0.9f) }
                at(1_800) { sound("entity.firework_rocket.twinkle_far", 1f, 1f) }
                at(0) { totem(ItemStack(Items.TOTEM_OF_UNDYING)) }
                at(0) { spawnConfetti(240, reveal) }
            }
            rarity >= Rarity.LEGENDARY -> {
                at(0) { sound("item.totem.use", 1f, 0.6f) }
                at(250) { sound("ui.toast.challenge_complete", 1f, 0.9f) }
                at(450) { sound("entity.firework_rocket.blast", 1f, 0.9f) }
                at(750) { sound("entity.firework_rocket.twinkle", 1f, 0.9f) }
                at(0) {
                    val item = if (rarity == Rarity.MYTHIC) ItemStack(Items.NETHER_STAR)
                    else TrophyIcons.icon("goldenfish") ?: ItemStack(Items.GOLD_INGOT)
                    totem(item)
                }
                at(0) { spawnConfetti(if (rarity == Rarity.MYTHIC) 150 else 100, reveal) }
            }
            else -> {
                chimeAt(reveal, extraNote = rarity >= Rarity.UNCOMMON)
                if (rarity >= Rarity.RARE) at(250) { sound("entity.player.levelup", if (rarity == Rarity.EPIC) 0.9f else 1.2f, 0.8f) }
            }
        }
    }

    /** Rising major-triad chime (plus a top note for Uncommon and up). */
    private fun chimeAt(start: Long, extraNote: Boolean) {
        val pitches = if (extraNote) listOf(1.0f, 1.26f, 1.5f, 2.0f) else listOf(1.0f, 1.26f, 1.5f)
        pitches.forEachIndexed { i, p -> scheduled.add(start + i * 90L to { sound("block.note_block.chime", p, 0.8f) }) }
    }

    /** Snare hits that speed up towards the reveal. */
    private fun drumroll(start: Long, length: Long) {
        var t = 0.0
        var gap = 180.0
        while (t < length - 40) {
            val at = start + t.toLong()
            val pitch = 0.8f + (t / length).toFloat() * 0.6f
            scheduled.add(at to { sound("block.note_block.snare", pitch, 0.7f) })
            t += gap
            gap = maxOf(35.0, gap * 0.82)
        }
    }

    private fun totem(item: ItemStack) = Minecraft.getInstance().gameRenderer.displayItemActivation(item)

    private fun sound(name: String, pitch: Float, volume: Float) {
        if (config.sounds) Sounds.play(name, pitch, volume)
    }

    private fun spawnConfetti(count: Int, born: Long) {
        val colours = intArrayOf(
            0xFFFF5555.toInt(), 0xFFFFAA00.toInt(), 0xFFFFFF55.toInt(), 0xFF55FF55.toInt(),
            0xFF55FFFF.toInt(), 0xFF5555FF.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFFFF.toInt(),
        )
        repeat(count) {
            confetti += Confetti(
                x = Random.nextFloat(), speed = 60f + Random.nextFloat() * 110f, sway = 4f + Random.nextFloat() * 10f,
                phase = Random.nextFloat() * 6.28f, colour = colours.random(), size = 2 + Random.nextInt(3),
                born = born - Random.nextLong(0, 900), // staggered so it doesn't fall as one sheet
            )
        }
    }

    // --- drawing ---

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val now = System.currentTimeMillis()
        var toast = current
        if (toast == null || now - toast.startedAt > toast.showMs) {
            toast = queue.removeFirstOrNull()
            current = toast
            if (toast != null) begin(toast, now)
        }

        if (toast?.achievement?.rarity == Rarity.DIVINE) drawDim(graphics, toast, now)
        drawConfetti(graphics, now)
        if (toast == null) return

        val font = Minecraft.getInstance().font
        val achievement = toast.achievement
        when {
            achievement == null -> drawSummary(graphics, font, toast, now)
            achievement.rarity == Rarity.DIVINE -> drawDivine(graphics, font, toast, achievement, now)
            else -> drawBanner(graphics, font, toast, achievement, now)
        }
    }

    /** Bounce in (overshoot) at the start, shrink away at the end. */
    private fun popScale(toast: Toast, now: Long): Float {
        val elapsed = now - toast.startedAt
        val t = (elapsed.toFloat() / POP_MS).coerceIn(0f, 1f)
        val c1 = 1.70158f
        val back = 1f + (c1 + 1f) * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
        val exit = ((toast.showMs - elapsed).toFloat() / EXIT_MS).coerceIn(0f, 1f)
        return (0.2f + 0.8f * back) * exit
    }

    private fun drawBanner(graphics: GuiGraphicsExtractor, font: Font, toast: Toast, a: Achievement, now: Long) {
        val rarity = a.rarity
        val sinceReveal = toast.sinceReveal
        val revealed = sinceReveal >= 0
        val title = if (revealed) "ACHIEVEMENT UNLOCKED · ${rarity.label.uppercase()}" else "SOMETHING RARE…"
        val width = maxOf(font.width(title), font.width(a.name) * 3 / 2, font.width(a.description)) + 24
        val height = 46

        if (revealed && rarity >= Rarity.RARE && sinceReveal < 900) edgeFlash(graphics, rarity, now, sinceReveal)

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(graphics.guiWidth() / 2f, 10f + height / 2f)
        // Building tension during the drumroll: the banner shakes harder and harder.
        if (!revealed) {
            val intensity = 1f + 5f * (1f + sinceReveal.toFloat() / toast.prelude)
            pose.translate(Effects.shake(now, 1, intensity), Effects.shake(now, 2, intensity))
        }
        pose.scale(popScale(toast, now))
        pose.translate(-width / 2f, -height / 2f)

        val colour = rarity.rgb
        graphics.fill(0, 0, width, height, 0xE8141018.toInt())
        graphics.fill(0, 0, width, 2, colour)
        graphics.outline(0, 0, width, height, Effects.withAlpha(colour, 0.7f))
        if (revealed && rarity >= Rarity.EPIC) shimmer(graphics, width, height, sinceReveal)

        graphics.centeredText(font, title, width / 2, 6, Effects.withAlpha(colour, 0.9f))

        pose.pushMatrix()
        pose.translate(width / 2f, 18f)
        pose.scale(1.5f)
        pose.translate(-font.width(a.name) / 2f, 0f)
        drawName(graphics, font, a.name, rarity, now, sinceReveal)
        pose.popMatrix()

        if (revealed) graphics.centeredText(font, a.description, width / 2, 33, 0xFFB0B0B0.toInt())
        if (revealed && rarity >= Rarity.UNCOMMON && sinceReveal < 2_200) {
            Effects.sparkles(graphics, font, now, width, height, count = 4 + rarity.ordinal * 2)
        }
        pose.popMatrix()
    }

    private fun drawDivine(graphics: GuiGraphicsExtractor, font: Font, toast: Toast, a: Achievement, now: Long) {
        val sinceReveal = toast.sinceReveal
        val pose = graphics.pose()
        val title = a.name.uppercase()

        if (sinceReveal in 0..1_199) edgeFlash(graphics, Rarity.DIVINE, now, sinceReveal, rainbow = true)

        pose.pushMatrix()
        pose.translate(graphics.guiWidth() / 2f, graphics.guiHeight() / 2f - 30)
        if (sinceReveal < 0) {
            // Drumroll: the scrambled title shakes in the middle of the dimmed screen.
            pose.translate(Effects.shake(now, 3, 6f), Effects.shake(now, 4, 6f))
            pose.scale(2f)
            graphics.centeredText(font, "§b§k$title", 0, 0, -1)
            graphics.centeredText(font, "§7" + ".".repeat(((now / 250) % 4).toInt()), 0, 12, -1)
            pose.popMatrix()
            return
        }
        pose.scale(popScale(toast, now))

        graphics.centeredText(font, "§b§lDIVINE ACHIEVEMENT", 0, -34, -1)
        pose.pushMatrix()
        pose.scale(3f)
        pose.translate(-font.width(title) / 2f, -4f)
        drawName(graphics, font, title, Rarity.DIVINE, now, sinceReveal)
        pose.popMatrix()
        graphics.centeredText(font, "§f${a.description}", 0, 26, -1)
        graphics.centeredText(font, "§7You actually did it.", 0, 40, -1)

        pose.pushMatrix()
        pose.translate(-font.width(title) * 1.5f - 20, -40f)
        Effects.sparkles(graphics, font, now, (font.width(title) * 3f + 40).toInt(), 90, count = 16)
        pose.popMatrix()
        pose.popMatrix()
    }

    private fun drawSummary(graphics: GuiGraphicsExtractor, font: Font, toast: Toast, now: Long) {
        val elapsed = now - toast.startedAt
        val counted = if (toast.countUpMs == 0L) toast.count
        else (toast.count * elapsed / toast.countUpMs).toInt().coerceIn(0, toast.count)
        val done = counted == toast.count
        val title = "ACHIEVEMENTS UNLOCKED"
        val big = "+$counted"
        val sub = toast.rarest?.let { "§7…including ${it.rarity.colour}${it.name} §7(${it.rarity.label})" } ?: "§7From your existing progress"
        val width = maxOf(font.width(title), font.width(big) * 2, font.width(sub)) + 30
        val height = 50
        val pose = graphics.pose()

        pose.pushMatrix()
        pose.translate(graphics.guiWidth() / 2f, 10f + height / 2f)
        pose.scale(popScale(toast, now))
        pose.translate(-width / 2f, -height / 2f)
        val colour = Rarity.RARE.rgb
        graphics.fill(0, 0, width, height, 0xE8141018.toInt())
        graphics.fill(0, 0, width, 2, colour)
        graphics.outline(0, 0, width, height, Effects.withAlpha(colour, 0.7f))
        graphics.centeredText(font, title, width / 2, 6, Effects.withAlpha(colour, 0.9f))
        pose.pushMatrix()
        pose.translate(width / 2f, 17f)
        pose.scale(2f)
        graphics.centeredText(font, big, 0, 0, if (done) 0xFFFFAA00.toInt() else -1)
        pose.popMatrix()
        if (done) {
            graphics.centeredText(font, sub, width / 2, 37, -1)
            Effects.sparkles(graphics, font, now, width, height, count = 8)
        }
        pose.popMatrix()
    }

    /**
     * Common-Rare: the name in the rarity colour. Epic / Legendary: typed out letter by letter.
     * Mythic / Divine: scrambled until the reveal, then unscrambles left to right into rainbow.
     */
    private fun drawName(graphics: GuiGraphicsExtractor, font: Font, name: String, rarity: Rarity, now: Long, sinceReveal: Long) {
        when {
            rarity >= Rarity.MYTHIC -> {
                val clearChars = if (sinceReveal < 0) 0 else (name.length * sinceReveal / 700).toInt().coerceIn(0, name.length)
                val clear = name.take(clearChars)
                Effects.rainbowText(graphics, font, clear, now)
                if (clearChars < name.length) graphics.text(font, "§k" + name.drop(clearChars), font.width(clear), 0, rarity.rgb, true)
            }
            rarity >= Rarity.EPIC -> {
                val typed = name.take((sinceReveal / 40).toInt().coerceIn(0, name.length))
                graphics.text(font, typed, 0, 0, rarity.rgb, true)
            }
            else -> graphics.text(font, name, 0, 0, rarity.rgb, true)
        }
    }

    /** A glow around the screen edges that fades out. */
    private fun edgeFlash(graphics: GuiGraphicsExtractor, rarity: Rarity, now: Long, sinceReveal: Long, rainbow: Boolean = false) {
        val fade = 1f - sinceReveal / 900f
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        for ((thickness, strength) in listOf(10 to 0.18f, 5 to 0.3f, 2 to 0.55f)) {
            val colour = Effects.withAlpha(if (rainbow) Effects.rainbow(now / 600f) else rarity.rgb, strength * fade)
            graphics.fill(0, 0, w, thickness, colour)
            graphics.fill(0, h - thickness, w, h, colour)
            graphics.fill(0, 0, thickness, h, colour)
            graphics.fill(w - thickness, 0, w, h, colour)
        }
    }

    /** A bright band sweeping across the banner. */
    private fun shimmer(graphics: GuiGraphicsExtractor, width: Int, height: Int, sinceReveal: Long) {
        val cycle = 1_300L
        val x = ((sinceReveal % cycle).toFloat() / cycle * (width + 40) - 20).toInt()
        for ((offset, alpha) in listOf(0 to 0.10f, 3 to 0.18f, 6 to 0.10f)) {
            val x0 = (x + offset).coerceIn(0, width)
            val x1 = (x + offset + 3).coerceIn(0, width)
            if (x1 > x0) graphics.fill(x0, 2, x1, height - 1, Effects.withAlpha(0xFFFFFFFF.toInt(), alpha))
        }
    }

    private fun drawDim(graphics: GuiGraphicsExtractor, toast: Toast, now: Long) {
        val elapsed = now - toast.startedAt
        val fadeIn = (elapsed / 400f).coerceIn(0f, 1f)
        val fadeOut = ((toast.showMs - elapsed) / 600f).coerceIn(0f, 1f)
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Effects.withAlpha(0xFF000000.toInt(), 0.6f * fadeIn * fadeOut))
    }

    private fun drawConfetti(graphics: GuiGraphicsExtractor, now: Long) {
        if (confetti.isEmpty()) return
        confetti.removeAll { now - it.born > CONFETTI_MS }
        val w = graphics.guiWidth()
        for (c in confetti) {
            val age = now - c.born
            if (age < 0) continue
            val y = -8f + c.speed * age / 1000f
            val x = c.x * w + sin(age / 280.0 + c.phase).toFloat() * c.sway
            val fade = (1f - age.toFloat() / CONFETTI_MS).coerceIn(0f, 1f)
            graphics.fill(x.toInt(), y.toInt(), x.toInt() + c.size, y.toInt() + c.size + 1, Effects.withAlpha(c.colour, 0.3f + 0.7f * fade))
        }
    }
}
