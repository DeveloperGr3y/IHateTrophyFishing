package io.github.developergr3y.ihtf.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.developergr3y.ihtf.IHateTrophyFishing

/** Mod Menu's "configure" button (clicking us in its mod list) opens the same settings as /ihtf. */
object ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory { parent -> IHateTrophyFishing.configScreen(parent) }
}
