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

package com.lambda.gui.snap

import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.components.ClickGuiLayout.gridSize
import com.lambda.gui.components.ClickGuiLayout.snapDistanceElement
import com.lambda.gui.components.ClickGuiLayout.snapDistanceGrid
import com.lambda.gui.components.ClickGuiLayout.snapDistanceScreen
import com.lambda.gui.components.ClickGuiLayout.snapEnabled
import com.lambda.gui.components.ClickGuiLayout.snapLineColor
import com.lambda.gui.components.ClickGuiLayout.snapToCenters
import com.lambda.gui.components.ClickGuiLayout.snapToEdges
import com.lambda.gui.components.ClickGuiLayout.snapToGrid
import com.lambda.gui.components.ClickGuiLayout.snapToScreenCenter
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.lambda.imgui.ImGui
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.max

object SnapHandler : Loadable {
    private data class SnapGuide(val guide: Guide, val sourceId: String?)
    private val frameGuides = ArrayList<SnapGuide>(512)
    private val elementRects = LinkedHashMap<String, RectF>()
    private var viewW = 0f
    private var viewH = 0f
    private var scale = 1f
    private var lastInitFrame = -1

	data class SnapVisual(
		val snapX: Float?,
		val snapY: Float?,
		val kindX: Guide.Kind?,
		val kindY: Guide.Kind?
	)

	init {
		listen<GuiEvent.NewImguiFrame> {
			val vp = ImGui.getMainViewport()
			val io = ImGui.getIO()
			beginFrame(vp.sizeX, vp.sizeY, io.fontGlobalScale)
		}
	}

    fun beginFrame(viewWidth: Float, viewHeight: Float, uiScale: Float) {
        val frame = ImGui.getFrameCount()
        if (frame == lastInitFrame) return
        lastInitFrame = frame

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
        Guide.Kind.ElementEdge, Guide.Kind.ElementCenter -> snapDistanceElement * scale
        Guide.Kind.ScreenCenter -> snapDistanceScreen * scale
        Guide.Kind.Grid -> snapDistanceGrid * scale
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

    fun ImGuiBuilder.drawSnapLines(snapX: Float?, kindX: Guide.Kind?, snapY: Float?, kindY: Guide.Kind?) {
		val draw = foregroundDrawList
        val showX = kindX == Guide.Kind.ElementEdge || kindX == Guide.Kind.ElementCenter
        val showY = kindY == Guide.Kind.ElementEdge || kindY == Guide.Kind.ElementCenter
        if (!showX && !showY) return

        val col = ImColor.rgba(snapLineColor.red, snapLineColor.green, snapLineColor.blue, snapLineColor.alpha)
        val thick = 2f
        if (showX && snapX != null) draw.addLine(snapX, 0f, snapX, viewH, col, thick)
        if (showY && snapY != null) draw.addLine(0f, snapY, viewW, snapY, col, thick)
    }

	fun ImGuiBuilder.drawDragGrid() {
		if (!snapEnabled || !snapToGrid) return
		val step = max(4f, gridSize * io.fontGlobalScale)
		if (step <= 0f) return

		val vp = ImGui.getMainViewport()
		val x0 = vp.posX
		val y0 = vp.posY
		val x1 = vp.posX + vp.sizeX
		val y1 = vp.posY + vp.sizeY
		val col = ImColor.rgba(255, 255, 255, 28)
		val thickness = 1f

		var x = x0
		while (x <= x1 + 0.5f) {
			backgroundDrawList.addLine(x, y0, x, y1, col, thickness)
			x += step
		}
		var y = y0
		while (y <= y1 + 0.5f) {
			backgroundDrawList.addLine(x0, y, x1, y, col, thickness)
			y += step
		}
	}

	fun ImGuiBuilder.updateDragAndSnapping(
		id: String,
		lastBound: RectF,
		dragOffsetX: Float,
		dragOffsetY: Float,
		pendingPositions: MutableMap<String, Pair<Float, Float>>,
		snapOverlays: MutableMap<String, SnapVisual>
	) {
		val mx = io.mousePos.x
		val my = io.mousePos.y
		val targetX = mx - dragOffsetX
		val targetY = my - dragOffsetY
		val proposed = RectF(targetX, targetY, lastBound.w, lastBound.h)
		val snap = computeSnap(proposed, id)
		var finalX = targetX + snap.dx
		var finalY = targetY + snap.dy

		val vp = ImGui.getMainViewport()
		val minX = vp.posX
		val minY = vp.posY
		val maxX = vp.posX + vp.sizeX - lastBound.w
		val maxY = vp.posY + vp.sizeY - lastBound.h

		finalX = if (lastBound.w >= vp.sizeX) minX else finalX.coerceIn(minX, maxX)
		finalY = if (lastBound.h >= vp.sizeY) minY else finalY.coerceIn(minY, maxY)

		pendingPositions[id] = finalX to finalY
		snapOverlays[id] = SnapVisual(snap.snapX, snap.snapY, snap.kindX, snap.kindY)
	}
}