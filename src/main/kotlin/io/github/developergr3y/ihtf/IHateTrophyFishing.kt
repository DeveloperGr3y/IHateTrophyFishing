package io.github.developergr3y.ihtf

import io.github.developergr3y.ihtf.config.ModConfig
import io.github.developergr3y.ihtf.data.Period
import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.features.Roulette
import io.github.developergr3y.ihtf.features.SlugfishTimer
import io.github.developergr3y.ihtf.features.achievements.Achievements
import io.github.developergr3y.ihtf.hud.AchievementToast
import io.github.developergr3y.ihtf.hud.AchievementsScreen
import io.github.developergr3y.ihtf.features.Streak
import io.github.developergr3y.ihtf.hud.HudEditScreen
import io.github.developergr3y.ihtf.hud.HudManager
import io.github.developergr3y.ihtf.hud.RouletteOverlay
import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.trophy.ChatListener
import io.github.developergr3y.ihtf.trophy.MenuImport
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.util.Compat
import io.github.developergr3y.ihtf.util.Location
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import java.io.File

object IHateTrophyFishing : ClientModInitializer {
    const val MOD_ID = "ihatetrophyfishing"

    val logger = LoggerFactory.getLogger("IHateTrophyFishing")
    val configDir = File(FabricLoader.getInstance().configDir.toFile(), MOD_ID)
    val version: String = FabricLoader.getInstance().getModContainer(MOD_ID)
        .map { it.metadata.version.friendlyString }.orElse("dev")

    private lateinit var managedConfig: ManagedConfig<ModConfig>
    val config: ModConfig get() = managedConfig.instance

    /** Opening a screen straight from a chat command gets undone when the chat closes, so wait a tick. */
    private var nextTick: (() -> Unit)? = null

    override fun onInitializeClient() {
        configDir.mkdirs()
        managedConfig = ManagedConfig.create(File(configDir, "config.json"), ModConfig::class.java) {
            checkExpose = false
        }
        Storage.load()
        if (config.trackers.trophyFish.newSessionOnLaunch) {
            Storage.root.profiles.values.forEach { it.session = Period() }
        }

        ChatListener.register()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            nextTick?.let {
                nextTick = null
                it()
            }
            Location.tick(client)
            Tracker.tick(client)
            Streak.tick(client)
            Roulette.tick()
            Achievements.tick(client)
            SlugfishTimer.tick(client)
            MenuImport.tick()
            Storage.tick()
        }

        HudManager.register()

        // MoulConfig only saves when its editor hears the screen closed, and openConfigGui() never passes that on,
        // so changes made in /ihtf were lost on restart. Save whenever our settings screen closes instead.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is MoulConfigScreenComponent) ScreenEvents.remove(screen).register { saveConfig() }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("slugfish"), SlugfishTimer::render)
        HudElementRegistry.addLast(id("roulette"), RouletteOverlay::render) // on top of everything
        HudElementRegistry.addLast(id("achievement_toast"), AchievementToast::render)

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> Storage.save() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { Storage.save() }

        registerCommands()
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            for (name in listOf("ihtf", "ihatetrophyfishing")) {
                dispatcher.register(
                    ClientCommands.literal(name)
                        .executes {
                            nextTick = { managedConfig.openConfigGui() }
                            1
                        }
                        .then(ClientCommands.literal("gui").executes {
                            openHudEditor()
                            1
                        })
                        .then(
                            ClientCommands.literal("roulette")
                                .executes {
                                    Roulette.test(TrophyTier.DIAMOND)
                                    1
                                }
                                .then(ClientCommands.literal("diamond").executes {
                                    Roulette.test(TrophyTier.DIAMOND)
                                    1
                                })
                                .then(ClientCommands.literal("gold").executes {
                                    Roulette.test(TrophyTier.GOLD)
                                    1
                                }),
                        )
                        .then(ClientCommands.literal("achievements").executes {
                            openAchievements()
                            1
                        })
                        .then(ClientCommands.literal("reset").executes {
                            Tracker.resetSession()
                            chat("Session reset.")
                            1
                        }),
                )
            }
        }
    }

    fun openAchievements() {
        nextTick = { Compat.setScreen(AchievementsScreen()) }
    }

    fun openHudEditor() {
        nextTick = { Compat.setScreen(HudEditScreen()) }
    }

    fun saveConfig() {
        managedConfig.saveToFile()
        HudManager.refreshAll()
    }

    fun chat(message: String) {
        Compat.chat.addClientSystemMessage(Component.literal("§6[IHTF] §r$message"))
    }

    private fun id(path: String) = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
