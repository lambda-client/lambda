/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.HorizontalDynamics
import com.lambda.pathing.launch.Kinematics
import com.lambda.pathing.actions.CoarseMoveRates
import kotlin.test.Test
import kotlin.test.assertEquals

class KinematicsTest {
    @Test
    fun `constants match vanilla and their former per-file copies`() {
        assertEquals(0.08, Kinematics.GRAVITY)
        assertEquals(0.98, Kinematics.VERTICAL_DRAG)
        assertEquals(0.91, Kinematics.HORIZONTAL_DRAG)
        assertEquals(0.6, Kinematics.DEFAULT_SLIPPERINESS)
        assertEquals(0.42, Kinematics.JUMP_VELOCITY)
        assertEquals(0.91 * 0.6, Kinematics.DEFAULT_GROUND_FRICTION)
        assertEquals(Kinematics.HORIZONTAL_DRAG, HorizontalDynamics.AIR_DRAG)
        assertEquals(Kinematics.HORIZONTAL_DRAG, BallisticProfile.HORIZONTAL_DRAG)
        assertEquals(Kinematics.VERTICAL_DRAG, BallisticProfile.VERTICAL_DRAG)
        assertEquals(Kinematics.GRAVITY, BallisticProfile.VANILLA.gravity)
        assertEquals(Kinematics.JUMP_VELOCITY, BallisticProfile.VANILLA.jumpVelocity)
    }

    @Test
    fun `fall step is gravity then drag, and the coarse fall table is built from it`() {
        assertEquals((0.0 + 0.08) * 0.98, Kinematics.fallStep(0.0))
        assertEquals((-0.5 + 0.08) * 0.98, Kinematics.fallStep(-0.5))
        // Ticks to fall 1..12 blocks from rest, as the table had them before consolidation.
        var velocity = 0.0
        var fallen = 0.0
        var tick = 0
        var depth = 1
        val expected = DoubleArray(13)
        while (depth <= 12) {
            tick++
            velocity = (velocity + 0.08) * 0.98
            fallen += velocity
            while (depth <= 12 && fallen >= depth) {
                expected[depth] = tick.toDouble()
                depth++
            }
        }
        for (d in 1..12) assertEquals(expected[d], CoarseMoveRates.fallTicks(d), "depth $d")
    }
}
