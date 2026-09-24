package io.github.developergr3y.ihtf.config

import com.google.gson.annotations.Expose
import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.features.SlugfishTimer
import io.github.developergr3y.ihtf.hud.HudManager
import io.github.developergr3y.ihtf.hud.HudPosition
import io.github.developergr3y.ihtf.hud.ShowWhen
import io.github.developergr3y.ihtf.hud.ShowWhere
import io.github.developergr3y.ihtf.tracker.Tracker
import io.github.developergr3y.ihtf.tracker.TrackerView
import io.github.developergr3y.ihtf.trophy.HideOwned
import io.github.developergr3y.ihtf.trophy.TrophyTier
import io.github.developergr3y.ihtf.util.Sounds
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

/**
 * Everything shown in /ihtf. MoulConfig builds the screen from these annotations:
 * @Category = a tab on the left, @ConfigOption + @ConfigEditor* = one setting.
 * Fields need @Expose to be saved to config/ihatetrophyfishing/config.json.
 */
class ModConfig : Config() {
    override fun getTitle(): StructuredText =
        StructuredText.of("IHateTrophyFishing ${IHateTrophyFishing.version} §7(because someone has to)")

    override fun saveNow() = IHateTrophyFishing.saveConfig()

    // Categories appear in this order, so GUI is first.
    @Expose
    @JvmField
    @Category(name = "GUI", desc = "Move and resize all on-screen displays (§e/ihtf gui§7).")
    var gui = GuiCategory()

    @Expose
    @JvmField
    @Category(name = "Trackers", desc = "On-screen trackers: trophy fish and frogs per hour, and time until pity.")
    var trackers = TrackersCategory()

    @Expose
    @JvmField
    @Category(name = "Fishing Helpers", desc = "Timers and alerts that help you catch specific trophy fish.")
    var helpers = HelpersCategory()

    @Expose
    @JvmField
    @Category(name = "Dopamine Enhancers", desc = "Silly extras to make trophy fishing a bit less soul-destroying.")
    var dopamine = DopamineCategory()
}

class GuiCategory {
    @Transient
    @JvmField
    @ConfigOption(
        name = "Edit GUI Locations",
        desc = "Drag displays to move them, scroll to resize. Also: §e/ihtf gui",
    )
    @ConfigEditorButton(buttonText = "Edit")
    val edit = Runnable { IHateTrophyFishing.openHudEditor() }

    @Transient
    @JvmField
    @ConfigOption(name = "Reset GUI Locations", desc = "Put every display back to its default position and size.")
    @ConfigEditorButton(buttonText = "Reset")
    val reset = Runnable {
        HudManager.resetPositions()
        IHateTrophyFishing.chat("Display positions reset.")
    }
}

// Each feature is a sub-category (listed under its parent on the left), so new features slot in easily.

class TrackersCategory {
    @Expose
    @JvmField
    @Category(name = "Trophy Tracker", desc = "Catches per hour, pity counts and time until pity.")
    var trophyFish = TrackerConfig()

    @Expose
    @JvmField
    @Category(name = "Currently Targeting", desc = "Every trophy caught in the last few minutes.")
    var currentlyTargeting = CurrentlyTargetingConfig()

    @Expose
    @JvmField
    @Category(name = "Missing Trophies", desc = "Which trophies you still need, one tier at a time.")
    var missing = MissingConfig()
}

class MissingConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Show the trophies you haven't caught yet for one tier.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Tier", desc = "Which tier to show. With your inventory open, click the tiers to change it.")
    @ConfigEditorDropdown
    var tier = TrophyTier.GOLD

    @Expose
    @JvmField
    @ConfigOption(name = "Show Hints", desc = "Show where or how to catch each one.")
    @ConfigEditorBoolean
    var showHints = true

    @Expose
    @JvmField
    @ConfigOption(name = "Show Pity", desc = "For Gold and Diamond, show catches left until pity.")
    @ConfigEditorBoolean
    var showPity = true

    @Expose
    @JvmField
    @ConfigOption(name = "Where", desc = "Only on the Crimson Isle and Lotus Atoll, or anywhere.")
    @ConfigEditorDropdown
    var where = ShowWhere.TROPHY_ISLANDS

    @Expose
    @JvmField
    @ConfigOption(name = "Show When", desc = "Only while fishing or holding a rod, or always. Shows with your inventory open.")
    @ConfigEditorDropdown
    var showWhen = ShowWhen.FISHING_OR_ROD

    // Not shown as an option; set in GUI > Edit GUI Locations.
    @Expose @JvmField var position = HudPosition(x = 5, y = 160)
}

