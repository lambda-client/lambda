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

import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.visibilty.PlaceDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the PlaceDirection class
 */
class PlaceDirectionTest {

    // Tests for snapToArea method - Horizontal directions

    @Test
    fun `test pitch snapping for East direction`() {
        val direction = PlaceDirection.East
        val rot = Rotation(-90.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for West direction`() {
        val direction = PlaceDirection.West
        val rot = Rotation(90.0, 90.0)
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
    fun `test pitch snapping for South direction`() {
        val direction = PlaceDirection.South
        val rot = Rotation(0.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    // Tests for snapToArea method - Up directions

    @Test
    fun `test pitch snapping for UpEast direction`() {
        val direction = PlaceDirection.UpEast
        val rot = Rotation(-90.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for UpWest direction`() {
        val direction = PlaceDirection.UpWest
        val rot = Rotation(90.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for UpNorth direction`() {
        val direction = PlaceDirection.UpNorth
        val rot = Rotation(-180.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for UpSouth direction`() {
        val direction = PlaceDirection.UpSouth
        val rot = Rotation(0.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    // Tests for snapToArea method - Down directions

    @Test
    fun `test pitch snapping for DownEast direction`() {
        val direction = PlaceDirection.DownEast
        val rot = Rotation(-90.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for DownWest direction`() {
        val direction = PlaceDirection.DownWest
        val rot = Rotation(90.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for DownNorth direction`() {
        val direction = PlaceDirection.DownNorth
        val rot = Rotation(-180.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for DownSouth direction`() {
        val direction = PlaceDirection.DownSouth
        val rot = Rotation(0.0, 0.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    // Tests for snapToArea method - Pure Up/Down directions

    @Test
    fun `test pitch snapping for UpSouth direction with extreme pitch`() {
        val direction = PlaceDirection.UpSouth
        val rot = Rotation(0.0, -45.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test pitch snapping for DownSouth direction with extreme pitch`() {
        val direction = PlaceDirection.DownSouth
        val rot = Rotation(0.0, 45.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the pitch is snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for East direction from up`() {
        val direction = PlaceDirection.East
        val rot = Rotation(0.0, -90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for South direction from up`() {
        val direction = PlaceDirection.South
        val rot = Rotation(90.0, -90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for West direction from up`() {
        val direction = PlaceDirection.West
        val rot = Rotation(-180.0, -90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for North direction from up`() {
        val direction = PlaceDirection.North
        val rot = Rotation(-90.0, -90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for East direction from down`() {
        val direction = PlaceDirection.East
        val rot = Rotation(0.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for South direction from down`() {
        val direction = PlaceDirection.South
        val rot = Rotation(90.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for West direction from down`() {
        val direction = PlaceDirection.West
        val rot = Rotation(-180.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping for North direction from down`() {
        val direction = PlaceDirection.North
        val rot = Rotation(-90.0, 90.0)
        val snapped = direction.snapToArea(rot)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(direction, PlaceDirection.fromRotation(snapped))
    }

    @Test
    fun `test yaw and pitch snapping from one snap to another starting with East from up`() {
        val direction = PlaceDirection.East
        val rot = Rotation(0.0, -90.0)
        val firstSnapped = direction.snapToArea(rot)

        val nextDirection = PlaceDirection.South
        val secondSnapped = nextDirection.snapToArea(firstSnapped)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(nextDirection, PlaceDirection.fromRotation(secondSnapped))
    }

    @Test
    fun `test yaw and pitch snapping from one snap to another starting with South from up`() {
        val direction = PlaceDirection.South
        val rot = Rotation(90.0, -90.0)
        val firstSnapped = direction.snapToArea(rot)

        val nextDirection = PlaceDirection.West
        val secondSnapped = nextDirection.snapToArea(firstSnapped)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(nextDirection, PlaceDirection.fromRotation(secondSnapped))
    }

    @Test
    fun `test yaw and pitch snapping from one snap to another starting with West from up`() {
        val direction = PlaceDirection.West
        val rot = Rotation(-180.0, -90.0)
        val firstSnapped = direction.snapToArea(rot)

        val nextDirection = PlaceDirection.North
        val secondSnapped = nextDirection.snapToArea(firstSnapped)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(nextDirection, PlaceDirection.fromRotation(secondSnapped))
    }

    @Test
    fun `test yaw and pitch snapping from one snap to another starting with North from up`() {
        val direction = PlaceDirection.North
        val rot = Rotation(-90.0, -90.0)
        val firstSnapped = direction.snapToArea(rot)

        val nextDirection = PlaceDirection.East
        val secondSnapped = nextDirection.snapToArea(firstSnapped)

        // Verify that the yaw and pitch are snapped to the boundary
        assertEquals(nextDirection, PlaceDirection.fromRotation(secondSnapped))
    }

    // Tests for when rotation is already in the area

    @Test
    fun `test no snapping when rotation is already in area`() {
        val direction = PlaceDirection.East
        // Create a rotation that's already in the East area
        val rot = Rotation(-90.0, 0.0)

        // Verify it's already in the area
        assertEquals(direction, PlaceDirection.fromRotation(rot))

        val snapped = direction.snapToArea(rot)

        // Verify that the rotation is unchanged
        assertEquals(rot, snapped)
    }

    // Tests for isInArea method

    @Test
    fun `test isInArea method`() {
        val eastRot = Rotation(-90.0, 0.0)
        val northRot = Rotation(-180.0, 0.0)

        assertTrue(PlaceDirection.East.isInArea(eastRot))
        assertFalse(PlaceDirection.East.isInArea(northRot))

        assertTrue(PlaceDirection.North.isInArea(northRot))
        assertFalse(PlaceDirection.North.isInArea(eastRot))
    }

    // Tests for fromRotation method

    @Test
    fun `test fromRotation for horizontal directions`() {
        assertEquals(PlaceDirection.East, PlaceDirection.fromRotation(Rotation(-90.0, 0.0)))
        assertEquals(PlaceDirection.West, PlaceDirection.fromRotation(Rotation(90.0, 0.0)))
        assertEquals(PlaceDirection.North, PlaceDirection.fromRotation(Rotation(-180.0, 0.0)))
        assertEquals(PlaceDirection.South, PlaceDirection.fromRotation(Rotation(0.0, 0.0)))
    }

    @Test
    fun `test fromRotation for up directions`() {
        assertEquals(PlaceDirection.UpEast, PlaceDirection.fromRotation(Rotation(-90.0, -60.0)))
        assertEquals(PlaceDirection.UpWest, PlaceDirection.fromRotation(Rotation(90.0, -60.0)))
        assertEquals(PlaceDirection.UpNorth, PlaceDirection.fromRotation(Rotation(-180.0, -60.0)))
        assertEquals(PlaceDirection.UpSouth, PlaceDirection.fromRotation(Rotation(0.0, -60.0)))
    }

    @Test
    fun `test fromRotation for down directions`() {
        assertEquals(PlaceDirection.DownEast, PlaceDirection.fromRotation(Rotation(-90.0, 60.0)))
        assertEquals(PlaceDirection.DownWest, PlaceDirection.fromRotation(Rotation(90.0, 60.0)))
        assertEquals(PlaceDirection.DownNorth, PlaceDirection.fromRotation(Rotation(-180.0, 60.0)))
        assertEquals(PlaceDirection.DownSouth, PlaceDirection.fromRotation(Rotation(0.0, 60.0)))
    }

    // Edge case tests

    @Test
    fun `test edge case with yaw at boundaries`() {
        // Test with yaw at the boundaries between directions
        val boundaryYaw = -135.0 // Boundary between North and East

        // Slightly to the East side
        assertEquals(PlaceDirection.East, PlaceDirection.fromRotation(Rotation(boundaryYaw + 0.1, 0.0)))

        // Slightly to the North side
        assertEquals(PlaceDirection.North, PlaceDirection.fromRotation(Rotation(boundaryYaw - 0.1, 0.0)))
    }
}
