/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.execution.projectWorldDeltaToLocalInput
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals

class PathfinderExecutorSteeringTest {
    @Test
    fun `projectWorldDeltaToLocalInput is forward on zero yaw southward movement`() {
        val input = projectWorldDeltaToLocalInput(Vec3d(0.0, 0.0, 1.0), 0.0)

        assertEquals(1.0, input.forward, 1.0E-6)
        assertEquals(0.0, input.strafe, 1.0E-6)
    }

    @Test
    fun `projectWorldDeltaToLocalInput is pure left for westward movement at zero yaw`() {
        val input = projectWorldDeltaToLocalInput(Vec3d(-1.0, 0.0, 0.0), 0.0)

        assertEquals(0.0, input.forward, 1.0E-6)
        assertEquals(-1.0, input.strafe, 1.0E-6)
    }

    @Test
    fun `projectWorldDeltaToLocalInput normalizes diagonal input`() {
        val input = projectWorldDeltaToLocalInput(Vec3d(1.0, 0.0, 1.0), 0.0)

        assertEquals(0.707106, input.forward, 1.0E-4)
        assertEquals(0.707106, input.strafe, 1.0E-4)
    }
}