class DopamineCategory {
    @Expose
    @JvmField
    @ConfigOption(
        name = "Enable Dopamine Enhancers",
        desc = "Master switch for everything in this tab. Off keeps the mod strictly business.",
    )
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @Category(name = "Streak", desc = "osu!-style counter for trophies caught in a row.")
    var streak = StreakConfig()

    @Expose
    @JvmField
    @Category(name = "Roulette", desc = "A slot-machine reel that always lands on your Gold or Diamond.")
    var roulette = RouletteConfig()

    @Expose
    @JvmField
    @Category(name = "Achievements", desc = "52 achievements that unlock as you fish, from Common to Divine.")
    var achievements = AchievementsConfig()
}

class AchievementsConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Unlock achievements as you fish. See them with §e/ihtf achievements§7.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Pop-ups", desc = "Show a banner at the top of the screen when you unlock one.")
    @ConfigEditorBoolean
    var popups = true

    @Expose
    @JvmField
    @ConfigOption(name = "Chat Message", desc = "Post unlocks in your chat with buttons to share them.")
    @ConfigEditorBoolean
    var chat = true

    @Expose
    @JvmField
    @ConfigOption(name = "Sounds", desc = "Play a sound on unlock (grander for rarer ones).")
    @ConfigEditorBoolean
    var sounds = true

    @Transient
    @JvmField
    @ConfigOption(name = "View Achievements", desc = "Open the list of achievements. Also: §e/ihtf achievements")
    @ConfigEditorButton(buttonText = "Open")
    val view = Runnable { IHateTrophyFishing.openAchievements() }
}

enum class RouletteMode(private val label: String) {
    EVERY("Every one"),
    FIRST_ONLY("First ones only"),
    OFF("Off"),
    ;

    override fun toString() = label
}

class RouletteConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Diamonds", desc = "Spin on Diamond catches. First only skips duplicates. Test: §e/ihtf roulette")
    @ConfigEditorDropdown
    var diamonds = RouletteMode.EVERY

    @Expose
    @JvmField
    @ConfigOption(name = "Golds", desc = "A shorter spin on Gold catches. Test: §e/ihtf roulette gold")
    @ConfigEditorDropdown
    var golds = RouletteMode.EVERY

    @Expose
    @JvmField
    @ConfigOption(name = "Sounds", desc = "Reel ticks and the jackpot sound.")
    @ConfigEditorBoolean
    var sounds = true
}

class StreakConfig {
    @Expose
    @JvmField
    @ConfigOption(
        name = "Enabled",
        desc = "Trophies in a row (one every 15s keeps it alive). 10+ posts a shareable chat message.",
    )
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Sounds", desc = "Hit sounds, milestone jingles and a combo-break sound.")
    @ConfigEditorBoolean
    var sounds = true

    // Not shown as an option; set in GUI > Edit GUI Locations.
    // Centred, below the crosshair and below where the Slugfish timer's REEL! shows.
    @Expose @JvmField var position = HudPosition(y = 45, centred = true)
}

class HelpersCategory {
    @Expose
    @JvmField
    @Category(name = "Slugfish Timer", desc = "Alerts when a bite is late enough to count for a Slugfish.")
    var slugfish = SlugfishConfig()
}

class TrackerConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Show the trophy tracker on screen.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "View", desc = "Count this session, today, or all time.")
    @ConfigEditorDropdown
    var view = TrackerView.SESSION

    @Expose
    @JvmField
    @ConfigOption(
        name = "Where",
        desc = "Only on the Crimson Isle and Lotus Atoll, or anywhere.",
    )
    @ConfigEditorDropdown
    var where = ShowWhere.TROPHY_ISLANDS

    @Expose
    @JvmField
    @ConfigOption(
        name = "Show When",
        desc = "Only while fishing or holding a rod, or always. Shows with your inventory open.",
    )
    @ConfigEditorDropdown
    var showWhen = ShowWhen.FISHING_OR_ROD

    @Expose
    @JvmField
    @ConfigOption(
        name = "Hide Owned",
        desc = "Hide trophies you already have a Gold (or Diamond) of. Totals still count them.",
    )
    @ConfigEditorDropdown
    var hideOwned = HideOwned.OFF

    @Expose
    @JvmField
    @ConfigOption(name = "Show Diamond Pity", desc = "Also show Diamond pity for fish still missing a Gold.")
    @ConfigEditorBoolean
    var showBothPities = false

    @Expose
    @JvmField
    @ConfigOption(
        name = "Show Icons",
        desc = "Show each trophy's icon (needs Hypixel's resource pack).",
    )
    @ConfigEditorBoolean
    var showIcons = true

    @Expose
    @JvmField
    @ConfigOption(
        name = "Count Water Fishing",
        desc = "Count time with your bobber in water too (needed for Trophy Frogs).",
    )
    @ConfigEditorBoolean
    var countWaterFishing = true

    @Expose
    @JvmField
    @ConfigOption(
        name = "AFK Timeout",
        desc = "Seconds without fishing before the clock pauses. Paused time isn't in your rates.",
    )
    @ConfigEditorSlider(minValue = 10f, maxValue = 300f, minStep = 5f)
    var afkTimeoutSeconds = 60

    @Expose
    @JvmField
    @ConfigOption(name = "New Session On Launch", desc = "Start a fresh session every time you start the game.")
    @ConfigEditorBoolean
    var newSessionOnLaunch = true

    @Transient
    @JvmField
    @ConfigOption(name = "Reset Session", desc = "Clear this session's catches and time. Also: §e/ihtf reset")
    @ConfigEditorButton(buttonText = "Reset")
    val resetSession = Runnable {
        Tracker.resetSession()
        IHateTrophyFishing.chat("Session reset.")
    }

    @Expose
    @JvmField
    @ConfigOption(
        name = "Refresh Interval",
        desc = "Seconds between number updates. Catches and clicks show straight away.",
    )
    @ConfigEditorSlider(minValue = 1f, maxValue = 30f, minStep = 1f)
    var refreshSeconds = 10

    // Not shown as an option; set in GUI > Edit GUI Locations.
    @Expose @JvmField var position = HudPosition(x = 5, y = 80)
}

class CurrentlyTargetingConfig {
    @Expose
    @JvmField
    @ConfigOption(
        name = "Enabled",
        desc = "Show every trophy caught in the last few minutes.",
    )
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Time Window", desc = "How many minutes back to look.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 60f, minStep = 1f)
    var windowMinutes = 10

    @Expose
    @JvmField
    @ConfigOption(
        name = "Where",
        desc = "Only on the Crimson Isle and Lotus Atoll, or anywhere.",
    )
    @ConfigEditorDropdown
    var where = ShowWhere.TROPHY_ISLANDS

    @Expose
    @JvmField
    @ConfigOption(
        name = "Show When",
        desc = "Only while fishing or holding a rod, or always. Shows with your inventory open.",
    )
    @ConfigEditorDropdown
    var showWhen = ShowWhen.FISHING_OR_ROD

    @Expose
    @JvmField
    @ConfigOption(
        name = "Hide Owned",
        desc = "Hide trophies you already have a Gold (or Diamond) of.",
    )
    @ConfigEditorDropdown
    var hideOwned = HideOwned.OFF

    // Not shown as an option; set in GUI > Edit GUI Locations.
    @Expose @JvmField var position = HudPosition(x = 5, y = 5)
}

class SlugfishConfig {
    @Expose
    @JvmField
    @ConfigOption(
        name = "Enabled",
        desc = "Counts up from each cast; a bite after the timer dings and shows REEL!",
    )
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Seconds", desc = "10 with a Slug pet, 20 without.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 60f, minStep = 1f)
    var seconds = 10

    @Expose
    @JvmField
    @ConfigOption(
        name = "Alert Sound",
        desc = "Sound name, e.g. §eblock.note_block.bell§7. Find one with §eList of Sounds§7.",
    )
    @ConfigEditorText
    var sound = "block.note_block.bell"

    @Expose
    @JvmField
    @ConfigOption(name = "Pitch", desc = "Higher is a brighter, higher-pitched sound. 1.0 is normal.")
    @ConfigEditorSlider(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
    var pitch = 1.6f

    @Transient
    @JvmField
    @ConfigOption(name = "Test Sound", desc = "Play the alert (all 3 hits) so you can check it and your volume.")
    @ConfigEditorButton(buttonText = "Test")
    val testSound = Runnable { SlugfishTimer.queueDing() }

    @Transient
    @JvmField
    @ConfigOption(name = "List of Sounds", desc = "Opens a site where you can play every sound and copy its name.")
    @ConfigEditorButton(buttonText = "Open")
    val listOfSounds = Runnable { Sounds.openSoundList() }
}
