package com.lambda.pathing.launch

import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile

enum class LaunchMode(val sprint: Boolean, val jumps: Boolean) {
    SPRINT_JUMP(sprint = true, jumps = true),
    WALK_JUMP(sprint = false, jumps = true),
    SPRINT_DROP(sprint = true, jumps = false),
    WALK_DROP(sprint = false, jumps = false),
    ;

    val drops: Boolean get() = !jumps

    fun supports(rise: Double): Boolean = if (jumps) rise <= MAX_JUMP_RISE else rise < 0.0

    companion object {

        const val MAX_JUMP_RISE = 1
    }
}

class ArcSample(
    val airTicks: Int,
    val distance: Double,
    val heights: DoubleArray,

    val distances: DoubleArray,

    val exitSpeed: Double,
) {
    val apex: Double get() = heights.maxOrNull() ?: 0.0
}

data class BallisticProfile(
    val gravity: Double,
    val jumpVelocity: Double,

    val walkGroundAcceleration: Double,
    val sprintGroundAcceleration: Double,
    val slipperiness: Double,
) {
    private val groundFriction = HORIZONTAL_DRAG * slipperiness

    fun groundAcceleration(sprint: Boolean): Double =
        if (sprint) sprintGroundAcceleration else walkGroundAcceleration

    fun cruiseSpeed(sprint: Boolean): Double {
        val acceleration = groundAcceleration(sprint)
        return groundFriction * acceleration / (1.0 - groundFriction)
    }

    fun runUpSpeed(entrySpeed: Double, ticks: Int, sprint: Boolean): Double {
        var velocity = entrySpeed
        repeat(ticks) { velocity = (velocity + groundAcceleration(sprint)) * groundFriction }
        return velocity
    }

    /**
     * Speeds from which [target] can be reached within [ticks] of standing on the ground.
     *
     * The piece a one-gap lookahead was missing. Asking whether a launch's exit speed
     * lands *inside* the next gap's entry window is the wrong question when the body gets
     * to stand on the pad in between: measured on a corpus course, a jump exits at 0.2431
     * into a gap wanting 0.0917-0.1702, which reads as hopeless, while one tick of
     * coasting takes it to 0.1327 and straight into the window. The right question is
     * whether the window is *reachable*, and this answers it.
     *
     * Coasting only sheds speed and holding forward only builds it, so the reachable set
     * after `k` ticks is an interval that widens with `k`; taking the widest admits any
     * entry the body could arrange. Deliberately generous -- this exists to stop good
     * launches being discarded, and the rollout still has to certify whatever survives.
     */
    fun groundReachable(
        target: ClosedFloatingPointRange<Double>,
        ticks: Int,
        sprint: Boolean,
    ): ClosedFloatingPointRange<Double> {
        require(ticks >= 0) { "a ground phase cannot be negative: $ticks" }
        // Arriving faster than the window is fine as long as friction can shed the excess
        // in the ticks available; each one multiplies the speed by the ground friction.
        var high = target.endInclusive
        repeat(ticks) { high /= groundFriction }
        // Arriving slower is fine as long as forward input can build the difference back.
        val low = if (runUpSpeed(0.0, ticks, sprint) >= target.start) 0.0 else target.start
        return low..high.coerceAtMost(momentumSpeed(sprint))
    }

    fun runUpDistanceFor(targetSpeed: Double, sprint: Boolean, maxTicks: Int = 24): Double? {
        var velocity = 0.0
        var covered = 0.0
        repeat(maxTicks) {
            val step = velocity + groundAcceleration(sprint)
            covered += step
            velocity = step * groundFriction
            if (velocity >= targetSpeed) return covered
        }
        return null
    }

    fun groundRunUpTicks(
        entrySpeed: Double,
        distance: Double,
        sprint: Boolean,
        maxTicks: Int,
    ): Int {
        if (distance <= 0.0) return 0
        val acceleration = groundAcceleration(sprint)
        var velocity = entrySpeed
        var covered = 0.0
        for (tick in 0 until maxTicks) {
            val step = velocity + acceleration
            covered += step
            velocity = step * groundFriction
            if (covered >= distance) return tick + 1
        }
        return maxTicks
    }

    /**
     * Fly an arc, optionally letting go of forward partway through it.
     *
     * [holdTicks] is the missing control dimension. Held for the whole flight, an arc's
     * landing distance and its exit speed rise together -- they are one number wearing two
     * hats, and a chain of gaps that needs to land far *and* slowly has nowhere to go.
     * Releasing partway decouples them, because acceleration applied early is dragged for
     * the rest of the flight while acceleration applied late is not. Measured on a flat
     * sprint jump entered at 0.11: holding throughout lands at 3.42 moving 0.251, holding
     * the first eight ticks lands at 3.18 moving 0.170, and holding the first three lands
     * at 2.51 moving 0.104 -- roughly a twofold spread in exit speed at a given distance.
     *
     * The default holds throughout, which is what every caller did before this existed.
     */
    fun fly(
        mode: LaunchMode,
        entrySpeed: Double,
        rise: Double,
        holdForward: Boolean = true,
        maxTicks: Int = MAX_ARC_TICKS,
        holdTicks: Int = Int.MAX_VALUE,
    ): ArcSample? {
        var velocity = entrySpeed
        var verticalVelocity = 0.0
        var height = 0.0
        var distance = 0.0
        val heights = ArrayList<Double>(maxTicks)
        val distances = ArrayList<Double>(maxTicks)

        if (mode.jumps) {
            verticalVelocity = jumpVelocity
            if (mode.sprint) velocity += SPRINT_JUMP_BOOST
        }

        if (holdForward && holdTicks > 0) velocity += groundAcceleration(mode.sprint)
        distance += velocity
        height += verticalVelocity
        verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
        velocity *= groundFriction
        heights += height
        distances += distance

        val airAcceleration = when {
            !holdForward -> 0.0
            mode.sprint -> SPRINT_AIR_ACCELERATION
            else -> WALK_AIR_ACCELERATION
        }
        for (tick in 1..maxTicks) {
            if (tick < holdTicks) velocity += airAcceleration
            distance += velocity

            if (verticalVelocity < 0.0) {

                if (height < rise) return null

                if (height + verticalVelocity <= rise) {
                    heights += rise
                    distances += distance
                    return ArcSample(
                        airTicks = tick,
                        distance = distance,
                        heights = heights.toDoubleArray(),
                        distances = distances.toDoubleArray(),
                        exitSpeed = velocity * HORIZONTAL_DRAG,
                    )
                }
            }

            height += verticalVelocity
            verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
            velocity *= HORIZONTAL_DRAG
            heights += height
            distances += distance
        }
        return null
    }

    fun bounce(
        entrySpeed: Double,
        drop: Int,
        rise: Int,
        holdForward: Boolean = false,
        sprint: Boolean = false,
        bounceFactor: Double = 1.0,
        maxTicks: Int = MAX_BOUNCE_TICKS,
    ): ArcSample? {
        require(drop > 0) { "a bounce must fall onto something: drop=$drop" }

        var velocity = entrySpeed
        var verticalVelocity = 0.0
        var height = 0.0
        var distance = 0.0
        val heights = ArrayList<Double>(maxTicks)
        val distances = ArrayList<Double>(maxTicks)
        var bounced = false
        var groundedLastTick = false

        if (holdForward) velocity += groundAcceleration(sprint)
        distance += velocity
        verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
        velocity *= groundFriction
        heights += height
        distances += distance

        val airAcceleration = when {
            !holdForward -> 0.0
            sprint -> SPRINT_AIR_ACCELERATION
            else -> WALK_AIR_ACCELERATION
        }

        for (tick in 1..maxTicks) {

            val grounded = groundedLastTick
            groundedLastTick = false
            velocity += if (grounded && holdForward) groundAcceleration(sprint) else airAcceleration
            distance += velocity

            if (!bounced) {
                if (height + verticalVelocity <= -drop) {

                    height = -drop.toDouble()
                    verticalVelocity = -verticalVelocity * bounceFactor
                    bounced = true
                    groundedLastTick = true
                } else {
                    height += verticalVelocity
                }
            } else if (verticalVelocity < 0.0) {
                if (height < rise) return null
                if (height + verticalVelocity <= rise) {
                    heights += rise.toDouble()
                    distances += distance
                    return ArcSample(
                        airTicks = tick,
                        distance = distance,
                        heights = heights.toDoubleArray(),
                        distances = distances.toDoubleArray(),
                        exitSpeed = velocity * HORIZONTAL_DRAG,
                    )
                }
                height += verticalVelocity
            } else {
                height += verticalVelocity
            }

            verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
            velocity *= if (grounded) groundFriction else HORIZONTAL_DRAG
            heights += height
            distances += distance
        }
        return null
    }

    companion object {

        const val MAX_BOUNCE_TICKS = 48

        const val SPRINT_JUMP_BOOST = 0.2

        const val INPUT_DAMPING = 0.98

        const val VERTICAL_DRAG = 0.98
        const val HORIZONTAL_DRAG = 0.91

        const val WALK_AIR_ACCELERATION = INPUT_DAMPING * 0.02
        const val SPRINT_AIR_ACCELERATION = INPUT_DAMPING * 0.026

        const val MAX_ARC_TICKS = 24

        private const val DEFAULT_MOVEMENT_SPEED = 0.1
        private const val DEFAULT_SLIPPERINESS = 0.6

        val VANILLA = of(
            movementSpeed = DEFAULT_MOVEMENT_SPEED,
            gravity = 0.08,
            jumpStrength = 0.42,
            jumpBoostVelocityModifier = 0.0,
            slipperiness = DEFAULT_SLIPPERINESS,
        )

        fun of(
            profile: PlayerPhysicsProfile,
            slipperiness: Double = DEFAULT_SLIPPERINESS,
        ): BallisticProfile = of(
            movementSpeed = profile.movementSpeed,
            gravity = profile.gravity,
            jumpStrength = profile.jumpStrength,
            jumpBoostVelocityModifier = profile.jumpBoostVelocityModifier,
            slipperiness = slipperiness,
        )

        fun of(
            movementSpeed: Double,
            gravity: Double,
            jumpStrength: Double,
            jumpBoostVelocityModifier: Double,
            slipperiness: Double,
        ): BallisticProfile {

            val slipperinessCubed = slipperiness * slipperiness * slipperiness
            val walk = movementSpeed * (0.21600002 / slipperinessCubed)
            val sprint = walk * PlayerPhysicsProfile.SPRINT_SPEED_MULTIPLIER
            return BallisticProfile(
                gravity = gravity,
                jumpVelocity = jumpStrength + jumpBoostVelocityModifier,
                walkGroundAcceleration = INPUT_DAMPING * walk,
                sprintGroundAcceleration = INPUT_DAMPING * sprint,
                slipperiness = slipperiness,
            )
        }
    }
}
