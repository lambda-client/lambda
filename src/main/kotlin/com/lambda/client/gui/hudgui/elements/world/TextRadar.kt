package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.util.AsyncCachedValue
import com.lambda.client.util.color.ColorGradient
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.color.DyeColors
import com.lambda.client.util.threads.runSafeR
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.MobEffects

internal object TextRadar : LabelHud(
    name = "TextRadar",
    category = Category.WORLD,
    description = "List of players nearby"
) {

    private val health by setting("Health", true)
    private val ping by setting("Ping", false)
    private val combatPotion by setting("Combat Potion", true)
    private val distance by setting("Distance", true)
    private val friend by setting("Friend", true)
    private val maxEntries by setting("Max Entries", 8, 4..32, 1)
    private val range by setting("Range", 64, 16..256, 2)

    private val healthColorGradient = ColorGradient(
        0.0f to ColorHolder(180, 20, 20),
        10.0f to ColorHolder(240, 220, 20),
        20.0f to ColorHolder(20, 232, 20),
        30.0f to ColorHolder(125, 20, 230)
    )

    private val pingColorGradient = ColorGradient(
        0f to ColorHolder(101, 101, 101),
        0.1f to ColorHolder(20, 232, 20),
        20f to ColorHolder(20, 232, 20),
        150f to ColorHolder(20, 232, 20),
        300f to ColorHolder(150, 0, 0)
    )

    private val cacheList by AsyncCachedValue(50L) {
        runSafeR {
            val list = world.playerEntities.toList().asSequence()
                .filter { it != null && !it.isDead && it.health > 0.0f }
                .filter { it != player && it != mc.renderViewEntity }
                .filter { friend || !FriendManager.isFriend(it.name) }
                .map { it to player.getDistance(it) }
                .filter { it.second <= range }
                .sortedBy { it.second }
                .toList()

            remainingEntries = list.size - maxEntries
            list.take(maxEntries)
        } ?: emptyList()
    }
    private var remainingEntries = 0

    override fun SafeClientEvent.updateText() {
        cacheList.forEach {
            addHealth(it.first)
            addName(it.first)
            addPing(it.first)
            addPotion(it.first)
            addDist(it.second)
            displayText.currentLine++
        }
        if (remainingEntries > 0) {
            displayText.addLine("...and $remainingEntries more")
        }
    }

    private fun addHealth(player: EntityPlayer) {
        if (health) {
            val hp = MathUtils.round(player.health + player.absorptionAmount, 1).toString()
            displayText.add(hp, healthColorGradient.get(player.health + player.absorptionAmount))
        }
    }

    private fun addName(player: EntityPlayer) {
        val color = if (FriendManager.isFriend(player.name)) DyeColors.GREEN.color else primaryColor
        displayText.add(player.name, color)
    }

    private fun SafeClientEvent.addPing(player: EntityPlayer) {
        if (ping) {
            val ping = connection.getPlayerInfo(player.name)?.responseTime ?: 0
            val color = pingColorGradient.get(ping.toFloat())
            displayText.add("${ping}ms", color)
        }
    }

    private fun addPotion(player: EntityPlayer) {
        if (combatPotion) {
            if (player.isPotionActive(MobEffects.WEAKNESS)) displayText.add("W", secondaryColor)
            if (player.isPotionActive(MobEffects.STRENGTH)) displayText.add("S", secondaryColor)
        }
    }

    private fun addDist(distIn: Float) {
        if (distance) {
            val dist = MathUtils.round(distIn, 1)
            displayText.add(dist.toString(), secondaryColor)
        }
    }
}