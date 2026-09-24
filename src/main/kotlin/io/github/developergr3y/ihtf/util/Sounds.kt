package io.github.developergr3y.ihtf.util

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Util

object Sounds {
    /** Every sound, playable in the browser (the same list SkyHanni links to). */
    private const val SOUND_LIST_URL = "https://misode.github.io/sounds/"

    /**
     * Plays a sound by its name, e.g. "block.note_block.bell" or "entity.experience_orb.pickup",
     * at full volume regardless of where you're looking. Returns false if the name isn't a valid sound id.
     */
    fun play(name: String, pitch: Float, volume: Float = 1f): Boolean {
        val id = Identifier.tryParse(name.trim()) ?: return false
        // Sounds only defined in a resource pack aren't in the registry, but can still be played by id.
        val sound = BuiltInRegistries.SOUND_EVENT.getValue(id) ?: SoundEvent.createVariableRangeEvent(id)
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
        return true
    }

    fun openSoundList() = Util.getPlatform().openUri(SOUND_LIST_URL)
}
