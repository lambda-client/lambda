package com.lambda.client.module.modules.movement

import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent


/**
 * @author Doogie13
 * @since 20/09/2022
 */
object Step : Module(
    name = "Step",
    category = Category.MOVEMENT,
    description = "Allows you to step up blocks",
    modulePriority = 201
) {

    private val mode by setting("Mode", Mode.NCP, description = "Anticheat step bypass")
    val strict by setting("Strict", false, description = "Bypass the new UpdatedNCP step checks")
    val upStep = setting("Step Height", 2.5f, 1f..2.5f, .5f, { !strict }, description = "How high to step")

    private enum class Mode {
        NCP, VANILLA
    }

    private var playerY = 0.0
    private var timing = false

    init {
        upStep.valueListeners.add { _, _ ->
            BaritoneUtils.settings?.assumeStep?.value = isEnabled
        }

        onDisable {
            resetTimer()
            runSafe {
                player.stepHeight = .6f
            }
        }

        safeListener<ClientTickEvent> {
            if (!timing) resetTimer()

            timing = false
        }
    }

    fun pre(bb: AxisAlignedBB, player: EntityPlayerSP): Boolean {
        player.ridingEntity?.let {
            it.stepHeight = if (strict) 1f else upStep.value
        }

        playerY = bb.minY

        return player.isInWater
            || player.isInLava
            || !player.onGround
            || player.isOnLadder
            || player.movementInput.jump
            || player.fallDistance >= 0.1
    }

    fun post(bb: AxisAlignedBB, mc: Minecraft) {
        if (mode == Mode.VANILLA) return

        val height = bb.minY - playerY

        if (height < .6)
            return

        val player = mc.player
        val connection = mc.connection ?: return

        val values = ArrayList<Double>()

        when {
            height > 2.019 -> values.addAll(listOf(.425, .821, .699, .599, 1.022, 1.372, 1.652, 1.869, 2.019, 1.919))
            height > 1.5 -> values.addAll(listOf(.42, .78, .63, .51, .9, 1.21, 1.45, 1.43))
            height > 1.015 -> values.addAll(listOf(.42, .7532, 1.01, 1.093, 1.015))
            height > .6 -> values.addAll(listOf(.42 * height, .7532 * height))
        }

        if (strict && height > .6) values.add(height)

        values.forEach {
            connection.sendPacket(CPacketPlayer.Position(player.posX, player.posY + it, player.posZ, false))
        }

        modifyTimer(50f * values.size)
        timing = true
    }
}