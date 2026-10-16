/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.player

import com.lambda.config.Group
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.editSetting
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Suppress("unused")
object LineLock : Module(
	name = "LineLock",
	description = "Locks the player's yaw to a line and steers toward a point ahead on the line",
	tag = ModuleTag.PLAYER,
) {
	private const val YAW_SNAP_BIAS = 1.0           // nudge before rounding so exact half-steps snap consistently
	private const val FULL_CIRCLE_DEGREES = 360.0
	private const val YAW_DEGREES_OFFSET = 90.0     // atan2 (east = 0°) to Minecraft yaw (south = 0°)
	private const val TOLERANCE = 0.1               // tolerance for classifying a direction as diagonal vs. straight
	private const val RENDER_LINE_HALF_LENGTH = 500.0

	private const val ADVANCED_GROUP = "Advanced"

	private val renderLine by setting("Render Line", true, "Render the axis line the yaw is snapping to")
	private val lineColor by setting("Line Color", Color(0, 255, 0, 255)) { renderLine }

	@Group(ADVANCED_GROUP) private val correctionSmoothness by setting("Correction Smoothness", 5.0, 0.0..10.0, 0.1, "Scales how far ahead down the axis to aim by your speed; higher curves back onto the line more gently")
	@Group(ADVANCED_GROUP) private val yawSnapIncrement by setting("Yaw Snap Increment", 45.0, 1.0..90.0, 1.0, "Snap the locked axis to the nearest multiple of this heading, in degrees")
	@Group(ADVANCED_GROUP) private val onLineThreshold by setting("On Line Threshold", 0.1, 0.0..1.0, 0.01, "How close to the line counts as \"on it\" before holding the snapped yaw")
	@Group(ADVANCED_GROUP) private val minLookahead by setting("Min Lookahead", 2.0, 0.0..10.0, 0.1, "Floor for the look-ahead distance so steering stays stable at low speed")

	private var startingYaw = 0.0
	private var lineOrigin: Vec3d = Vec3d.ZERO
	private var lineDirection: Vec3d = Vec3d.ZERO

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::rotationConfig)
				rotationConfig::rotationMode.editSetting { defaultValue(RotationMode.Lock) }
			}

		//  When enabled we lock onto a straight "rail" and steer the camera down it:
		//
		//        lineOrigin ●─────────●──────────►  lineDirection (the way the rail points)
		//                            ╱ closestPoint (rail spot nearest you)
		//                    you ●──╯                → we aim a bit further down the rail
		//                                              (lookaheadPoint) so you curve back on
		onEnable {
			// Snap our facing to the nearest Yaw Snap Increment (e.g. 45° -> N, NE, E, SE, ...)
			// and lock the rail in from where we're standing.
			val nearestNotch = ((player.yaw + YAW_SNAP_BIAS) / yawSnapIncrement).roundToInt()  // which notch we're closest to
			startingYaw = nearestNotch * yawSnapIncrement % FULL_CIRCLE_DEGREES                  // back to degrees, kept in 0–360
			lineOrigin = player.pos
			lineDirection = directionForHeading(startingYaw)
			adjustYaw()
		}

		//  Every tick: find the rail spot nearest you, pick a point a little ahead of it,
		//  and turn to face that point.
		listen<TickEvent.Pre> {
			if (player.velocity == Vec3d.ZERO) return@listen
			adjustYaw()
		}

		immediateRenderer("LineLock Renderer") {
			if (!renderLine) return@immediateRenderer
			runSafe {
				val playerPos = Vec3d(player.x, lineOrigin.y, player.z)
				val closestPoint = closestPointOnLine(playerPos, lineOrigin, lineDirection)
				val lineStart = closestPoint.subtract(lineDirection.multiply(RENDER_LINE_HALF_LENGTH))
				val lineEnd = closestPoint.add(lineDirection.multiply(RENDER_LINE_HALF_LENGTH))
				line(lineStart, lineEnd, lineColor)
			}
		}
	}

	private fun SafeContext.adjustYaw() {
		val playerPos = Vec3d(player.x, lineOrigin.y, player.z)
		val closestPoint = closestPointOnLine(playerPos, lineOrigin, lineDirection)
		val distanceToLine = Vec3d(closestPoint.x, playerPos.y, closestPoint.z).distanceTo(playerPos)
		val targetYaw = if (distanceToLine < onLineThreshold) {
			// Already on the line - hold the snapped axis yaw.
			startingYaw
		} else {
			// Off the line - steer toward a point ahead so we converge back onto it.
			yawTowardsLine(closestPoint)
		}
		rotationRequest { yaw(targetYaw) }.submit()
	}

	private fun SafeContext.yawTowardsLine(closestPoint: Vec3d): Double {
		// Aim further down the rail than the nearest spot, so we curve back onto it. How far ahead
		// scales with our speed (times Correction Smoothness), but never less than Min Lookahead.
		val lookaheadPoint = closestPoint.add(lineDirection.multiply(max(minLookahead, player.velocity.length() * correctionSmoothness)))
		return headingToward(lookaheadPoint)
	}

	// What compass heading (Minecraft yaw) points from the player toward this spot on the map?
	private fun SafeContext.headingToward(point: Vec3d): Double {
		val eastOffset = point.x - player.pos.x
		val southOffset = point.z - player.pos.z
		return Math.toDegrees(atan2(southOffset, eastOffset)) - YAW_DEGREES_OFFSET
	}

	// Turn a compass heading (degrees) into a unit arrow pointing that way on the map.
	private fun directionForHeading(heading: Double): Vec3d {
		val radians = Math.toRadians(heading)
		return Vec3d(-sin(radians), 0.0, cos(radians)).normalize()
	}

	private fun closestPointOnLine(point: Vec3d, lineOrigin: Vec3d, lineDirection: Vec3d): Vec3d {
		val originToPoint = point.subtract(lineOrigin)
		val projection = originToPoint.dotProduct(lineDirection.normalize())
		return snapToAxis(lineOrigin.add(lineDirection.multiply(projection)), lineOrigin, lineDirection)
	}

	private fun snapToAxis(point: Vec3d, lineOrigin: Vec3d, lineDirection: Vec3d): Vec3d {
		var offset = point.subtract(lineOrigin)

		if (abs(abs(lineDirection.x) - abs(lineDirection.z)) < TOLERANCE) { // diagonal
			val magnitude = (abs(offset.x) + abs(offset.z)) / 2
			val x = if (lineDirection.x > 0) magnitude else -magnitude
			val z = if (lineDirection.z > 0) magnitude else -magnitude
			offset = Vec3d(x, 0.0, z)
		} else { // straight
			val x = if (abs(lineDirection.x) < TOLERANCE) 0.0 else offset.x
			val z = if (abs(lineDirection.z) < TOLERANCE) 0.0 else offset.z
			if (x == 0.0) {
				offset = Vec3d(0.0, 0.0, z)
			} else if (z == 0.0) {
				offset = Vec3d(x, 0.0, 0.0)
			}
		}
		return lineOrigin.add(offset)
	}
}