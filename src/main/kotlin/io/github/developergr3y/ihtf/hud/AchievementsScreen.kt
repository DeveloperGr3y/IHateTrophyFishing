package io.github.developergr3y.ihtf.hud

import io.github.developergr3y.ihtf.data.Storage
import io.github.developergr3y.ihtf.features.achievements.AchievementList
import io.github.developergr3y.ihtf.features.achievements.Achievements
import io.github.developergr3y.ihtf.features.achievements.Rarity
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** /ihtf achievements: every achievement, grouped by rarity, scrollable. Hidden ones show as ??? until unlocked. */
class AchievementsScreen : Screen(Component.literal("IHateTrophyFishing achievements")) {
    private val rowHeight = 22
    private val headerHeight = 16
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
    private var scroll = 0

    private sealed interface Row
    private data class Header(val rarity: Rarity) : Row
    private data class Item(val index: Int) : Row

    private val rows: List<Row> = buildList {
        for (rarity in Rarity.entries) {
            val items = AchievementList.all.withIndex().filter { it.value.rarity == rarity }
            if (items.isEmpty()) continue
            add(Header(rarity))
            items.forEach { add(Item(it.index)) }
        }
    }

    private val listTop = 36
    private fun listBottom() = height - 12
    private fun contentHeight() = rows.sumOf { if (it is Header) headerHeight else rowHeight }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val unlocked = Storage.profile().achievements
        val total = AchievementList.all.size

        graphics.centeredText(font, "§e§lAchievements  §f${Achievements.unlockedCount()} §7/ §f$total", width / 2, 12, -1)
        graphics.centeredText(font, "§8Scroll to see more", width / 2, 23, -1)

        val panelWidth = minOf(width - 32, 360)
        val left = (width - panelWidth) / 2
        graphics.fill(left - 4, listTop - 4, left + panelWidth + 4, listBottom() + 2, 0xC0101014.toInt())
        graphics.enableScissor(left - 4, listTop - 2, left + panelWidth + 4, listBottom())

        var y = listTop - scroll
        for (row in rows) {
            when (row) {
                is Header -> {
                    val count = AchievementList.all.count { it.rarity == row.rarity }
                    val got = AchievementList.all.count { it.rarity == row.rarity && it.id in unlocked }
                    graphics.text(font, "${row.rarity.colour}§l${row.rarity.label.uppercase()} §8$got / $count", left, y + 4, -1, true)
                    y += headerHeight
                }
                is Item -> {
                    val achievement = AchievementList.all[row.index]
                    val at = unlocked[achievement.id]
                    val has = at != null
                    if (has) graphics.fill(left, y, left + panelWidth, y + rowHeight - 2, 0x30FFFFFF)
                    val mark = if (has) "§a✔" else "§8✖"
                    val name = when {
                        has -> "${achievement.rarity.colour}${achievement.name}"
                        achievement.hidden -> "§8???"
                        else -> "§7${achievement.name}"
                    }
                    val description = if (!has && achievement.hidden) "§8Hidden achievement" else "§7${achievement.description}"
                    graphics.text(font, "$mark $name", left + 4, y + 2, -1, true)
                    graphics.text(font, description, left + 16, y + 11, -1, false)
                    if (at != null) {
                        val date = dateFormat.format(Instant.ofEpochMilli(at))
                        graphics.text(font, "§8$date", left + panelWidth - font.width(date) - 4, y + 2, -1, false)
                    }
                    y += rowHeight
                }
            }
        }
        graphics.disableScissor()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val max = maxOf(0, contentHeight() - (listBottom() - listTop))
        scroll = (scroll - (scrollY * 20).toInt()).coerceIn(0, max)
        return true
    }

    override fun isPauseScreen() = false
}
