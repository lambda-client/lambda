package com.lambda.gui.snap

import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.gridSize
import com.lambda.module.modules.client.GuiSettings.snapEnabled
import com.lambda.module.modules.client.GuiSettings.snapToCenters
import com.lambda.module.modules.client.GuiSettings.snapToEdges
import com.lambda.module.modules.client.GuiSettings.snapToGrid
import com.lambda.module.modules.client.GuiSettings.snapToScreenCenter
import imgui.ImDrawList
import kotlin.math.abs
import kotlin.math.max

object SnapManager {
    private data class SnapGuide(val guide: Guide, val sourceId: String?)
    private val frameGuides = ArrayList<SnapGuide>(512)
    private val elementRects = LinkedHashMap<String, RectF>()
    private var viewW = 0f
    private var viewH = 0f
    private var scale = 1f

    fun beginFrame(viewWidth: Float, viewHeight: Float, uiScale: Float) {
        viewW = max(1f, viewWidth)
        viewH = max(1f, viewHeight)
        scale = max(0.5f, uiScale)
        frameGuides.clear()

        if (snapEnabled && snapToScreenCenter) {
            frameGuides += SnapGuide(Guide(Guide.Orientation.Vertical, viewW * 0.5f, 30, Guide.Kind.ScreenCenter), null)
            frameGuides += SnapGuide(Guide(Guide.Orientation.Horizontal, viewH * 0.5f, 30, Guide.Kind.ScreenCenter), null)
        }

        if (snapEnabled && snapToGrid && gridSize > 0f) {
            val step = max(4f, gridSize * scale)
            var x = 0f
            while (x <= viewW) {
                frameGuides += SnapGuide(Guide(Guide.Orientation.Vertical, x, 10, Guide.Kind.Grid), null)
                x += step
            }
            var y = 0f
            while (y <= viewH) {
                frameGuides += SnapGuide(Guide(Guide.Orientation.Horizontal, y, 10, Guide.Kind.Grid), null)
                y += step
            }
        }

        elementRects.forEach { (id, r) -> addElementGuides(id, r) }
    }

    fun registerElement(id: String, rect: RectF) {
        elementRects[id] = rect
    }

    fun unregisterElement(id: String) {
        elementRects.remove(id)
    }

    private fun addElementGuides(sourceId: String, r: RectF) {
        if (snapEnabled && snapToEdges) {
            frameGuides += SnapGuide(Guide(Guide.Orientation.Vertical, r.left, 100, Guide.Kind.ElementEdge), sourceId)
            frameGuides += SnapGuide(Guide(Guide.Orientation.Vertical, r.right, 100, Guide.Kind.ElementEdge), sourceId)
            frameGuides += SnapGuide(Guide(Guide.Orientation.Horizontal, r.top, 100, Guide.Kind.ElementEdge), sourceId)
            frameGuides += SnapGuide(Guide(Guide.Orientation.Horizontal, r.bottom, 100, Guide.Kind.ElementEdge), sourceId)
        }
        if (snapEnabled && snapToCenters) {
            frameGuides += SnapGuide(Guide(Guide.Orientation.Vertical, r.cx, 80, Guide.Kind.ElementCenter), sourceId)
            frameGuides += SnapGuide(Guide(Guide.Orientation.Horizontal, r.cy, 80, Guide.Kind.ElementCenter), sourceId)
        }
    }

    data class SnapResult(
        val dx: Float,
        val dy: Float,
        val snapX: Float?,
        val snapY: Float?,
        val kindX: Guide.Kind?,
        val kindY: Guide.Kind?
    )

    private fun thresholdFor(kind: Guide.Kind): Float = when (kind) {
        Guide.Kind.ElementEdge, Guide.Kind.ElementCenter -> GuiSettings.snapDistanceElement * scale
        Guide.Kind.ScreenCenter -> GuiSettings.snapDistanceScreen * scale
        Guide.Kind.Grid -> GuiSettings.snapDistanceGrid * scale
    }

    private fun score(dist: Float, strength: Int): Float = dist - strength * 0.08f

    fun computeSnap(proposed: RectF, currentId: String?): SnapResult {
        data class Best(var s: Float = Float.POSITIVE_INFINITY, var d: Float = 0f, var p: Float? = null, var k: Guide.Kind? = null)
        val bestElemX = Best(); val bestElemY = Best()
        val bestScreenX = Best(); val bestScreenY = Best()
        val bestGridX = Best(); val bestGridY = Best()

        fun consider(g: Guide, point: Float, out: Best) {
            val dist = abs(point - g.pos)
            if (dist <= max(1f, thresholdFor(g.kind))) {
                val sc = score(dist, g.strength)
                if (sc < out.s) { out.s = sc; out.d = g.pos - point; out.p = g.pos; out.k = g.kind }
            }
        }

        fun processAxis(
            g: Guide,
            points: FloatArray,
            tier: String,
            elem: Best,
            screen: Best,
            grid: Best
        ) {
            when (tier) {
                "elem" -> for (p in points) consider(g, p, elem)
                "screen" -> for (p in points) consider(g, p, screen)
                "grid" -> for (p in points) consider(g, p, grid)
            }
        }

        frameGuides.forEach { sg ->
            val g = sg.guide
            val isSelf = (currentId != null && sg.sourceId == currentId)
            val tier = when (g.kind) {
                Guide.Kind.ElementEdge, Guide.Kind.ElementCenter -> if (isSelf) null else "elem"
                Guide.Kind.ScreenCenter -> "screen"
                Guide.Kind.Grid -> "grid"
            } ?: return@forEach

            when (g.orientation) {
                Guide.Orientation.Vertical -> {
                    val points = floatArrayOf(proposed.left, proposed.cx, proposed.right)
                    processAxis(g, points, tier, bestElemX, bestScreenX, bestGridX)
                }
                Guide.Orientation.Horizontal -> {
                    val points = floatArrayOf(proposed.top, proposed.cy, proposed.bottom)
                    processAxis(g, points, tier, bestElemY, bestScreenY, bestGridY)
                }
            }
        }

        val choiceX = when {
            bestElemX.s.isFinite() -> bestElemX
            bestScreenX.s.isFinite() -> bestScreenX
            bestGridX.s.isFinite() -> bestGridX
            else -> Best()
        }
        val choiceY = when {
            bestElemY.s.isFinite() -> bestElemY
            bestScreenY.s.isFinite() -> bestScreenY
            bestGridY.s.isFinite() -> bestGridY
            else -> Best()
        }

        return SnapResult(
            dx = if (choiceX.s.isFinite()) choiceX.d else 0f,
            dy = if (choiceY.s.isFinite()) choiceY.d else 0f,
            snapX = choiceX.p, snapY = choiceY.p,
            kindX = choiceX.k, kindY = choiceY.k
        )
    }

    fun drawSnapLines(draw: ImDrawList, snapX: Float?, kindX: Guide.Kind?, snapY: Float?, kindY: Guide.Kind?) {
        val showX = kindX == Guide.Kind.ElementEdge || kindX == Guide.Kind.ElementCenter
        val showY = kindY == Guide.Kind.ElementEdge || kindY == Guide.Kind.ElementCenter
        if (!showX && !showY) return

        val col = GuiSettings.snapLineColor.rgb
        val thick = 2f
        if (showX && snapX != null) draw.addLine(snapX, 0f, snapX, viewH, col, thick)
        if (showY && snapY != null) draw.addLine(0f, snapY, viewW, snapY, col, thick)
    }
}