package com.lambda.client.util.graphics

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.util.Wrapper
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.math.Vec2d
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.item.ItemStack
import org.lwjgl.opengl.GL11.*
import kotlin.math.*

/**
 * Utils for basic 2D shapes rendering
 */
object RenderUtils2D {
    val mc = Wrapper.minecraft

    fun drawItem(itemStack: ItemStack, x: Int, y: Int, text: String? = null, drawOverlay: Boolean = true) {
        GlStateUtils.blend(true)
        GlStateUtils.depth(true)
        RenderHelper.enableGUIStandardItemLighting()

        mc.renderItem.zLevel = 0.0f
        mc.renderItem.renderItemAndEffectIntoGUI(itemStack, x, y)
        if (drawOverlay) mc.renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, itemStack, x, y, text)
        mc.renderItem.zLevel = 0.0f

        RenderHelper.disableStandardItemLighting()
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)

        GlStateUtils.depth(false)
        GlStateUtils.texture2d(true)
    }

    fun drawRoundedRectOutline(vertexHelper: VertexHelper, posBegin: Vec2d = Vec2d(0.0, 0.0), posEnd: Vec2d, radius: Double, segments: Int = 0, lineWidth: Float, color: ColorHolder) {
        val pos2 = Vec2d(posEnd.x, posBegin.y) // Top right
        val pos4 = Vec2d(posBegin.x, posEnd.y) // Bottom left

        drawArcOutline(vertexHelper, posBegin.plus(radius), radius, Pair(-90f, 0f), segments, lineWidth, color) // Top left
        drawArcOutline(vertexHelper, pos2.plus(-radius, radius), radius, Pair(0f, 90f), segments, lineWidth, color) // Top right
        drawArcOutline(vertexHelper, posEnd.minus(radius), radius, Pair(90f, 180f), segments, lineWidth, color) // Bottom right
        drawArcOutline(vertexHelper, pos4.plus(radius, -radius), radius, Pair(180f, 270f), segments, lineWidth, color) // Bottom left

        drawLine(vertexHelper, posBegin.plus(radius, 0.0), pos2.minus(radius, 0.0), lineWidth, color) // Top
        drawLine(vertexHelper, posBegin.plus(0.0, radius), pos4.minus(0.0, radius), lineWidth, color) // Left
        drawLine(vertexHelper, pos2.plus(0.0, radius), posEnd.minus(0.0, radius), lineWidth, color) // Right
        drawLine(vertexHelper, pos4.plus(radius, 0.0), posEnd.minus(radius, 0.0), lineWidth, color) // Bottom
    }

    fun drawRoundedRectFilled(vertexHelper: VertexHelper, posBegin: Vec2d = Vec2d(0.0, 0.0), posEnd: Vec2d, radius: Double, segments: Int = 0, color: ColorHolder) {
        val pos2 = Vec2d(posEnd.x, posBegin.y) // Top right
        val pos4 = Vec2d(posBegin.x, posEnd.y) // Bottom left

        drawArcFilled(vertexHelper, posBegin.plus(radius), radius, Pair(-90f, 0f), segments, color) // Top left
        drawArcFilled(vertexHelper, pos2.plus(-radius, radius), radius, Pair(0f, 90f), segments, color) // Top right
        drawArcFilled(vertexHelper, posEnd.minus(radius), radius, Pair(90f, 180f), segments, color) // Bottom right
        drawArcFilled(vertexHelper, pos4.plus(radius, -radius), radius, Pair(180f, 270f), segments, color) // Bottom left

        drawRectFilled(vertexHelper, posBegin.plus(radius, 0.0), pos2.plus(-radius, radius), color) // Top
        drawRectFilled(vertexHelper, posBegin.plus(0.0, radius), posEnd.minus(0.0, radius), color) // Center
        drawRectFilled(vertexHelper, pos4.plus(radius, -radius), posEnd.minus(radius, 0.0), color) // Bottom
    }

    fun drawCircleOutline(vertexHelper: VertexHelper, center: Vec2d = Vec2d(0.0, 0.0), radius: Double, segments: Int = 0, lineWidth: Float = 1f, color: ColorHolder) {
        drawArcOutline(vertexHelper, center, radius, Pair(0f, 360f), segments, lineWidth, color)
    }

    fun drawCircleFilled(vertexHelper: VertexHelper, center: Vec2d = Vec2d(0.0, 0.0), radius: Double, segments: Int = 0, color: ColorHolder) {
        drawArcFilled(vertexHelper, center, radius, Pair(0f, 360f), segments, color)
    }

    private fun drawArcOutline(vertexHelper: VertexHelper, center: Vec2d = Vec2d(0.0, 0.0), radius: Double, angleRange: Pair<Float, Float>, segments: Int = 0, lineWidth: Float = 1f, color: ColorHolder) {
        val arcVertices = getArcVertices(center, radius, angleRange, segments)
        drawLineStrip(vertexHelper, arcVertices, lineWidth, color)
    }

    private fun drawArcFilled(vertexHelper: VertexHelper, center: Vec2d = Vec2d(0.0, 0.0), radius: Double, angleRange: Pair<Float, Float>, segments: Int = 0, color: ColorHolder) {
        val arcVertices = getArcVertices(center, radius, angleRange, segments)
        drawTriangleFan(vertexHelper, center, arcVertices, color)
    }

    fun drawRectOutline(vertexHelper: VertexHelper, posBegin: Vec2d = Vec2d(0.0, 0.0), posEnd: Vec2d, lineWidth: Float = 1f, color: ColorHolder) {
        val pos2 = Vec2d(posEnd.x, posBegin.y) // Top right
        val pos4 = Vec2d(posBegin.x, posEnd.y) // Bottom left
        val vertices = arrayOf(posBegin, pos2, posEnd, pos4)
        drawLineLoop(vertexHelper, vertices, lineWidth, color)
    }

    fun drawRectOutlineList(vertexHelper: VertexHelper,
                            rects: List<Pair<Vec2d, Vec2d>>,
                            lineWidth: Float = 1f,
                            color: ColorHolder) {
        prepareGl()
        glLineWidth(lineWidth)
        vertexHelper.begin(GL_LINES)
        rects.forEach {
            val pos1 = it.first // Top left
            val pos2 = Vec2d(it.second.x, it.first.y) // Top right
            val pos3 = it.second // Bottom right
            val pos4 = Vec2d(it.first.x, it.second.y) // Bottom left
            vertexHelper.put(pos1, color)
            vertexHelper.put(pos2, color)
            vertexHelper.put(pos2, color)
            vertexHelper.put(pos3, color)
            vertexHelper.put(pos3, color)
            vertexHelper.put(pos4, color)
            vertexHelper.put(pos4, color)
            vertexHelper.put(pos1, color)
        }
        vertexHelper.end()
        releaseGl()
        glLineWidth(1f)
    }

    fun drawRectFilled(vertexHelper: VertexHelper, posBegin: Vec2d = Vec2d(0.0, 0.0), posEnd: Vec2d, color: ColorHolder) {
        val pos2 = Vec2d(posEnd.x, posBegin.y) // Top right
        val pos4 = Vec2d(posBegin.x, posEnd.y) // Bottom left
        drawQuad(vertexHelper, posBegin, pos2, posEnd, pos4, color)
    }

    fun drawRectFilledList(vertexHelper: VertexHelper,
                           rects: List<Pair<Vec2d, Vec2d>>,
                           color: ColorHolder) {
        prepareGl()
        vertexHelper.begin(GL_TRIANGLES)
        rects.forEach {
            val pos1 = it.first // Top left
            val pos2 = Vec2d(it.second.x, it.first.y) // Top right
            val pos3 = it.second // Bottom right
            val pos4 = Vec2d(it.first.x, it.second.y) // Bottom left
            vertexHelper.put(pos1, color)
            vertexHelper.put(pos2, color)
            vertexHelper.put(pos4, color)
            vertexHelper.put(pos4, color)
            vertexHelper.put(pos3, color)
            vertexHelper.put(pos2, color)
        }
        vertexHelper.end()
        releaseGl()
    }

    private fun drawQuad(vertexHelper: VertexHelper, pos1: Vec2d, pos2: Vec2d, pos3: Vec2d, pos4: Vec2d, color: ColorHolder) {
        val vertices = arrayOf(pos1, pos2, pos4, pos3)
        drawTriangleStrip(vertexHelper, vertices, color)
    }

    fun drawTriangleOutline(vertexHelper: VertexHelper, pos1: Vec2d, pos2: Vec2d, pos3: Vec2d, lineWidth: Float = 1f, color: ColorHolder) {
        val vertices = arrayOf(pos1, pos2, pos3)
        drawLineLoop(vertexHelper, vertices, lineWidth, color)
    }

    fun drawTriangleFilled(vertexHelper: VertexHelper, pos1: Vec2d, pos2: Vec2d, pos3: Vec2d, color: ColorHolder) {
        prepareGl()

        vertexHelper.begin(GL_TRIANGLES)
        vertexHelper.put(pos1, color)
        vertexHelper.put(pos2, color)
        vertexHelper.put(pos3, color)
        vertexHelper.end()

        releaseGl()
    }

    private fun drawTriangleFan(vertexHelper: VertexHelper, center: Vec2d, vertices: Array<Vec2d>, color: ColorHolder) {
        prepareGl()

        vertexHelper.begin(GL_TRIANGLE_FAN)
        vertexHelper.put(center, color)
        for (vertex in vertices) {
            vertexHelper.put(vertex, color)
        }
        vertexHelper.end()

        releaseGl()
    }

    private fun drawTriangleStrip(vertexHelper: VertexHelper, vertices: Array<Vec2d>, color: ColorHolder) {
        prepareGl()

        vertexHelper.begin(GL_TRIANGLE_STRIP)
        for (vertex in vertices) {
            vertexHelper.put(vertex, color)
        }
        vertexHelper.end()

        releaseGl()
    }

    private fun drawLineLoop(vertexHelper: VertexHelper, vertices: Array<Vec2d>, lineWidth: Float = 1f, color: ColorHolder) {
        prepareGl()
        glLineWidth(lineWidth)

        vertexHelper.begin(GL_LINE_LOOP)
        for (vertex in vertices) {
            vertexHelper.put(vertex, color)
        }
        vertexHelper.end()


        releaseGl()
        glLineWidth(1f)
    }

    fun drawLineStrip(vertexHelper: VertexHelper, vertices: Array<Vec2d>, lineWidth: Float = 1f, color: ColorHolder) {
        prepareGl()
        glLineWidth(lineWidth)

        vertexHelper.begin(GL_LINE_STRIP)
        for (vertex in vertices) {
            vertexHelper.put(vertex, color)
        }
        vertexHelper.end()


        releaseGl()
        glLineWidth(1f)
    }

    fun drawLine(vertexHelper: VertexHelper, posBegin: Vec2d, posEnd: Vec2d, lineWidth: Float = 1f, color: ColorHolder) {
        prepareGl()
        glLineWidth(lineWidth)

        vertexHelper.begin(GL_LINES)
        vertexHelper.put(posBegin, color)
        vertexHelper.put(posEnd, color)
        vertexHelper.end()

        releaseGl()
        glLineWidth(1f)
    }

    private fun getArcVertices(center: Vec2d, radius: Double, angleRange: Pair<Float, Float>, segments: Int): Array<Vec2d> {
        val range = max(angleRange.first, angleRange.second) - min(angleRange.first, angleRange.second)
        val seg = calcSegments(segments, radius, range)
        val segAngle = (range.toDouble() / seg.toDouble())

        return Array(seg + 1) {
            val angle = Math.toRadians(it * segAngle + angleRange.first.toDouble())
            val unRounded = Vec2d(sin(angle), -cos(angle)).times(radius).plus(center)
            Vec2d(MathUtils.round(unRounded.x, 8), MathUtils.round(unRounded.y, 8))
        }
    }

    private fun calcSegments(segmentsIn: Int, radius: Double, range: Float): Int {
        if (segmentsIn != -0) return segmentsIn
        val segments = radius * 0.5 * PI * (range / 360.0)
        return max(segments.roundToInt(), 16)
    }

    fun prepareGl() {
        GlStateUtils.alpha(false)
        GlStateUtils.texture2d(false)
        GlStateUtils.blend(true)
        GlStateUtils.smooth(true)
        GlStateUtils.lineSmooth(true)
        GlStateUtils.cull(false)
    }

    fun releaseGl() {
        GlStateUtils.alpha(true)
        GlStateUtils.texture2d(true)
        GlStateUtils.smooth(false)
        GlStateUtils.lineSmooth(false)
        GlStateUtils.cull(true)
    }

}