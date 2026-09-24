package io.github.developergr3y.ihtf.data

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import io.github.developergr3y.ihtf.IHateTrophyFishing
import io.github.developergr3y.ihtf.trophy.FishCounts
import io.github.developergr3y.ihtf.trophy.PityProgress
import net.minecraft.client.Minecraft
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/** Catches and fishing time within one tracking period (a session, a day, or all time). */
class Period {
    @Expose var fish: MutableMap<String, FishCounts> = mutableMapOf()
    @Expose var activeMillis: Long = 0

    val totalCatches get() = fish.values.sumOf { it.total }
}

class ProfileData {
    /**
     * All-time counts per trophy, including catches from before this mod (imported from Odger's / Researcher
     * Ribery's menus). Used to know which tiers you already have, and as a pity estimate before /pity is synced.
     */
    @Expose var lifetime: MutableMap<String, FishCounts> = mutableMapOf()

    /** Pity progress per trophy, read from Hypixel's /pity menu and kept up to date from catches. */
    @Expose var pity: MutableMap<String, PityProgress> = mutableMapOf()

    /** Fish id -> display name as Hypixel writes it. */
    @Expose var names: MutableMap<String, String> = mutableMapOf()

    /** Fish id -> item rarity colour code (e.g. "§5" for Epic), as seen in Hypixel's chat message or Odger's menu. */
    @Expose var colours: MutableMap<String, String> = mutableMapOf()

    @Expose var session = Period()
    @Expose var days: MutableMap<String, Period> = mutableMapOf()
    @Expose var total = Period()

    fun today(): Period = days.getOrPut(LocalDate.now().toString()) { Period() }

    /** Every period a new catch or fishing time should be added to. */
    fun activePeriods() = listOf(session, today(), total)
}

class StorageRoot {
    /** Key: "<player uuid>:<SkyBlock profile name>". */
    @Expose var profiles: MutableMap<String, ProfileData> = mutableMapOf()

    /** Player uuid -> last SkyBlock profile seen, so data goes to the right place before Hypixel announces it. */
    @Expose var lastProfile: MutableMap<String, String> = mutableMapOf()
}

object Storage {
    private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create()
    private val file = File(IHateTrophyFishing.configDir, "data.json")
    private const val SAVE_INTERVAL_MS = 30_000L

    var root = StorageRoot()
        private set
    private var dirty = false
    private var lastSave = 0L

    fun load() {
        if (!file.exists()) return
        try {
            root = file.reader().use { gson.fromJson(it, StorageRoot::class.java) } ?: StorageRoot()
        } catch (e: Exception) {
            IHateTrophyFishing.logger.error("Could not read ${file.path}; keeping a backup and starting fresh", e)
            file.copyTo(File(file.parentFile, "data.broken-${System.currentTimeMillis()}.json"), overwrite = true)
            root = StorageRoot()
        }
    }

    fun markDirty() {
        dirty = true
    }

    /** Called every tick; writes to disk at most every 30 seconds. */
    fun tick() {
        val now = System.currentTimeMillis()
        if (dirty && now - lastSave > SAVE_INTERVAL_MS) save()
    }

    fun save() {
        lastSave = System.currentTimeMillis()
        dirty = false
        try {
            file.parentFile.mkdirs()
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(gson.toJson(root))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            IHateTrophyFishing.logger.error("Could not save ${file.path}", e)
        }
    }

    private fun playerId(): String = Minecraft.getInstance().player?.uuid?.toString() ?: "unknown"

    fun setProfile(profileName: String) {
        root.lastProfile[playerId()] = profileName
        markDirty()
    }

    val profileName: String get() = root.lastProfile[playerId()] ?: "default"

    fun profile(): ProfileData = root.profiles.getOrPut("${playerId()}:$profileName") { ProfileData() }
}
