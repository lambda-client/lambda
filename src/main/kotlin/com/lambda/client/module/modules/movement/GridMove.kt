package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.LagNotifier
import com.lambda.commons.interfaces.DisplayEnum
import com.lambda.event.listener.listener
import net.minecraft.util.MovementInputFromOptions
import net.minecraftforge.client.event.InputUpdateEvent

object GridMove : Module(
    name = "GridMove",
    category = Category.MOVEMENT,
    description = "Moves in a grid. Useful for stash hunting"
) {
    private val disableOnDisconnect by setting("Disable On Disconnect", true)
    enum class GridMoveMode(override val displayName: String) : DisplayEnum {
        FORWARD("Forward"),
        BACKWARD("Backward")
    }
    var foo = GridMoveMode.FORWARD
    init {
        listener<ConnectionEvent.Disconnect> {
            if (disableOnDisconnect) disable()
        }
        listener<InputUpdateEvent>(6969) {
            if (LagNotifier.paused && LagNotifier.pauseAutoWalk) return@listener

            if (it.movementInput !is MovementInputFromOptions) return@listener
            when (foo) {
                GridMoveMode.FORWARD -> {
                    it.movementInput.moveForward = 1.0f
                }
                GridMoveMode.BACKWARD -> {
                    it.movementInput.moveForward = -1.0f
                }
            }
        }
    }
}