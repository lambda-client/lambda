/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.lambda.interaction.request.rotation.Rotation
import com.lambda.interaction.request.rotation.visibilty.PlaceDirection
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceDirectionTest {

    @Test
    fun `test pitch snapping for East direction`() {
        val direction = PlaceDirection.East
        val rot = Rotation(-90.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for North direction`() {
        val direction = PlaceDirection.North
        val rot = Rotation(-180.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for UpEast direction`() {
        val direction = PlaceDirection.UpEast
        val rot = Rotation(-90.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for DownNorth direction`() {
        val direction = PlaceDirection.DownNorth
        
        // Calculate expected pitch boundary for DownNorth at yaw -180.0
        val yawRad = Math.toRadians(-180.0)
        val expectedBoundary = Math.toDegrees(atan(abs(cos(yawRad))))
        
        // Test rotation outside the area (pitch too high)
        val rotationOutside = Rotation(-180.0, -expectedBoundary + 10.0)
        val snapped = direction.snapToArea(rotationOutside)
        
        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }
}