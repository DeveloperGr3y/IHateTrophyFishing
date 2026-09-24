package io.github.developergr3y.ihtf.features.achievements

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.data.ProfileData
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.hud.AchievementToast
import io.github.developergr3y.ihtf.trophy.Trophies
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.util.Compat
import io.github.developergr3y.ihtf.util.Share
import io.github.developergr3y.ihtf.util.Sounds
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.time.LocalTime

/**
 * Unlocks achievements. Two kinds of rule:
 *  - events (a catch, a streak milestone or break, ...), checked as they happen;
 *  - state (collections, totals, session and day time), checked after catches, menu syncs and every few seconds.
 * Unlocks that come from progress you already had (e.g. after syncing Odger) are summed up in one message
 * instead of a pile of pop-ups.
 */
object Achievements {
    private const val STATE_CHECK_MS = 5_000L
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    private val config get() = IHateTrophyFishing.config.dopamine.achievements
    val isActive get() = IHateTrophyFishing.config.dopamine.enabled && config.enabled

    private var lastStateCheck = 0L
    private var checkedProfile: String? = null
    private var lastCatchAt = 0L
    private var lastGoldAt = 0L
    private var lastDiamondAt = 0L
    private var activeSinceCatch = 0L

    // --- state rules: true when the profile's data meets them ---

    private val fishKeys get() = Trophies.all.values.filter { !it.isFrog }.map { it.key }
    private val frogKeys get() = Trophies.all.values.filter { it.isFrog }.map { it.key }

    private fun ProfileData.count(key: String) = lifetime[key]?.total ?: 0
    private fun ProfileData.lifetimeTier(tier: TrophyTier) = lifetime.values.sumOf { it[tier] }
    private fun ProfileData.ownsAll(keys: List<String>, tier: TrophyTier) = keys.all { (lifetime[it]?.get(tier) ?: 0) > 0 }
    private fun ProfileData.sessionTier(tier: TrophyTier) = session.fish.values.sumOf { it[tier] }

    private val stateRules: Map<String, (ProfileData) -> Boolean> = mapOf(
        "welcome" to { it.total.totalCatches >= 1 },
        "bronze_age" to { it.lifetimeTier(TrophyTier.BRONZE) >= 10 },
        "silver_lining" to { it.lifetimeTier(TrophyTier.SILVER) >= 1 },
        "frog_throat" to { p -> frogKeys.any { p.count(it) > 0 } },
        "warming_up" to { it.total.activeMillis >= 10 * MINUTE },
        "blob_enjoyer" to { it.count("blobfish") >= 25 },
        "horse_girl" to { it.count("lavahorse") >= 10 },
        // Fish pity from /pity, and every fish listed from Odger's menu.
        "paperwork" to { p -> p.pity.keys.any { !Trophies.isFrog(it) } && fishKeys.all { p.lifetime.containsKey(it) } },
        "half_hour" to { it.session.activeMillis >= 30 * MINUTE },
        "golden_boy" to { it.lifetimeTier(TrophyTier.GOLD) >= 1 },
        "centurion" to { it.session.totalCatches >= 100 },
        "patience" to { it.count("slugfish") >= 1 },
        "frequent_flyer" to { it.count("flyfish") >= 25 },
        "raining_frogs" to { it.count("wetlandsfrog") >= 1 },
        "two_hours" to { it.session.activeMillis >= 2 * HOUR },
        "hat_trick" to { it.sessionTier(TrophyTier.GOLD) >= 3 },
        "tourist" to { p -> p.session.fish.count { (key, counts) -> !Trophies.isFrog(key) && counts.total > 0 } >= 5 },
        "diamond_rough" to { it.lifetimeTier(TrophyTier.DIAMOND) >= 1 },
        "thousand_club" to { it.total.totalCatches >= 1_000 },
        "golden_hour" to { it.count("goldenfish") >= 1 },
        "full_house" to { it.ownsAll(fishKeys, TrophyTier.BRONZE) },
        "frog_collector" to { it.ownsAll(frogKeys, TrophyTier.BRONZE) },
        "marathon" to { it.session.activeMillis >= 4 * HOUR },
        "unreadable" to { it.count("obfuscated3") >= 1 },
        "silver_service" to { it.ownsAll(fishKeys, TrophyTier.SILVER) },
        "ten_thousand" to { it.total.totalCatches >= 10_000 },
        "sulphur_sniffer" to { it.count("sulphurskitter") >= 100 },
        "double_diamond" to { it.sessionTier(TrophyTier.DIAMOND) >= 2 },
        "touch_grass" to { it.today().activeMillis >= 8 * HOUR },
        "gold_standard" to { it.ownsAll(fishKeys, TrophyTier.GOLD) },
        "all_frogged_up" to { it.ownsAll(frogKeys, TrophyTier.GOLD) },
        "lucky_day" to { p -> p.today().fish.values.sumOf { it[TrophyTier.DIAMOND] } >= 3 },
        "why" to { it.total.totalCatches >= 100_000 },
        "divine_fish" to { it.ownsAll(fishKeys, TrophyTier.DIAMOND) },
        "divine_frog" to { it.ownsAll(frogKeys, TrophyTier.DIAMOND) },
    )

    // --- events ---

