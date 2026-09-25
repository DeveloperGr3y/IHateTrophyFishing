package io.github.developergr3y.ihtf.odds

import com.google.gson.annotations.Expose
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.util.Location

/** What we know about the things that boost Gold / Diamond odds, per profile. null = not read yet. */
class OddsData {
    /** Charm level on the last fishing rod you held. */
    @Expose var charm: Int? = null

    /** Marigold's Midas Lure perk, as a percentage (0-20). */
    @Expose var midasLure: Double? = null

    /** Gemma's Radiant Fisher perk, as a percentage (0-20). */
    @Expose var radiantFisher: Double? = null

    /** Golden Frog / Diamond Frog attributes (Flipflopper / Seashine shards), as percentages (0-5). Frogs only. */
    @Expose var goldenFrog: Double? = null
    @Expose var diamondFrog: Double? = null

    /** Pets seen in /pets, by name (e.g. "Spinosaurus"). */
    @Expose var pets: MutableMap<String, PetInfo> = mutableMapOf()

    /** Name of the pet you have out; "" = no pet; null = don't know yet. */
    @Expose var activePet: String? = null

    /** Froggles we've seen you wear, so we can suggest them. */
    @Expose var ownedFroggles: MutableSet<String> = mutableSetOf()
}

class PetInfo {
    @Expose var level = 0
    @Expose var heldItem: String? = null

    /** Gold and Diamond boost from the pet's own ability (Spinosaurus: fish, Frog: frogs), in %. */
    @Expose var fishBoost = 0.0
    @Expose var frogBoost = 0.0
}

/** One row of the breakdown. [gold] / [diamond] are percentages (+12 = ×1.12). */
class Boost(
    val name: String,
    val gold: Double,
    val diamond: Double,
    /** Not read yet: what to open to fix it. */
    val syncStep: String? = null,
    /** Only applies in Wormholes. */
    val wormholeOnly: Boolean = false,
    /** Shown when this isn't at its best, e.g. "up to +20% Gold". */
    val tip: String? = null,
) {
    val active get() = syncStep == null && (gold > 0 || diamond > 0)

    /** A short explanation, shown when you hover the row. */
    var info: List<String> = emptyList()
}

class Odds(val gold: Double, val diamond: Double)

/**
 * Gold / Diamond chances for the next trophy, from everything that boosts them.
 *
 * Hypixel rolls Diamond first (0.2%), then Gold (2%), then Silver, then Bronze. Each boost multiplies a tier's
 * roll chance, e.g. Charm VI ×1.12. The Trophy Chance stat is separate: it changes *which* trophy you catch, not
 * its tier, so it isn't counted here.
 */
object TrophyOdds {
    private const val BASE_GOLD = 2.0
    private const val BASE_DIAMOND = 0.2

    val data: OddsData get() = Storage.profile().odds

    private val numerals = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
    fun roman(n: Int) = numerals.getOrElse(n) { n.toString() }

