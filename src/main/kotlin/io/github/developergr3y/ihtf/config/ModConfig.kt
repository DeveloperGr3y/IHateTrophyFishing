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
import io.github.developergr3y.ihtf.util.Sounds
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
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

    // Categories appear in this order, so GUI is first (like SkyHanni).
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
}

class GuiCategory {
    @Transient
    @JvmField
    @ConfigOption(
        name = "Edit GUI Locations",
        desc = "Opens an editor showing every display: drag one to move it, scroll over it to resize. Also: §e/ihtf gui",
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

// Each category holds one collapsible section (@Accordion) per feature, so new features slot in as new sections.

class TrackersCategory {
    @Expose
    @JvmField
    @ConfigOption(name = "Trophy Tracker", desc = "Trophy fish and trophy frogs: catches per hour, pity counts and time until pity.")
    @Accordion
    var trophyFish = TrackerConfig()

    @Expose
    @JvmField
    @ConfigOption(name = "Currently Targeting", desc = "Lists every trophy fish or frog caught in the last few minutes.")
    @Accordion
    var currentlyTargeting = CurrentlyTargetingConfig()
}

class HelpersCategory {
    @Expose
    @JvmField
    @ConfigOption(name = "Slugfish Timer", desc = "Tells you when a bite is late enough to count for a Slugfish.")
    @Accordion
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
    @ConfigOption(name = "View", desc = "Which catches the tracker counts: this session, today, or all time.")
    @ConfigEditorDropdown
    var view = TrackerView.SESSION

    @Expose
    @JvmField
    @ConfigOption(
        name = "Where",
        desc = "Only show the tracker on the Crimson Isle and Lotus Atoll, or anywhere. If Hypixel's tab list has no " +
            "Area line (tab widgets turned off), it counts as a trophy island.",
    )
    @ConfigEditorDropdown
    var where = ShowWhere.TROPHY_ISLANDS

    @Expose
    @JvmField
    @ConfigOption(
        name = "Show When",
        desc = "Show the tracker only while you're fishing or holding a rod, or always. " +
            "It always shows with your inventory open, so its buttons can be reached.",
    )
    @ConfigEditorDropdown
    var showWhen = ShowWhen.FISHING_OR_ROD

    @Expose
    @JvmField
    @ConfigOption(
        name = "Hide Owned",
        desc = "Hide trophies you've already caught a Gold (or Diamond) of, e.g. to only see what's left while going " +
            "for Golds. Totals still count everything. Uses your all-time counts, so open Odger's menu once first.",
    )
    @ConfigEditorDropdown
    var hideOwned = HideOwned.OFF

    @Expose
    @JvmField
    @ConfigOption(name = "Show Diamond Pity", desc = "Also show Diamond pity for fish that are still missing Gold. Otherwise only the next pity is shown.")
    @ConfigEditorBoolean
    var showBothPities = false

    @Expose
    @JvmField
    @ConfigOption(
        name = "Show Icons",
        desc = "Show each trophy's icon (needs Hypixel's server resource pack, which is on by default).",
    )
    @ConfigEditorBoolean
    var showIcons = true

    @Expose
    @JvmField
    @ConfigOption(
        name = "Count Water Fishing",
        desc = "Count time with your bobber in water as active time too (needed for Trophy Frogs). " +
            "Turn off if you water fish elsewhere and don't want that time in your rates.",
    )
    @ConfigEditorBoolean
    var countWaterFishing = true

    @Expose
    @JvmField
    @ConfigOption(
        name = "AFK Timeout",
        desc = "Seconds without your bobber in lava (or water) before the tracker's clock pauses. Paused time doesn't count towards catches per hour.",
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
        desc = "Seconds between updates of the displays' numbers. Catches, view changes and resets still show straight away.",
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
        desc = "Show every trophy caught in the last few minutes, so you can see which ones you're getting while stacking conditions.",
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
        desc = "Only show this display on the Crimson Isle and Lotus Atoll, or anywhere. If Hypixel's tab list has no " +
            "Area line (tab widgets turned off), it counts as a trophy island.",
    )
    @ConfigEditorDropdown
    var where = ShowWhere.TROPHY_ISLANDS

    @Expose
    @JvmField
    @ConfigOption(
        name = "Show When",
        desc = "Show this display only while you're fishing or holding a rod, or always. " +
            "It always shows with your inventory open, so its buttons can be reached.",
    )
    @ConfigEditorDropdown
    var showWhen = ShowWhen.FISHING_OR_ROD

    @Expose
    @JvmField
    @ConfigOption(
        name = "Hide Owned",
        desc = "Hide trophies you've already caught a Gold (or Diamond) of. Uses your all-time counts, so open Odger's menu once first.",
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
        desc = "After you cast, count up to the Slugfish time. A bite after that plays a ding and shows REEL!. " +
            "Bites before it are ignored because they can't be a Slugfish.",
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
        desc = "The sound played when a bite counts, e.g. §eblock.note_block.bell§7 or §eentity.experience_orb.pickup§7. " +
            "Use §eList of Sounds§7 to find and listen to one.",
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
    @ConfigOption(name = "List of Sounds", desc = "Opens a website in your browser where you can play every Minecraft sound and copy its name.")
    @ConfigEditorButton(buttonText = "Open")
    val listOfSounds = Runnable { Sounds.openSoundList() }
}
