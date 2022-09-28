package com.lambda.client.module.modules.movement

import baritone.api.pathing.goals.GoalXZ
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.BaritoneCommandEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.LagNotifier
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.math.Direction
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.MovementInputFromOptions
import net.minecraftforge.client.event.InputUpdateEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object AutoWalk : Module(
    name = "AutoWalk",
    description = "Automatically walks somewhere",
    category = Category.MOVEMENT
) {
    private val mode = setting("Direction", AutoWalkMode.BARITONE)
    private val disableOnDisconnect by setting("Disable On Disconnect", true)
    private var pauseOnBackwards by setting("Pause on opposite movement", true)

    private enum class AutoWalkMode(override val displayName: String) : DisplayEnum {
        FORWARD("Forward"),
        BACKWARD("Backward"),
        BARITONE("Baritone")
    }

    val baritoneWalk get() = isEnabled && mode.value == AutoWalkMode.BARITONE

    private const val border = 30000000
    private val messageTimer = TickTimer(TimeUnit.SECONDS)
    var direction = Direction.NORTH; private set

    override fun isActive(): Boolean {
        return isEnabled && (mode.value != AutoWalkMode.BARITONE || BaritoneUtils.isActive || BaritoneUtils.isPathing)
    }

    override fun getHudInfo(): String {
        return if (mode.value == AutoWalkMode.BARITONE && (BaritoneUtils.isActive || BaritoneUtils.isPathing)) {
            direction.displayName
        } else {
            mode.value.displayName
        }
    }

    init {
        onDisable {
            if (mode.value == AutoWalkMode.BARITONE) BaritoneUtils.cancelEverything()
        }

        listener<BaritoneCommandEvent> {
            if (it.command.contains("cancel")) {
                disable()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            if (disableOnDisconnect) disable()
        }

        listener<InputUpdateEvent>(6969) {
            if (LagNotifier.isBaritonePaused && LagNotifier.pauseAutoWalk) return@listener

            if (it.movementInput !is MovementInputFromOptions) return@listener

            it.movementInput.moveForward = when (mode.value) {
                AutoWalkMode.FORWARD -> {
                    if (pauseOnBackwards && mc.gameSettings.keyBindBack.isKeyDown) 0.0f else 1.0f
                }
                AutoWalkMode.BACKWARD -> {
                    if (pauseOnBackwards && mc.gameSettings.keyBindForward.isKeyDown) 0.0f else -1.0f
                }
                else -> {
                    0.0f
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (mode.value == AutoWalkMode.BARITONE
                && !checkBaritoneElytra()
                && !isActive()
            ) {
                startPathing()
            }
        }
    }

    private fun SafeClientEvent.startPathing() {
        if (!world.isChunkGeneratedAt(player.chunkCoordX, player.chunkCoordZ)) return

        direction = Direction.fromEntity(player)
        val x = player.posX.floorToInt() + direction.directionVec.x * border
        val z = player.posZ.floorToInt() + direction.directionVec.z * border

        BaritoneUtils.cancelEverything()
        BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalXZ(x, z))
    }

    private fun checkBaritoneElytra() = mc.player?.let {
        if (it.isElytraFlying && messageTimer.tick(10L)) {
            MessageSendHelper.sendErrorMessage("$chatName Baritone mode isn't currently compatible with Elytra flying!" +
                " Choose a different mode if you want to use AutoWalk while Elytra flying")
        }
        it.isElytraFlying
    } ?: true

    init {
        mode.listeners.add {
            if (isDisabled || mc.player == null) return@add
            if (mode.value == AutoWalkMode.BARITONE) {
                if (!checkBaritoneElytra()) {
                    runSafe { startPathing() }
                }
            } else {
                BaritoneUtils.cancelEverything()
            }
        }
    }
}