    fun boosts(frog: Boolean): List<Boost> {
        val d = data
        val list = mutableListOf<Boost>()

        list += d.charm.let { charm ->
            if (charm == null) Boost("Charm", 0.0, 0.0, syncStep = "hold your fishing rod")
            else Boost(
                if (charm > 0) "Charm ${roman(charm)}" else "Charm",
                charm * 2.0, charm * 2.0,
                tip = "Charm VI: up to §6+12%§7/§b+12%".takeIf { charm < 6 },
            )
        }
        list.last().info = listOf(
            "§fCharm §7is a fishing rod enchantment.",
            "§7Each level adds §6+2% Gold§7, §b+2% Diamond",
            "§7and +2% Silver chance, up to §f+12% §7at VI.",
            "§8Read from the rod you're holding.",
        )
        list += d.midasLure.let {
            if (it == null) Boost("Midas Lure", 0.0, 0.0, syncStep = "open §6Marigold's§e shop (Dwarven Mines)")
            else Boost(perkName("Midas Lure", it), it, 0.0, tip = "max it for §6+20% Gold".takeIf { _ -> it < 20 })
        }
        list.last().info = listOf(
            "§fMidas Lure §7is a perk from §6Marigold's",
            "§7Gold Essence Shop in the Dwarven Mines.",
            "§7Each tier adds §6+2% Gold §7chance, up to §6+20% §7at X.",
        )
        list += d.radiantFisher.let {
            if (it == null) Boost("Radiant Fisher", 0.0, 0.0, syncStep = "open §6Gemma's§e shop (Crystal Nucleus)")
            else Boost(perkName("Radiant Fisher", it), 0.0, it, tip = "max it for §b+20% Diamond".takeIf { _ -> it < 20 })
        }
        list.last().info = listOf(
            "§fRadiant Fisher §7is a perk from §6Gemma's",
            "§7Diamond Essence Shop in the Crystal Nucleus.",
            "§7Each tier adds §b+2% Diamond §7chance, up to §b+20% §7at X.",
        )

        val petName = if (frog) "Mythic Frog pet" else "Spinosaurus"
        val petInfo = if (frog) {
            listOf(
                "§fThe Mythic Frog pet's §7Home Sweet Home ability",
                "§7adds §6Gold §7and §bDiamond §7chance on Trophy Frogs,",
                "§7up to §f+10% §7at level 100.",
                "§8Read from /pets; your active pet from chat and the tab list.",
            )
        } else {
            listOf(
                "§fThe Spinosaurus pet's §7Pursuit ability adds",
                "§6+0.1% Gold §7and §b+0.1% Diamond §7chance per level",
                "§7on Trophy Fish, up to §f+10% §7at level 100.",
                "§8Read from /pets; your active pet from chat and the tab list.",
            )
        }
        val petItemInfo = listOf(
            "§fPet items §7for trophy fishing (one per pet):",
            "§7 Barrel of Riches: §6+10% Gold",
            "§7 Lotus Crown: §b+10% Diamond",
            "§7They work on whichever pet you have out.",
        )
        val pet = d.activePet?.let { d.pets[it] }
        when {
            d.activePet == null -> list += Boost("Pet", 0.0, 0.0, syncStep = "open §6/pets").also { it.info = petInfo + petItemInfo }
            d.activePet != "" && pet == null ->
                list += Boost("Pet", 0.0, 0.0, syncStep = "open §6/pets§e to read your ${d.activePet}").also { it.info = petInfo + petItemInfo }
            else -> {
                val boost = if (frog) pet?.frogBoost ?: 0.0 else pet?.fishBoost ?: 0.0
                list += Boost(
                    if (boost > 0) "${d.activePet} ${pet?.level}" else petName,
                    boost, boost,
                    tip = "up to §6+10%§7/§b+10%".takeIf { boost < 10 },
                ).also { it.info = petInfo }
                val item = pet?.heldItem
                list += when (item) {
                    "Barrel of Riches" -> Boost(item, 10.0, 0.0)
                    "Lotus Crown" -> Boost(item, 0.0, 10.0)
                    else -> Boost("Pet item", 0.0, 0.0, tip = "Lotus Crown §b+10% Diamond§7 or Barrel of Riches §6+10% Gold")
                }.also { it.info = petItemInfo }
            }
        }

        if (frog) {
            val step = "open your §6Attribute Menu§e and search §6Frog"
            list += d.goldenFrog.let {
                if (it == null) Boost("Golden Frog", 0.0, 0.0, syncStep = step)
                else Boost("Golden Frog", it, 0.0, tip = "Flipflopper shards: up to §6+5% Gold".takeIf { _ -> it < 5 })
            }
            list.last().info = listOf(
                "§fGolden Frog §7is an attribute from §fFlipflopper shards§7.",
                "§7Each level adds §6+0.5% Gold §7chance on Trophy Frogs,",
                "§7up to §6+5% §7at X.",
            )
            list += d.diamondFrog.let {
                if (it == null) Boost("Diamond Frog", 0.0, 0.0, syncStep = step)
                else Boost("Diamond Frog", 0.0, it, tip = "Seashine shards: up to §b+5% Diamond".takeIf { _ -> it < 5 })
            }
            list.last().info = listOf(
                "§fDiamond Frog §7is an attribute from §fSeashine shards§7.",
                "§7Each level adds §b+0.5% Diamond §7chance on Trophy Frogs,",
                "§7up to §b+5% §7at X.",
            )
        }

        list += when (OddsImport.froggles) {
            "Diamond Froggles" -> Boost("Diamond Froggles", 10.0, 5.0, wormholeOnly = true)
            "Golden Froggles" -> Boost("Golden Froggles", 5.0, 0.0, wormholeOnly = true, tip = "Diamond Froggles: §6+10%§7/§b+5%")
            else -> Boost("Froggles", 0.0, 0.0, wormholeOnly = true, tip = "Diamond Froggles: §6+10%§7/§b+5%§7 in Wormholes")
        }
        list.last().info = listOf(
            "§fFroggles §7are helmets that only boost you in Wormholes:",
            "§7 Golden Froggles: §6+5% Gold",
            "§7 Diamond Froggles: §6+10% Gold§7, §b+5% Diamond",
            "§8Read from the helmet you're wearing.",
        )
        return list
    }

    private const val TIP_AFTER_MS = 2 * 60_000L
    private const val TIP_MIN_GAIN = 5.0

    /**
     * One suggestion to swap to something you already own (never to buy something), shown only after you've fished
     * with the same setup for a couple of minutes, and only when it's worth at least +5%. Null if there's nothing.
     */
    fun swapTip(frog: Boolean): String? {
        if (System.currentTimeMillis() - OddsImport.setupSince < TIP_AFTER_MS || Tracker.isAfk) return null
        val d = data
        val active = d.activePet ?: return null
        fun boostOf(pet: PetInfo?) = (if (frog) pet?.frogBoost else pet?.fishBoost) ?: 0.0

        val current = boostOf(d.pets[active])
        d.pets.entries.filter { it.key != active }.maxByOrNull { boostOf(it.value) }?.let { (name, pet) ->
            val gain = boostOf(pet) - current
            if (gain >= TIP_MIN_GAIN) return "your $name adds §6+${fmt(gain)}%§7/§b+${fmt(gain)}%§7 here"
        }

        if (OddsImport.froggles == null && Location.onTrophyIsland) {
            when {
                "Diamond Froggles" in d.ownedFroggles -> return "your Diamond Froggles add §6+10%§7/§b+5%§7 in Wormholes"
                "Golden Froggles" in d.ownedFroggles -> return "your Golden Froggles add §6+5% Gold§7 in Wormholes"
            }
        }
        return null
    }

    fun fmt(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    private fun perkName(name: String, percent: Double): String {
        val tier = (percent / 2).toInt()
        return if (tier > 0) "$name ${roman(tier)}" else name
    }

    /** Chance (in %) that the next trophy is Gold / Diamond. */
    fun odds(boosts: List<Boost>, inWormhole: Boolean): Odds {
        val counted = boosts.filter { it.active && (inWormhole || !it.wormholeOnly) }
        val diamondRoll = BASE_DIAMOND * counted.fold(1.0) { acc, b -> acc * (1 + b.diamond / 100) }
        val goldRoll = BASE_GOLD * counted.fold(1.0) { acc, b -> acc * (1 + b.gold / 100) }
        // Diamond is rolled first, so Gold only happens when Diamond didn't.
        return Odds(gold = goldRoll * (1 - diamondRoll / 100), diamond = diamondRoll)
    }
}
