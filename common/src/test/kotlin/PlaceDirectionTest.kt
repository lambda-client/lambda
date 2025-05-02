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
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceDirectionTest {
    
    @Test
    fun `test pitch snapping for East direction`() {
        val direction = PlaceDirection.East
        
        // Calculate expected pitch boundary for East at yaw -90.0
        val yawRad = Math.toRadians(-90.0)
        val expectedBoundary = Math.toDegrees(atan(abs(sin(yawRad))))
        
        // Test rotation outside the area (pitch too high)
        val rotationOutsideHigh = Rotation(-90.0, expectedBoundary + 10.0)
        val snappedHigh = direction.snapToArea(rotationOutsideHigh)
        
        // Test rotation outside the area (pitch too low)
        val rotationOutsideLow = Rotation(-90.0, -expectedBoundary - 10.0)
        val snappedLow = direction.snapToArea(rotationOutsideLow)
        
        // Verify that the pitch is snapped to the boundary
        assertEquals(expectedBoundary, snappedHigh.pitch, 0.001, "Pitch should be snapped to the upper boundary")
        assertEquals(-expectedBoundary, snappedLow.pitch, 0.001, "Pitch should be snapped to the lower boundary")
    }
    
    @Test
    fun `test pitch snapping for North direction`() {
        val direction = PlaceDirection.North
        
        // Calculate expected pitch boundary for North at yaw -180.0
        val yawRad = Math.toRadians(-180.0)
        val expectedBoundary = Math.toDegrees(atan(abs(cos(yawRad))))
        
        // Test rotation outside the area (pitch too high)
        val rotationOutsideHigh = Rotation(-180.0, expectedBoundary + 10.0)
        val snappedHigh = direction.snapToArea(rotationOutsideHigh)
        
        // Test rotation outside the area (pitch too low)
        val rotationOutsideLow = Rotation(-180.0, -expectedBoundary - 10.0)
        val snappedLow = direction.snapToArea(rotationOutsideLow)
        
        // Verify that the pitch is snapped to the boundary
        assertEquals(expectedBoundary, snappedHigh.pitch, 0.001, "Pitch should be snapped to the upper boundary")
        assertEquals(-expectedBoundary, snappedLow.pitch, 0.001, "Pitch should be snapped to the lower boundary")
    }
    
    @Test
    fun `test pitch snapping for UpEast direction`() {
        val direction = PlaceDirection.UpEast
        
        // Calculate expected pitch boundary for UpEast at yaw -90.0
        val yawRad = Math.toRadians(-90.0)
        val expectedBoundary = Math.toDegrees(atan(abs(sin(yawRad))))
        
        // Test rotation outside the area (pitch too low)
        val rotationOutside = Rotation(-90.0, expectedBoundary - 10.0)
        val snapped = direction.snapToArea(rotationOutside)
        
        // Verify that the pitch is snapped to the boundary
        assertEquals(expectedBoundary, snapped.pitch, 0.001, "Pitch should be snapped to the boundary")
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
        assertEquals(-expectedBoundary, snapped.pitch, 0.001, "Pitch should be snapped to the boundary")
    }
    
    @Test
    fun `test no snapping when rotation is already in area`() {
        val direction = PlaceDirection.East
        
        // Create a rotation that should be in the East area
        val rotation = Rotation(-90.0, 0.0)
        
        // Verify that the rotation is in the area
        assertTrue(direction.isInArea(rotation), "Rotation should be in the East area")
        
        // Verify that snapToArea returns the same rotation
        val snapped = direction.snapToArea(rotation)
        assertEquals(rotation.yaw, snapped.yaw, 0.001, "Yaw should not change")
        assertEquals(rotation.pitch, snapped.pitch, 0.001, "Pitch should not change")
    }
}