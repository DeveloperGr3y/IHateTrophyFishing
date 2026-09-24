package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.util.Location
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Items

enum class ShowWhere(private val label: String) {
    TROPHY_ISLANDS("Trophy islands only"),
    ANYWHERE("Anywhere"),
    ;

    override fun toString() = label
}

enum class ShowWhen(private val label: String) {
    FISHING_OR_ROD("Fishing or rod in hand"),
    ALWAYS("Always"),
    ;

    override fun toString() = label
}

/** Shared "should this display show right now?" rules for the Where / Show When settings. */
object Visibility {
    fun allowed(where: ShowWhere, showWhen: ShowWhen, inInventory: Boolean): Boolean {
        if (where == ShowWhere.TROPHY_ISLANDS && !Location.onTrophyIsland) return false
        // With your inventory open it always shows, so the tracker's buttons can be reached.
        if (inInventory || showWhen == ShowWhen.ALWAYS) return true
        return !Tracker.isAfk || holdingRod()
    }

    /** Lava and water rods are both vanilla fishing rods underneath, so this covers fish and frogs. */
    private fun holdingRod() = Minecraft.getInstance().player?.mainHandItem?.`is`(Items.FISHING_ROD) == true
}
