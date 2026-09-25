package io.github.developergr3y.ihtf.trophy

import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.data.ProfileData
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.odds.TrophyOdds

/**
 * The two things to open in-game so the mod knows your trophy data, spelled out per kind:
 *  - pity progress comes from /pity (Crimson Isle section for fish, Lotus Atoll for frogs);
 *  - which tiers you own comes from Odger's Trophy Fish menu (fish) or Researcher Ribery (frogs).
 */
object SyncStatus {
    private fun kind(frog: Boolean) = if (frog) "frogs" else "fish"

    fun pityStep(frog: Boolean) = if (frog) "§6/pity §e→ §6Lotus Atoll" else "§6/pity §e→ §6Crimson Isle"

    fun ownedStep(frog: Boolean) = if (frog) "talk to §6Researcher Ribery" else "talk to §6Odger §e(Trophy Fish)"

    /** One short line per missing step, for the displays. Empty when everything is synced. */
    fun hints(profile: ProfileData, frog: Boolean) = buildList {
        if (!profile.pitySynced(frog)) add("§eSync ${kind(frog)} pity: ${pityStep(frog)}")
        if (!profile.collectionSynced(frog)) add("§eSync owned ${kind(frog)}: ${ownedStep(frog)}")
    }

    /** /ihtf sync: a checklist in chat. */
    fun report() {
        val profile = Storage.profile()
        IHateTrophyFishing.chat("§eTrophy data sync:")
        for (frog in listOf(false, true)) {
            line(profile.pitySynced(frog), "${kind(frog).replaceFirstChar { it.uppercase() }} pity", pityStep(frog))
            line(profile.collectionSynced(frog), "${kind(frog).replaceFirstChar { it.uppercase() }} owned", ownedStep(frog))
        }
        // Trophy Odds boosts (fish and frog lists overlap, so merge them).
        val boosts = (TrophyOdds.boosts(frog = false) + TrophyOdds.boosts(frog = true)).distinctBy { it.name }
        for (boost in boosts.filter { it.syncStep != null }) line(false, "Trophy Odds: ${boost.name}", boost.syncStep!!)
        if (boosts.none { it.syncStep != null }) line(true, "Trophy Odds boosts", "")
    }

    private fun line(done: Boolean, what: String, step: String) {
        IHateTrophyFishing.chat(if (done) " §a✔ §f$what" else " §c✖ §f$what §7— $step")
    }
}
