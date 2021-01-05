package me.zeroeightsix.kami.gui.hudgui.elements.combat

import me.zeroeightsix.kami.gui.hudgui.LabelHud
import me.zeroeightsix.kami.manager.managers.FriendManager
import me.zeroeightsix.kami.module.modules.combat.AntiBot
import me.zeroeightsix.kami.setting.GuiConfig.setting
import me.zeroeightsix.kami.util.text.format
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.MobEffects
import net.minecraft.util.text.TextFormatting
import org.kamiblue.commons.utils.MathUtils

object TextRadar : LabelHud(
    name = "TextRadar",
    category = Category.COMBAT,
    description = "View player names and health in text form"
) {
    private val showHeightDifference = setting("ShowHeightDifference", true)
    private val showCombatPotion = setting("ShowCombatPotion", false)
    private val showDistance = setting("ShowDistance", true)

    override val minWidth = 10f
    override val minHeight = 10f

    override fun updateText() {
        for (player in mc.world.playerEntities.sorted()) {
            if (player.isDead || player.name == mc.player.name || AntiBot.botSet.contains(player)) continue

            displayText.addLine(
                player.heightDifference()
                    + player.formattedHealth()
                    + player.formattedName()
                    + player.formattedPotions()
                    + player.formattedDistance()
            )
        }
    }

    private fun EntityPlayer.heightDifference() = if (showHeightDifference.value) {
        when {
            this.posY > mc.player.posY -> {
                TextFormatting.DARK_GREEN format "+ "
            }
            this.posY < mc.player.posY -> {
                TextFormatting.DARK_RED format "- "
            }
            else -> {
                "  "
            }
        }
    } else {
        ""
    }

    private fun EntityPlayer.formattedDistance() = if (showDistance.value) {
        " " + (TextFormatting.DARK_GRAY format MathUtils.round(mc.player.getDistance(this), 1))
    } else {
        ""
    }

    private fun EntityPlayer.formattedName() = if (FriendManager.isFriend(this.name)) {
        TextFormatting.GREEN format this.name
    } else {
        TextFormatting.GRAY format this.name
    }

    private fun EntityPlayer.formattedPotions(): String = if (showCombatPotion.value) {
        var potion = ""

        if (this.isPotionActive(MobEffects.WEAKNESS)) {
            potion += TextFormatting.DARK_GRAY format "W"
        }

        if (this.isPotionActive(MobEffects.STRENGTH)) {
            potion += TextFormatting.DARK_PURPLE format "S"
        }

        if (potion.isNotEmpty()) {
            " $potion "
        } else {
            potion
        }
    } else {
        ""
    }

    private fun EntityPlayer.formattedHealth(): String {
        val health = MathUtils.round(this.health + this.absorptionAmount, 1)
        return healthColor(health) format "$health "
    }

    private fun healthColor(health: Double) = when {
        health >= 20 -> TextFormatting.GREEN
        health >= 10 -> TextFormatting.YELLOW
        health >= 5 -> TextFormatting.GOLD
        else -> TextFormatting.RED
    }

    private fun List<EntityPlayer>.sorted(): List<EntityPlayer> {
        return this.sortedBy { it.getDistance(mc.player) }
    }
}