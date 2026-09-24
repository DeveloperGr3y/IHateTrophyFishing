package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.trophy.Trophies
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Trophy icons, drawn the way Hypixel draws the items themselves: a paper item with the trophy's model from
 * Hypixel's server resource pack (e.g. hypixel_skyblock:item/fishing/trophy/lava/karate_fish_bronze).
 * If that pack isn't loaded (not on Hypixel, or server resource packs turned off) no icon is shown,
 * instead of Minecraft's missing-texture cube.
 */
object TrophyIcons {
    private val cache = mutableMapOf<String, ItemStack>()
    private val definitelyMissing = Identifier.fromNamespaceAndPath("ihatetrophyfishing", "missing_model_probe")

    fun icon(key: String): ItemStack? {
        val info = Trophies[key] ?: return null
        val model = Identifier.tryParse(info.model) ?: return null
        if (!isLoaded(model)) return null
        return cache.getOrPut(key) { ItemStack(Items.PAPER).apply { set(DataComponents.ITEM_MODEL, model) } }
    }

    /** Minecraft hands back its shared "missing" model for unknown ids, so compare against one we know is missing. */
    private fun isLoaded(model: Identifier): Boolean {
        val models = Minecraft.getInstance().modelManager
        return models.getItemModel(model) !== models.getItemModel(definitelyMissing)
    }
}
