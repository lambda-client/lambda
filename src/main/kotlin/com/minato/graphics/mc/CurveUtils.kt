
package com.minato.graphics.mc

import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

/**
 * Utility functions for curve and spline calculations.
 *
 * Provides Bezier curves and Catmull-Rom splines for smooth
 * trajectory rendering and path visualization.
 */
object CurveUtils {

	/**
	 * Linear interpolation between two points.
	 */
	fun lerp(t: Double, p0: Vec3d, p1: Vec3d): Vec3d {
		return Vec3d(
			MathHelper.lerp(t, p0.x, p1.x),
			MathHelper.lerp(t, p0.y, p1.y),
			MathHelper.lerp(t, p0.z, p1.z)
		)
	}

	/**
	 * Quadratic Bezier curve.
	 *
	 * B(t) = (1-t)²P0 + 2(1-t)tP1 + t²P2
	 *
	 * @param t Parameter from 0.0 to 1.0
	 * @param p0 Start point
	 * @param p1 Control point
	 * @param p2 End point
	 */
	fun quadraticBezier(t: Double, p0: Vec3d, p1: Vec3d, p2: Vec3d): Vec3d {
		val mt = 1.0 - t
		val mt2 = mt * mt
		val t2 = t * t

		return Vec3d(
			mt2 * p0.x + 2 * mt * t * p1.x + t2 * p2.x,
			mt2 * p0.y + 2 * mt * t * p1.y + t2 * p2.y,
			mt2 * p0.z + 2 * mt * t * p1.z + t2 * p2.z
		)
	}

	/**
	 * Cubic Bezier curve.
	 *
	 * B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
	 *
	 * @param t Parameter from 0.0 to 1.0
	 * @param p0 Start point
	 * @param p1 First control point
	 * @param p2 Second control point
	 * @param p3 End point
	 */
	fun cubicBezier(t: Double, p0: Vec3d, p1: Vec3d, p2: Vec3d, p3: Vec3d): Vec3d {
		val mt = 1.0 - t
		val mt2 = mt * mt
		val mt3 = mt2 * mt
		val t2 = t * t
		val t3 = t2 * t

		return Vec3d(
			mt3 * p0.x + 3 * mt2 * t * p1.x + 3 * mt * t2 * p2.x + t3 * p3.x,
			mt3 * p0.y + 3 * mt2 * t * p1.y + 3 * mt * t2 * p2.y + t3 * p3.y,
			mt3 * p0.z + 3 * mt2 * t * p1.z + 3 * mt * t2 * p2.z + t3 * p3.z
		)
	}

	/**
	 * Catmull-Rom spline interpolation.
	 *
	 * Unlike Bezier curves, Catmull-Rom splines pass through all control points.
	 * Uses MC's built-in catmullRom function.
	 *
	 * @param t Parameter from 0.0 to 1.0 (interpolates between p1 and p2)
	 * @param p0 Point before the segment
	 * @param p1 Start of segment
	 * @param p2 End of segment
	 * @param p3 Point after the segment
	 */
	fun catmullRom(t: Float, p0: Vec3d, p1: Vec3d, p2: Vec3d, p3: Vec3d): Vec3d {
		return Vec3d(
			MathHelper.catmullRom(t, p0.x.toFloat(), p1.x.toFloat(), p2.x.toFloat(), p3.x.toFloat()).toDouble(),
			MathHelper.catmullRom(t, p0.y.toFloat(), p1.y.toFloat(), p2.y.toFloat(), p3.y.toFloat()).toDouble(),
			MathHelper.catmullRom(t, p0.z.toFloat(), p1.z.toFloat(), p2.z.toFloat(), p3.z.toFloat()).toDouble()
		)
	}

	/**
	 * Generate points along a quadratic Bezier curve.
	 *
	 * @param p0 Start point
	 * @param p1 Control point
	 * @param p2 End point
	 * @param segments Number of line segments
	 * @return List of points along the curve
	 */
	fun quadraticBezierPoints(p0: Vec3d, p1: Vec3d, p2: Vec3d, segments: Int): List<Vec3d> {
		return (0..segments).map { i ->
			val t = i.toDouble() / segments
			quadraticBezier(t, p0, p1, p2)
		}
	}

