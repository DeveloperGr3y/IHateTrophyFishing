package io.github.developergr3y.ihtf.trophy

import com.google.gson.Gson
import com.google.gson.annotations.Expose
import io.github.developergr3y.ihtf.data.ProfileData

enum class TrophyTier(val colour: String) {
    BRONZE("§8"),
    SILVER("§7"),
    GOLD("§6"),
    DIAMOND("§b"),
    ;

    val displayName get() = name.lowercase().replaceFirstChar { it.uppercase() }
    val formatted get() = "$colour$displayName"

    companion object {
        fun fromName(raw: String): TrophyTier? = entries.firstOrNull { raw.trim().uppercase() == it.name }
    }
}

/** One trophy fish or trophy frog, from the bundled trophies.json (generated from the NEU item repo, MIT). */
class TrophyInfo {
    lateinit var key: String
    lateinit var name: String

    /** "fish" or "frog". */
    lateinit var type: String

    /** Item rarity: 0 Common, 1 Uncommon, 2 Rare, 3 Epic, 4 Legendary. */
    var rank = 0
    lateinit var colour: String

    /** Hypixel resource pack item model, e.g. "hypixel_skyblock:item/fishing/trophy/lava/karate_fish_bronze". */
    lateinit var model: String

    /** Default pity thresholds (catches without that tier before the next one is guaranteed). */
    var goldPity = 100
    var diamondPity = 600

    val isFrog get() = type == "frog"
}

object Trophies {
    val all: Map<String, TrophyInfo> by lazy {
        val stream = Trophies::class.java.getResourceAsStream("/ihatetrophyfishing/trophies.json")!!
        stream.reader().use { Gson().fromJson(it, Array<TrophyInfo>::class.java) }.associateBy { it.key }
    }

    /** Stable id for a trophy, e.g. "Steaming-Hot Flounder" -> "steaminghotflounder", "Obfuscated 1" -> "obfuscated1". */
    fun key(displayName: String) = displayName.stripFormatting().lowercase().replace(nonAlphanumeric, "")

    private val nonAlphanumeric = Regex("[^a-z0-9]")

    operator fun get(key: String) = all[key]

    fun isFrog(key: String) = all[key]?.isFrog == true

    /** Rarity colour as Hypixel showed it (chat / menus), falling back to the bundled data. */
    fun colour(key: String, profile: ProfileData): String = profile.colours[key] ?: all[key]?.colour ?: "§f"

    /** Rarity rank for sorting (rarest last). Unknown trophies are ranked by their colour. */
    fun rank(key: String, profile: ProfileData): Int =
        all[key]?.rank ?: rankByColour.indexOf(colour(key, profile)).let { if (it < 0) 0 else it }

    private val rankByColour = listOf("§f", "§a", "§9", "§5", "§6", "§d")

    /** e.g. "§5Karate Fish" */
    fun colouredName(key: String, profile: ProfileData) = colour(key, profile) + (profile.names[key] ?: all[key]?.name ?: key)
}

private val formattingCode = Regex("§.")

fun String.stripFormatting() = replace(formattingCode, "")

/** How many of one trophy were caught, per tier. */
class FishCounts {
    @Expose var bronze = 0
    @Expose var silver = 0
    @Expose var gold = 0
    @Expose var diamond = 0

    val total get() = bronze + silver + gold + diamond

    operator fun get(tier: TrophyTier) = when (tier) {
        TrophyTier.BRONZE -> bronze
        TrophyTier.SILVER -> silver
        TrophyTier.GOLD -> gold
        TrophyTier.DIAMOND -> diamond
    }

    operator fun set(tier: TrophyTier, value: Int) {
        when (tier) {
            TrophyTier.BRONZE -> bronze = value
            TrophyTier.SILVER -> silver = value
            TrophyTier.GOLD -> gold = value
            TrophyTier.DIAMOND -> diamond = value
        }
    }

    fun add(tier: TrophyTier, amount: Int = 1) {
        this[tier] = this[tier] + amount
    }

    fun sameAs(other: FishCounts) = TrophyTier.entries.all { this[it] == other[it] }
}

/**
 * Pity progress as shown in Hypixel's /pity menu ("Progress to GOLD: 37/100").
 * Kept up to date between visits by counting catches (a Gold resets the Gold counter, a Diamond the Diamond one).
 */
class PityProgress {
    @Expose var gold = 0
    @Expose var goldTotal = 0
    @Expose var diamond = 0
    @Expose var diamondTotal = 0

    fun onCatch(tier: TrophyTier, amount: Int) {
        when (tier) {
            TrophyTier.GOLD -> gold = 0
            TrophyTier.DIAMOND -> diamond = 0
            else -> {
                gold += amount
                diamond += amount
            }
        }
    }
}

/** Setting for hiding trophies you already own a tier of, e.g. to only see what's left when going for Golds. */
enum class HideOwned(private val label: String, private val tier: TrophyTier?) {
    OFF("Show all", null),
    GOLD("Hide if I have Gold", TrophyTier.GOLD),
    DIAMOND("Hide if I have Diamond", TrophyTier.DIAMOND),
    ;

    /** True if [key] should be hidden: you've caught at least one of this setting's tier (all-time). */
    fun hides(key: String, profile: ProfileData): Boolean {
        val tier = tier ?: return false
        return (profile.lifetime[key]?.get(tier) ?: 0) > 0
    }

    val tierName get() = tier?.displayName

    override fun toString() = label
}

/** Catches left until a guaranteed [tier]. 0 means the next one is guaranteed. */
data class PityStatus(val tier: TrophyTier, val catchesLeft: Int)

/**
 * Which pity counters to show for a trophy: Gold until you have a Gold, then Diamond until you have a Diamond
 * (or both, if [showBoth]). Uses the /pity menu's numbers when we have them, otherwise estimates from your
 * all-time counts (community wiki rule: guaranteed after 100 / 600 catches without that tier).
 */
fun pityStatuses(key: String, profile: ProfileData, showBoth: Boolean): List<PityStatus> {
    val counts = profile.lifetime[key] ?: FishCounts()
    val info = Trophies[key]
    val synced = profile.pity[key]

    fun status(tier: TrophyTier): PityStatus? {
        if (counts[tier] > 0) return null
        if (synced != null) {
            val (progress, total) = if (tier == TrophyTier.GOLD) synced.gold to synced.goldTotal else synced.diamond to synced.diamondTotal
            if (total > 0) return PityStatus(tier, maxOf(0, total - progress))
        }
        val after = if (tier == TrophyTier.GOLD) info?.goldPity ?: 100 else info?.diamondPity ?: 600
        val left = after + 1 - counts.total
        return if (left > 0) PityStatus(tier, left) else null
    }

    val gold = status(TrophyTier.GOLD)
    val diamond = status(TrophyTier.DIAMOND)
    return listOfNotNull(gold, diamond.takeIf { gold == null || showBoth })
}
