
package com.minato.task.tasks

import com.minato.context.Automated
import com.minato.event.events.InventoryEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.task.Task
import com.minato.threading.runSafeAutomated
import com.minato.util.TickTimer
import com.minato.util.player.RotationUtils.lookAtBlock
import com.minato.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.screen.ScreenHandler
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class OpenContainerTask @Ta5kBuilder constructor(
    private val blockPos: BlockPos,
    private val automated: Automated,
    private val waitForSlotLoad: Boolean = true,
    private val sides: Set<Direction> = Direction.entries.toSet()
) : Task<ScreenHandler>(), Automated by automated {
    override val name get() = "${containerState.description()} at ${blockPos.toShortString()}"

    private var screenHandler: ScreenHandler? = null
    private var containerState = State.Scoping

    private val retryTimer = TickTimer()

    enum class State {
        Pathing, Scoping, Opening, SlotLoading;

        fun description() = when (this) {
            Pathing -> "Pathing closer"
            Scoping -> "Waiting for scope"
            Opening -> "Opening container"
            SlotLoading -> "Waiting for slots to load"
        }
    }

    init {
        listen<InventoryEvent.Open> {
            if (containerState != State.Opening) return@listen

            screenHandler = it.screenHandler
            containerState = State.SlotLoading

            if (!waitForSlotLoad) success(it.screenHandler)
        }

        listen<InventoryEvent.Close> {
            if (screenHandler != it.screenHandler) return@listen

            containerState = State.Scoping
            screenHandler = null
        }

        listen<InventoryEvent.FullUpdate> {
            if (containerState != State.SlotLoading) return@listen

            screenHandler?.let {
                success(it)
            }
        }

        listen<TickEvent.Pre> {
            if (containerState == State.Opening) {
                retryTimer.tick()
                if (retryTimer.hasSurpassed(10)) {
                    retryTimer.reset()
                    containerState = State.Scoping
                }
                return@listen
            }

            if (containerState != State.Scoping && containerState != State.Pathing) return@listen

            val checkedHit = runSafeAutomated { lookAtBlock(blockPos, sides) }
                ?: run {
                    containerState = State.Pathing
                    return@listen
                }
            if (interactConfig.rotate && !rotationRequest { rotation(checkedHit.rotation) }.submit().done) return@listen

            interaction.interactBlock(player, Hand.MAIN_HAND, checkedHit.hit.blockResult ?: return@listen)
            player.swingHand(Hand.MAIN_HAND)

            containerState = State.Opening
        }
    }
}