	/**
	 * Generate points along a cubic Bezier curve.
	 *
	 * @param p0 Start point
	 * @param p1 First control point
	 * @param p2 Second control point
	 * @param p3 End point
	 * @param segments Number of line segments
	 * @return List of points along the curve
	 */
	fun cubicBezierPoints(p0: Vec3d, p1: Vec3d, p2: Vec3d, p3: Vec3d, segments: Int): List<Vec3d> {
		return (0..segments).map { i ->
			val t = i.toDouble() / segments
			cubicBezier(t, p0, p1, p2, p3)
		}
	}

	/**
	 * Generate points along a Catmull-Rom spline that passes through all control points.
	 *
	 * @param controlPoints List of points the spline should pass through (minimum 4)
	 * @param segmentsPerSection Number of segments between each pair of control points
	 * @return List of points along the spline
	 */
	fun catmullRomSplinePoints(controlPoints: List<Vec3d>, segmentsPerSection: Int): List<Vec3d> {
		if (controlPoints.size < 4) return controlPoints

		val result = mutableListOf<Vec3d>()

		for (i in 1 until controlPoints.size - 2) {
			val p0 = controlPoints[i - 1]
			val p1 = controlPoints[i]
			val p2 = controlPoints[i + 1]
			val p3 = controlPoints[i + 2]

			for (j in 0 until segmentsPerSection) {
				val t = j.toFloat() / segmentsPerSection
				result.add(catmullRom(t, p0, p1, p2, p3))
			}
		}

		// Add the last point
		result.add(controlPoints[controlPoints.size - 2])

		return result
	}

	/**
	 * Estimate the arc length of a cubic Bezier curve using subdivision.
	 *
	 * @param p0 Start point
	 * @param p1 First control point
	 * @param p2 Second control point
	 * @param p3 End point
	 * @param subdivisions Number of subdivisions for estimation
	 */
	fun cubicBezierLength(p0: Vec3d, p1: Vec3d, p2: Vec3d, p3: Vec3d, subdivisions: Int = 32): Double {
		var length = 0.0
		var prev = p0

		for (i in 1..subdivisions) {
			val t = i.toDouble() / subdivisions
			val curr = cubicBezier(t, p0, p1, p2, p3)
			length += prev.distanceTo(curr)
			prev = curr
		}

		return length
	}

	/**
	 * Calculate the tangent (direction) at a point on a cubic Bezier curve.
	 *
	 * B'(t) = 3(1-t)²(P1-P0) + 6(1-t)t(P2-P1) + 3t²(P3-P2)
	 *
	 * @param t Parameter from 0.0 to 1.0
	 * @param p0 Start point
	 * @param p1 First control point
	 * @param p2 Second control point
	 * @param p3 End point
	 * @return Normalized tangent vector
	 */
	fun cubicBezierTangent(t: Double, p0: Vec3d, p1: Vec3d, p2: Vec3d, p3: Vec3d): Vec3d {
		val mt = 1.0 - t
		val mt2 = mt * mt
		val t2 = t * t

		val d1 = p1.subtract(p0)
		val d2 = p2.subtract(p1)
		val d3 = p3.subtract(p2)

		return Vec3d(
			3 * mt2 * d1.x + 6 * mt * t * d2.x + 3 * t2 * d3.x,
			3 * mt2 * d1.y + 6 * mt * t * d2.y + 3 * t2 * d3.y,
			3 * mt2 * d1.z + 6 * mt * t * d2.z + 3 * t2 * d3.z
		).normalize()
	}

	/**
	 * Create a smooth path through waypoints using Catmull-Rom splines.
	 * Automatically handles the first and last points by duplicating them.
	 *
	 * @param waypoints List of points to pass through (minimum 2)
	 * @param segmentsPerSection Smoothness (higher = smoother)
	 */
	fun smoothPath(waypoints: List<Vec3d>, segmentsPerSection: Int = 16): List<Vec3d> {
		if (waypoints.size < 2) return waypoints
		if (waypoints.size == 2) return listOf(waypoints[0], waypoints[1])

		// Extend with phantom points for natural curve at endpoints
		val extended = buildList {
			// Mirror first point
			add(waypoints[0].add(waypoints[0].subtract(waypoints[1])))
			addAll(waypoints)
			// Mirror last point
			val last = waypoints.last()
			val secondLast = waypoints[waypoints.size - 2]
			add(last.add(last.subtract(secondLast)))
		}

		return catmullRomSplinePoints(extended, segmentsPerSection)
	}
}
