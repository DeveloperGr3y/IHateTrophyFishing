package io.github.developergr3y.ihtf.odds

import com.google.gson.annotations.Expose
import io.github.developergr3y.ihtf.data.Storage

/** What we know about the things that boost Gold / Diamond odds, per profile. null = not read yet. */
class OddsData {
    /** Charm level on the last fishing rod you held. */
    @Expose var charm: Int? = null

    /** Marigold's Midas Lure perk, as a percentage (0-20). */
    @Expose var midasLure: Double? = null

    /** Gemma's Radiant Fisher perk, as a percentage (0-20). */
    @Expose var radiantFisher: Double? = null

    /** Hunting Box attributes (Flipflopper / Seashine shards), as percentages (0-5). Frogs only. */
    @Expose var goldenFrog: Double? = null
    @Expose var diamondFrog: Double? = null

    /** Pets seen in /pets, by name (e.g. "Spinosaurus"). */
    @Expose var pets: MutableMap<String, PetInfo> = mutableMapOf()

    /** Name of the pet you have out; "" = no pet; null = don't know yet. */
    @Expose var activePet: String? = null
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
        list += d.midasLure.let {
            if (it == null) Boost("Midas Lure", 0.0, 0.0, syncStep = "open §6Marigold's§e shop (Dwarven Mines)")
            else Boost(perkName("Midas Lure", it), it, 0.0, tip = "max it for §6+20% Gold".takeIf { _ -> it < 20 })
        }
        list += d.radiantFisher.let {
            if (it == null) Boost("Radiant Fisher", 0.0, 0.0, syncStep = "open §6Gemma's§e shop (Crystal Nucleus)")
            else Boost(perkName("Radiant Fisher", it), 0.0, it, tip = "max it for §b+20% Diamond".takeIf { _ -> it < 20 })
        }

        val petName = if (frog) "Frog pet" else "Spinosaurus"
        val pet = d.activePet?.let { d.pets[it] }
        when {
            d.activePet == null -> list += Boost("Pet", 0.0, 0.0, syncStep = "open §6/pets")
            d.activePet != "" && pet == null -> list += Boost("Pet", 0.0, 0.0, syncStep = "open §6/pets§e to read your ${d.activePet}")
            else -> {
                val boost = if (frog) pet?.frogBoost ?: 0.0 else pet?.fishBoost ?: 0.0
                list += Boost(
                    if (boost > 0) "${d.activePet} ${pet?.level}" else petName,
                    boost, boost,
                    tip = "$petName: up to §6+10%§7/§b+10%".takeIf { boost < 10 },
                )
                val item = pet?.heldItem
                list += when (item) {
                    "Barrel of Riches" -> Boost(item, 10.0, 0.0)
                    "Lotus Crown" -> Boost(item, 0.0, 10.0)
                    else -> Boost("Pet item", 0.0, 0.0, tip = "Lotus Crown §b+10% Diamond§7 or Barrel of Riches §6+10% Gold")
                }
            }
        }

        if (frog) {
            val step = "open the §6Hunting Box"
            list += d.goldenFrog.let {
                if (it == null) Boost("Golden Frog", 0.0, 0.0, syncStep = step)
                else Boost("Golden Frog", it, 0.0, tip = "Flipflopper shards: up to §6+5% Gold".takeIf { _ -> it < 5 })
            }
            list += d.diamondFrog.let {
                if (it == null) Boost("Diamond Frog", 0.0, 0.0, syncStep = step)
                else Boost("Diamond Frog", 0.0, it, tip = "Seashine shards: up to §b+5% Diamond".takeIf { _ -> it < 5 })
            }
        }

        list += when (OddsImport.froggles) {
            "Diamond Froggles" -> Boost("Diamond Froggles", 10.0, 5.0, wormholeOnly = true)
            "Golden Froggles" -> Boost("Golden Froggles", 5.0, 0.0, wormholeOnly = true, tip = "Diamond Froggles: §6+10%§7/§b+5%")
            else -> Boost("Froggles", 0.0, 0.0, wormholeOnly = true, tip = "Diamond Froggles: §6+10%§7/§b+5%§7 in Wormholes")
        }
        return list
    }

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
