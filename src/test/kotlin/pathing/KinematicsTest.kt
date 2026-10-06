/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.physics.Vanilla
import kotlin.test.Test
import kotlin.test.assertEquals

class KinematicsTest {
    @Test
    fun `fall step is gravity then vanilla drag, and the coarse fall table is built from it`() {
        val drag = Vanilla.VERTICAL_DRAG.toDouble()
        assertEquals((0.0 + Vanilla.GRAVITY) * drag, BallisticProfile.VANILLA.fallStep(0.0))
        assertEquals((-0.5 + Vanilla.GRAVITY) * drag, BallisticProfile.VANILLA.fallStep(-0.5))
        var velocity = 0.0
        var fallen = 0.0
        var tick = 0
        var depth = 1
        val expected = DoubleArray(13)
        while (depth <= 12) {
            tick++
            velocity = (velocity + Vanilla.GRAVITY) * drag
            fallen += velocity
            while (depth <= 12 && fallen >= depth) {
                expected[depth] = tick.toDouble()
                depth++
            }
        }
        for (d in 1..12) assertEquals(expected[d], CoarseMoveRates.fallTicks(d), "depth $d")
    }
}
