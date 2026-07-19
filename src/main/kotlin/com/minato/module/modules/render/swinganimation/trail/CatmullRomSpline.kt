@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.trail

import net.minecraft.util.math.Vec3d
import kotlin.math.floor

/**
 * Catmull-Rom spline 3D — nội suy mượt qua các điểm control point.
 * Dùng để tạo trail mượt mà cho swing animation.
 *
 * Công thức: q(t) = 0.5 * ((2*P1) + (-P0 + P2)*t + (2*P0 - 5*P1 + 4*P2 - P3)*t² + (-P0 + 3*P1 - 3*P2 + P3)*t³)
 */
object CatmullRomSpline {

    /**
     * Nội suy Catmull-Rom qua 4 điểm control.
     * @param p0, p1, p2, p3 4 điểm control (p1, p2 là segment chính)
     * @param t tham số 0..1
     */
    fun interpolate(p0: Vec3d, p1: Vec3d, p2: Vec3d, p3: Vec3d, t: Double): Vec3d {
        val t2 = t * t
        val t3 = t2 * t
        return Vec3d(
            0.5 * ((2.0 * p1.x) + (-p0.x + p2.x) * t + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3),
            0.5 * ((2.0 * p1.y) + (-p0.y + p2.y) * t + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3),
            0.5 * ((2.0 * p1.z) + (-p0.z + p2.z) * t + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2 + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3),
        )
    }

    /**
     * Build toàn bộ spline từ list các điểm control.
     * @param points Danh sách điểm control (tối thiểu 2)
     * @param subdivisions Số sub-segment giữa mỗi cặp point
     * @return List điểm nội suy mượt
     */
    fun buildSpline(points: List<Vec3d>, subdivisions: Int = 7): List<Vec3d> {
        if (points.size < 2) return points
        if (points.size == 2) {
            return interpolateLine(points[0], points[1], subdivisions)
        }

        val result = mutableListOf<Vec3d>()

        for (i in 0 until points.size - 1) {
            val p0 = if (i == 0) points[0] else points[i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 >= points.size) points.last() else points[i + 2]

            for (j in 0 until subdivisions) {
                val t = j.toDouble() / subdivisions
                result.add(interpolate(p0, p1, p2, p3, t))
            }
        }

        // Thêm điểm cuối
        result.add(points.last())
        return result
    }

    /**
     * Nội suy tuyến tính giữa 2 điểm (khi không đủ 4 điểm Catmull-Rom).
     */
    private fun interpolateLine(p1: Vec3d, p2: Vec3d, subdivisions: Int): List<Vec3d> {
        val result = mutableListOf<Vec3d>()
        for (i in 0 until subdivisions) {
            val t = i.toDouble() / subdivisions
            result.add(Vec3d(
                p1.x + (p2.x - p1.x) * t,
                p1.y + (p2.y - p1.y) * t,
                p1.z + (p2.z - p1.z) * t,
            ))
        }
        result.add(p2)
        return result
    }

    /**
     * Tính tangent tại 1 điểm trên spline.
     */
    fun tangent(p0: Vec3d, p1: Vec3d, p2: Vec3d): Vec3d {
        return Vec3d(
            (p2.x - p0.x) * 0.5,
            (p2.y - p0.y) * 0.5,
            (p2.z - p0.z) * 0.5,
        ).normalize()
    }
}