    /** Called for every trophy catch, before [checkState]. */
    fun onCatch(tier: TrophyTier, firstOfTier: Boolean, fromPity: Boolean) {
        if (!isActive) return
        val now = System.currentTimeMillis()
        if (now - lastCatchAt <= 5_000) unlock("double_dip")
        if (LocalTime.now().hour < 5) unlock("night_shift")
        if (fromPity && tier >= TrophyTier.GOLD) unlock("pity_party")
        if (fromPity && tier == TrophyTier.DIAMOND) unlock("pity_diamond")
        if (tier == TrophyTier.GOLD) {
            if (now - lastGoldAt <= 60_000) unlock("loaded_dice")
            lastGoldAt = now
        }
        if (tier == TrophyTier.DIAMOND) {
            if (!firstOfTier) unlock("deja_vu")
            if (now - lastDiamondAt <= 10 * MINUTE) unlock("double_rainbow")
            lastDiamondAt = now
        }
        lastCatchAt = now
        activeSinceCatch = 0
        checkState(fromExistingProgress = false)
    }

    fun onStreak(count: Int) {
        if (!isActive) return
        if (count >= 10) unlock("combo_starter")
        if (count >= 50) unlock("on_fire")
        if (count >= 100) unlock("unstoppable")
        if (count >= 250) unlock("unstoppable_250")
        if (count >= 500) unlock("streak_legends")
        if (count >= 1_000) unlock("glitch")
    }

    fun onStreakLost(count: Int) {
        if (!isActive) return
        if (count >= 25) unlock("combo_breaker")
        if (count >= 100) unlock("heartbreak")
    }

    fun onClutch() {
        if (isActive) unlock("clutch")
    }

    /** Active fishing time, from the tracker's clock (so AFK time doesn't count). */
    fun onActiveTime(elapsedMs: Long) {
        if (!isActive || lastCatchAt == 0L) return
        activeSinceCatch += elapsedMs
        if (activeSinceCatch >= 3 * MINUTE) unlock("nothing_biting")
    }

    /** After reading Odger / Ribery / /pity: whatever this unlocks came from existing progress. */
    fun onSynced() {
        if (isActive) checkState(fromExistingProgress = true)
    }

    fun tick(client: Minecraft) {
        if (!isActive || client.player == null) return
        // First time we see this profile (e.g. after installing), unlock from existing progress in one go.
        val profileKey = Storage.profileKey
        if (profileKey != checkedProfile) {
            checkedProfile = profileKey
            checkState(fromExistingProgress = true)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastStateCheck >= STATE_CHECK_MS) {
            lastStateCheck = now
            checkState(fromExistingProgress = false)
        }
    }

    private fun checkState(fromExistingProgress: Boolean) {
        val profile = Storage.profile()
        val newly = stateRules.filter { (id, rule) -> id !in profile.achievements && rule(profile) }.keys
        if (newly.isEmpty()) return
        if (fromExistingProgress && newly.size > 2) {
            val now = System.currentTimeMillis()
            newly.forEach { profile.achievements[it] = now }
            Storage.markDirty()
            IHateTrophyFishing.chat("§eUnlocked §f§l${newly.size} achievements§e from your existing progress! §7See them with §6/ihtf achievements")
            if (config.popups) AchievementToast.showSummary(newly.size)
            playSound(Rarity.RARE)
        } else {
            newly.forEach { unlock(it) }
        }
    }

    private fun unlock(id: String) {
        val achievement = AchievementList.byId[id] ?: return
        val profile = Storage.profile()
        if (id in profile.achievements) return
        profile.achievements[id] = System.currentTimeMillis()
        Storage.markDirty()

        if (config.popups) AchievementToast.show(achievement)
        playSound(achievement.rarity)
        if (config.chat) {
            val rarity = achievement.rarity
            val message = Component.literal(
                "§6[IHTF] §eAchievement unlocked: ${rarity.colour}§l${achievement.name}§r §8(${rarity.label}) §7${achievement.description} ",
            ).append(Share.buttons("[IHTF] I unlocked ${achievement.name} (${rarity.label})! ${achievement.description}"))
            Compat.chat.addClientSystemMessage(message)
        }
    }

    private fun playSound(rarity: Rarity) {
        if (!config.sounds) return
        when (rarity) {
            Rarity.COMMON -> Sounds.play("entity.experience_orb.pickup", 1f, 0.8f)
            Rarity.UNCOMMON -> Sounds.play("entity.experience_orb.pickup", 1.4f, 0.9f)
            Rarity.RARE -> Sounds.play("entity.player.levelup", 1.2f, 0.8f)
            Rarity.EPIC -> Sounds.play("entity.player.levelup", 0.9f, 0.9f)
            Rarity.LEGENDARY, Rarity.MYTHIC -> Sounds.play("ui.toast.challenge_complete", 1f, 0.9f)
            Rarity.DIVINE -> {
                Sounds.play("ui.toast.challenge_complete", 1f, 1f)
                Sounds.play("entity.firework_rocket.twinkle", 1f, 0.9f)
            }
        }
    }

    fun unlockedCount(profile: ProfileData = Storage.profile()) = profile.achievements.keys.count { it in AchievementList.byId }
}
