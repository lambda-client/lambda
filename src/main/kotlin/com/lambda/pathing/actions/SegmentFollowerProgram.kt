package com.lambda.pathing.actions

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs

class LaunchTrigger(private val delayFrames: Int) {
    private var groundedTicks = 0
    private var fired = false

    val hasFired: Boolean get() = fired

    fun press(observed: MovementSimulationState): Boolean {
        if (fired || !observed.onGround) return false
        if (groundedTicks++ < delayFrames) return false
        fired = true
        return true
    }
}

private const val EASE_TURN_DEGREES = 50.0

internal class SegmentFollowerProgram(
    nodes: List<HorizontalPoint>,
    private val sprint: Boolean,
    private val lookAheadNodes: Int,
    private val launch: LaunchTrigger?,
    private val maxYawChange: Double,
    private val easeTurns: Boolean = false,

    /**
     * Whether forward stays pressed once the body leaves the ground. Releasing it is the
     * only way a jump lands slower than it took off (see [LaunchSolution.holdForward]).
     */
    private val holdForwardInFlight: Boolean = true,

    /**
     * Air ticks to keep forward pressed after the launch before releasing it; the solved
     * schedule that sets landing distance and exit speed independently ([LaunchSolution.holdTicks]).
     */
    private val holdTicks: Int = Int.MAX_VALUE,

    /**
     * When present, the air phase is flown closed-loop: each airborne tick the drift
     * landing is predicted and the key press that best moves it onto the plan's aim
     * point replaces the scheduled input. Null flies the historical open schedule.
     */
    private val airPlan: AirSteering.AirPlan? = null,
) : ControlProgram {
    private var airborneTicks = 0
    private val pursuit = PursuitTracker(nodes)

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        pursuit.advance(observed)
        val jump = launch?.press(observed) == true

        val desiredYaw = yawTowards(pursuit.target(lookAheadNodes), observed)
        val yawError = Rotation.wrap(desiredYaw - observed.rotation.yaw)
        val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)

        // From the launch tick onward, a released jump keeps its hands off the throttle:
        // the ballistic model prices the launch tick's ground acceleration too, so this
        // has to start on the same tick the jump is pressed, not the one after.
        val flying = jump || (launch?.hasFired == true && !observed.onGround)
        if (flying) airborneTicks++ else airborneTicks = 0
        val coasting = flying && (!holdForwardInFlight || airborneTicks > holdTicks)

        // The launch tick itself moves on ground acceleration; correction begins on the
        // first true air tick. airborneTicks counts the launch tick as 1 and the arc's
        // air-loop tick k as k+1, whose displacement is still ahead -- so the ticks of
        // landing authority left, this one included, are airTicks - airborneTicks + 2.
        val steered = if (flying && !observed.onGround && airPlan != null) {
            AirSteering.steer(
                positionX = observed.position.x,
                positionZ = observed.position.z,
                velocityX = observed.velocity.x,
                velocityZ = observed.velocity.z,
                yawDegrees = observed.rotation.yaw + yawDelta,
                aimX = airPlan.aimX,
                aimZ = airPlan.aimZ,
                unitX = airPlan.unitX,
                unitZ = airPlan.unitZ,
                remainingTicks = airPlan.airTicks - airborneTicks + 2,
                plannedHoldTicks = when {
                    !holdForwardInFlight -> 0
                    else -> (airPlan.holdTicks - airborneTicks + 1).coerceAtLeast(0)
                },
                sprintAcceleration = airPlan.sprintAcceleration,
                walkAcceleration = airPlan.walkAcceleration,
            )
        } else null

        return MovementSimulationInput(
            forward = when {
                steered != null -> steered.forward
                coasting -> 0.0
                easeTurns && abs(yawError) > EASE_TURN_DEGREES -> 0.0
                else -> 1.0
            },
            strafe = steered?.strafe ?: 0.0,
            sprint = sprint,
            jump = jump,
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }

}
