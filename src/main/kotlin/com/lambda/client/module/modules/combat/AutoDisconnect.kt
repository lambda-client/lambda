package com.lambda.client.module.modules.combat

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.mc.LambdaGuiDisconnected
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.combat.AutoDisconnect.Reasons.*
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.combat.CombatUtils.scaledHealth
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.countItem
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.entity.monster.EntityCreeper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Items
import net.minecraft.init.SoundEvents
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.time.LocalTime

object AutoDisconnect : Module(
    name = "AutoDisconnect",
    description = "Automatically disconnects when in danger or on low health",
    category = Category.COMBAT,
    alwaysListening = true
) {
    private val disableMode by setting("Disable Mode", DisableMode.ALWAYS)
    private val health by setting("Health", true)
    private val crystals by setting("Crystals", false)
    private val healthAmount by setting("Min Health", 10, 6..36, 1, { health || crystals }, description = "Applies to both the Health and Crystals option")
    private val creeper by setting("Creepers", true)
    private val creeperDistance by setting("Creeper Distance", 5, 1..10, 1, { creeper })
    private val totem by setting("Totem", false)
    private val minTotems by setting("Min Totems", 2, 1..10, 1, { totem })
    private val players by setting("Players", false)
    private val playerDistance by setting("Player Distance", 64, 32..128, 4, { players })
    private val friends by setting("Friends", false, { players })

    @Suppress("UNUSED")
    private enum class DisableMode {
        NEVER, ALWAYS, NOT_PLAYER
    }

    init {
        safeListener<TickEvent.ClientTickEvent>(-1000) {
            if (isDisabled || it.phase != TickEvent.Phase.END) return@safeListener

            when {
                health && player.scaledHealth < healthAmount -> log(HEALTH, num = player.scaledHealth)
                totem && checkTotems() -> log(TOTEM)
                crystals && checkCrystals() -> log(END_CRYSTAL)
                creeper && checkCreeper() -> {
                    /* checkCreeper() does log() */
                }
                players && checkPlayers() -> {
                    /* checkPlayer() does log() */
                }
            }
        }
    }

    private fun SafeClientEvent.checkTotems(): Boolean {
        val slots = player.allSlots
        return slots.any { it.hasStack }
            && slots.countItem(Items.TOTEM_OF_UNDYING) < minTotems
    }

    private fun SafeClientEvent.checkCrystals(): Boolean {
        val maxSelfDamage = CombatManager.crystalMap.values.maxOfOrNull { it.selfDamage } ?: 0.0f
        return player.scaledHealth - maxSelfDamage < healthAmount
    }

    private fun SafeClientEvent.checkCreeper(): Boolean {
        for (entity in world.loadedEntityList) {
            if (entity !is EntityCreeper) continue
            if (player.getDistance(entity) > creeperDistance) continue
            log(CREEPER, MathUtils.round(entity.getDistance(player), 2).toString())
            return true
        }
        return false
    }

    private fun SafeClientEvent.checkPlayers(): Boolean {
        for (entity in world.loadedEntityList) {
            if (entity !is EntityPlayer) continue
            if (entity.isFakeOrSelf) continue
            if (player.getDistance(entity) > playerDistance) continue
            if (!friends && FriendManager.isFriend(entity.name)) continue
            log(PLAYER, entity.name)
            return true
        }
        return false
    }

    private fun SafeClientEvent.log(reason: Reasons, additionalInfo: String = "", num: Float = 0.0f) {
        val reasonText = getReason(reason, additionalInfo, num)
        val screen = getScreen() // do this before disconnecting

        mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
        connection.networkManager.closeChannel(TextComponentString(""))
        mc.loadWorld(null as WorldClient?)

        mc.displayGuiScreen(LambdaGuiDisconnected(reasonText, screen, disableMode == DisableMode.ALWAYS || (disableMode == DisableMode.NOT_PLAYER && reason != PLAYER), LocalTime.now()))
    }

    private fun getScreen() = if (mc.isIntegratedServerRunning) {
        GuiMainMenu()
    } else {
        GuiMultiplayer(GuiMainMenu())
    }

    private fun getReason(reason: Reasons, additionalInfo: String, num: Float) = when (reason) {
        HEALTH -> arrayOf("Health went below ${healthAmount}!")
        TOTEM -> arrayOf("Less then ${totemMessage(minTotems)}!")
        CREEPER -> arrayOf("Creeper came near you!", "It was $additionalInfo blocks away")
        PLAYER -> arrayOf("Player $additionalInfo came within $playerDistance blocks range!")
        END_CRYSTAL -> arrayOf("An end crystal was placed too close to you!",
            "It would have done more than ${MathUtils.round(num - healthAmount, 1)} damage!"
        )
    }

    private enum class Reasons {
        HEALTH, TOTEM, CREEPER, PLAYER, END_CRYSTAL
    }

    private fun totemMessage(amount: Int) = if (amount == 1) "one totem" else "$amount totems"
}