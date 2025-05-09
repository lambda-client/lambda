package com.lambda.graphics.renderer.gui

import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.pipeline.VertexBuilder
import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.shader.Shader.Companion.shader
import org.joml.Vector2d
import java.awt.Color
import kotlin.math.*

object LineRenderer {
    private val pipelineSingle = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.LINE)
    private val shaderSingle = shader("renderer/line")

    private val pipelineMulti = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.MULTILINE)
    private val shaderMulti = shader("renderer/multiline")

    fun lines(
        dashiness: Double = 1.0,
        dashPeriod: Double = 1.0,
        batching: Boolean = false,
        block: MultiLineBuilder.() -> Unit
    ): VertexPipeline {
        val lineBuilder = MultiLineBuilder().apply(block)

        if (batching) {
            shaderSingle.use()
            shaderSingle["u_Dashiness"] = dashiness
            shaderSingle["u_DashPeriod"] = dashPeriod * 100.0

            pipelineSingle.upload {
                lineBuilder.lines.forEach { data ->
                    shaderSingle["u_Width"] = data.width
                    putLines(this, data, false)
                }
            }

            return pipelineSingle
        }

        shaderMulti.use()
        shaderMulti["u_Dashiness"] = dashiness
        shaderMulti["u_DashPeriod"] = dashPeriod * 100.0

        lineBuilder.lines.forEach { data ->
            shaderMulti["u_Length"] = -1.0

            pipelineMulti.upload {
                putLines(this, data, true)
            }
        }

        return pipelineMulti
    }

    private fun putLines(
        builder: VertexBuilder,
        data: MultiLineBuilder.LineData,
        putDistance: Boolean
    ) {
        val points = data.points
        val width = data.width

        var dist = 0.0
        val lines = Array(points.size - 1) { i ->
            val p1 = points[i]
            val p2 = points[i + 1]

            var normalX = p1.pos.y - p2.pos.y
            var normalY = p2.pos.x - p1.pos.x

            val length = sqrt(normalX * normalX + normalY * normalY)
            normalX /= length
            normalY /= length

            val normal = Vector2d(normalX, normalY)
            val halfWidth = width / 2.0
            val p11 = Vector2d(p1.pos.x + normal.x * halfWidth, p1.pos.y + normal.y * halfWidth)
            val p12 = Vector2d(p1.pos.x - normal.x * halfWidth, p1.pos.y - normal.y * halfWidth)
            val p21 = Vector2d(p2.pos.x + normal.x * halfWidth, p2.pos.y + normal.y * halfWidth)
            val p22 = Vector2d(p2.pos.x - normal.x * halfWidth, p2.pos.y - normal.y * halfWidth)

            dist += length
            Line(p1, p2, length, dist, p11, p12, p21, p22)
        }

        val joints = mutableListOf<Triple<() -> Int, () -> Int, () -> Int>>()
        repeat(lines.size - 1) { i ->
            val current = lines[i]
            val next = lines[i + 1]

            findIntersection(
                current.p11, current.p21,
                next.p21, next.p11
            )?.also {
                current.p21 = it
                next.p11 = it
                next.connection = false

                joints.add(Triple(current::p22i, current::p21i, next::p12i))
            } ?: findIntersection(
                current.p12, current.p22,
                next.p22, next.p12
            )?.also {
                current.p22 = it
                next.p12 = it
                next.connection = true

                joints.add(Triple(current::p21i, next::p11i, current::p22i))
            }
        }

        // Render lines
        builder.use {
            lines.forEachIndexed { i, line ->
                val prev = lines.getOrNull(i - 1)

                line.p11i = vertex {
                    vec2(line.p11.x, line.p11.y)
                    if (putDistance) float(prev?.distance ?: 0.0)
                    if (putDistance) float(width)
                    float(1.0)
                    color(line.point1.color)
                }

                line.p12i = vertex {
                    vec2(line.p12.x, line.p12.y)
                    if (putDistance) float(prev?.distance ?: 0.0)
                    if (putDistance) float(width)
                    float(0.0)
                    color(line.point1.color)
                }

                line.p21i = vertex {
                    vec2(line.p21.x, line.p21.y)
                    if (putDistance) float(line.distance)
                    if (putDistance) float(width)
                    float(1.0)
                    color(line.point2.color)
                }

                line.p22i = vertex {
                    vec2(line.p22.x, line.p22.y)
                    if (putDistance) float(line.distance)
                    if (putDistance) float(width)
                    float(0.0)
                    color(line.point2.color)
                }

                buildQuad(line.p11i, line.p21i, line.p22i, line.p12i)
            }

            joints.forEach { joint ->
                buildTriangle(joint.first(), joint.second(), joint.third())
            }
        }
    }

    private fun findIntersection(
        firstP1: Vector2d,
        firstP2: Vector2d,
        secondP1: Vector2d,
        secondP2: Vector2d,
    ): Vector2d? {
        val denominator = (firstP1.x - firstP2.x) * (secondP1.y - secondP2.y) -
                (firstP1.y - firstP2.y) * (secondP1.x - secondP2.x)

        if (abs(denominator) < 1e-9) return null

        val det1 = firstP1.x * firstP2.y - firstP1.y * firstP2.x
        val det2 = secondP1.x * secondP2.y - secondP1.y * secondP2.x

        val x = (det1 * (secondP1.x - secondP2.x) - (firstP1.x - firstP2.x) * det2) / denominator
        val y = (det1 * (secondP1.y - secondP2.y) - (firstP1.y - firstP2.y) * det2) / denominator

        fun isPointOnSegment(x: Double, y: Double, p1: Vector2d, p2: Vector2d): Boolean {
            val crossProduct = (p2.x - p1.x) * (y - p1.y) - (p2.y - p1.y) * (x - p1.x)
            if (abs(crossProduct) > 1e-9) return false

            val dotProduct = (x - p1.x) * (p2.x - p1.x) + (y - p1.y) * (p2.y - p1.y)
            if (dotProduct < 0) return false

            val squaredLength = (p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2)
            if (dotProduct > squaredLength) return false

            return true
        }

        if (!isPointOnSegment(x, y, firstP1, firstP2)) return null
        if (!isPointOnSegment(x, y, secondP1, secondP2)) return null
        return Vector2d(x, y)
    }

    data class Point(
        val pos: Vector2d,
        val color: Color
    )

    private data class Line(
        val point1: Point,
        val point2: Point,
        val length: Double,
        var distance: Double,
        var p11: Vector2d,
        var p12: Vector2d,
        var p21: Vector2d,
        var p22: Vector2d
    ) {

        var p11i = 0
        var p12i = 0
        var p21i = 0
        var p22i = 0
        var connection: Boolean? = null
    }

    class MultiLineBuilder{
        val lines = mutableListOf<LineData>()

        fun line(width: Double, block: LineBuilder.() -> Unit) {
            lines.add(LineData(width).apply {
                LineBuilder(this).apply(block)
            })
        }

        class LineBuilder(
            private val data: LineData
        ) {
            fun point(position: Vector2d, color: Color) =
                data.points.add(Point(position, color))
        }

        class LineData(
            val width: Double
        ) {
            val points = mutableListOf<Point>()
        }
    }
}