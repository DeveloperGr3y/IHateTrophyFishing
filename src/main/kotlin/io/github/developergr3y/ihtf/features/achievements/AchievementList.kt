package io.github.developergr3y.ihtf.features.achievements

enum class Rarity(val label: String, val colour: String, val rgb: Int) {
    COMMON("Common", "§f", 0xFFFFFFFF.toInt()),
    UNCOMMON("Uncommon", "§a", 0xFF55FF55.toInt()),
    RARE("Rare", "§9", 0xFF5555FF.toInt()),
    EPIC("Epic", "§5", 0xFFAA00AA.toInt()),
    LEGENDARY("Legendary", "§6", 0xFFFFAA00.toInt()),
    MYTHIC("Mythic", "§d", 0xFFFF55FF.toInt()),
    DIVINE("Divine", "§b", 0xFF55FFFF.toInt()),
}

/** [hidden] achievements show as "???" in the list until unlocked. */
class Achievement(val id: String, val name: String, val description: String, val rarity: Rarity, val hidden: Boolean = false)

/**
 * Everything unlocks from things that happen naturally while fishing; nothing needs grinding on purpose.
 * Ids are saved per profile, so never rename one.
 */
object AchievementList {
    val all = listOf(
        // Common
        Achievement("welcome", "Welcome to Hell", "Catch your first trophy with the mod.", Rarity.COMMON),
        Achievement("bronze_age", "Bronze Age", "Catch 10 Bronze trophies.", Rarity.COMMON),
        Achievement("silver_lining", "Silver Lining", "Catch your first Silver.", Rarity.COMMON),
        Achievement("frog_throat", "Frog in the Throat", "Catch your first Trophy Frog.", Rarity.COMMON),
        Achievement("warming_up", "Warming Up", "Fish for 10 minutes.", Rarity.COMMON),
        Achievement("blob_enjoyer", "Blob Enjoyer", "Catch 25 Blobfish.", Rarity.COMMON),
        Achievement("horse_girl", "Horse Girl", "Catch 10 Lavahorses.", Rarity.COMMON),
        Achievement("combo_starter", "Combo Starter", "Reach a streak of 10.", Rarity.COMMON),
        Achievement("double_dip", "Double Dip", "Catch two trophies within 5 seconds.", Rarity.COMMON),
        Achievement("nothing_biting", "Nothing Biting", "Fish for 3 minutes without a trophy.", Rarity.COMMON),
        Achievement("paperwork", "Paperwork", "Sync your trophy fish pity and collection.", Rarity.COMMON),
        Achievement("half_hour", "Half Hour of Lava", "Fish for 30 minutes in one session.", Rarity.COMMON),
        // Uncommon
        Achievement("golden_boy", "Golden Boy", "Catch your first Gold.", Rarity.UNCOMMON),
        Achievement("centurion", "Centurion", "Catch 100 trophies in one session.", Rarity.UNCOMMON),
        Achievement("on_fire", "On Fire", "Reach a streak of 50.", Rarity.UNCOMMON),
        Achievement("patience", "Patience Is a Virtue", "Catch a Slugfish.", Rarity.UNCOMMON),
        Achievement("frequent_flyer", "Frequent Flyer", "Catch 25 Flyfish.", Rarity.UNCOMMON),
        Achievement("raining_frogs", "It's Raining Frogs", "Catch a Wetlands Frog.", Rarity.UNCOMMON),
        Achievement("night_shift", "Night Shift", "Catch a trophy after midnight.", Rarity.UNCOMMON, hidden = true),
        Achievement("two_hours", "Two Hours of My Life", "Fish for 2 hours in one session.", Rarity.UNCOMMON),
        Achievement("hat_trick", "Hat Trick", "Catch 3 Golds in one session.", Rarity.UNCOMMON),
        Achievement("pity_party", "Pity Party", "Get a Gold or Diamond from pity.", Rarity.UNCOMMON),
        Achievement("combo_breaker", "Combo Breaker", "Lose a streak of 25 or more.", Rarity.UNCOMMON),
        Achievement("tourist", "Tourist", "Catch 5 different trophy fish in one session.", Rarity.UNCOMMON),
        // Rare
        Achievement("diamond_rough", "Diamond in the Rough", "Catch your first Diamond.", Rarity.RARE),
        Achievement("unstoppable", "Unstoppable", "Reach a streak of 100.", Rarity.RARE),
        Achievement("thousand_club", "Thousand Club", "Catch 1,000 trophies with the mod.", Rarity.RARE),
        Achievement("golden_hour", "Golden Hour", "Catch a Golden Fish.", Rarity.RARE),
        Achievement("full_house", "Full House", "Own every trophy fish at Bronze.", Rarity.RARE),
        Achievement("frog_collector", "Frog Collector", "Own every Trophy Frog at Bronze.", Rarity.RARE),
        Achievement("clutch", "Clutch", "Save a streak with under a second to spare.", Rarity.RARE),
        Achievement("marathon", "Marathon", "Fish for 4 hours in one session.", Rarity.RARE),
        Achievement("loaded_dice", "Loaded Dice", "Catch 2 Golds within 60 seconds.", Rarity.RARE),
        Achievement("unreadable", "Unreadable", "Catch an Obfuscated 3.", Rarity.RARE),
        // Epic
        Achievement("silver_service", "Silver Service", "Own every trophy fish at Silver.", Rarity.EPIC),
        Achievement("unstoppable_250", "UNSTOPPABLE", "Reach a streak of 250.", Rarity.EPIC),
        Achievement("deja_vu", "Déjà Vu", "Catch a Diamond you already had.", Rarity.EPIC),
        Achievement("ten_thousand", "Ten Thousand", "Catch 10,000 trophies with the mod.", Rarity.EPIC),
        Achievement("heartbreak", "Heartbreak", "Lose a streak of 100 or more.", Rarity.EPIC, hidden = true),
        Achievement("sulphur_sniffer", "Sulphur Sniffer", "Catch 100 Sulphur Skitters.", Rarity.EPIC),
        Achievement("double_diamond", "Double Diamond", "Catch 2 Diamonds in one session.", Rarity.EPIC),
        Achievement("touch_grass", "Please Touch Grass", "Fish for 8 hours in one day.", Rarity.EPIC),
        // Legendary
        Achievement("gold_standard", "Gold Standard", "Own every trophy fish at Gold.", Rarity.LEGENDARY),
        Achievement("all_frogged_up", "All Frogged Up", "Own every Trophy Frog at Gold.", Rarity.LEGENDARY),
        Achievement("streak_legends", "Streak of Legends", "Reach a streak of 500.", Rarity.LEGENDARY),
        Achievement("pity_diamond", "Pity Diamond", "Get a Diamond from pity.", Rarity.LEGENDARY),
        Achievement("lucky_day", "Lucky Day", "Catch 3 Diamonds in one day.", Rarity.LEGENDARY),
        // Mythic
        Achievement("double_rainbow", "Double Rainbow", "Catch 2 Diamonds within 10 minutes.", Rarity.MYTHIC),
        Achievement("why", "Why Are You Like This", "Catch 100,000 trophies with the mod.", Rarity.MYTHIC),
        Achievement("glitch", "Glitch in the Matrix", "Reach a streak of 1,000.", Rarity.MYTHIC, hidden = true),
        // Divine
        Achievement("divine_fish", "I Hate Trophy Fishing", "Own every trophy fish at Diamond.", Rarity.DIVINE),
        Achievement("divine_frog", "I Hate Trophy Frogging Too", "Own every Trophy Frog at Diamond.", Rarity.DIVINE),
    )

    val byId = all.associateBy { it.id }
}
