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

package com.lambda.gui.components

import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.gui.snap.Guide
import com.lambda.gui.snap.RectF
import com.lambda.gui.snap.SnapManager
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.ClickGui
import imgui.ImGui
import imgui.ImDrawList
import imgui.flag.ImDrawListFlags
import imgui.flag.ImGuiWindowFlags
import kotlin.math.PI

object HudGuiLayout : Loadable {
    const val DEFAULT_HUD_FLAGS =
        ImGuiWindowFlags.NoDecoration or
                ImGuiWindowFlags.NoBackground or
                ImGuiWindowFlags.AlwaysAutoResize or
                ImGuiWindowFlags.NoDocking
    private var activeDragHudName: String? = null
    private var mouseWasDown = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private val lastBounds = mutableMapOf<String, RectF>()
    private val pendingPositions = mutableMapOf<String, Pair<Float, Float>>()
    private val snapOverlays = mutableMapOf<String, SnapVisual>()

    private data class SnapVisual(
        val snapX: Float?,
        val snapY: Float?,
        val kindX: Guide.Kind?,
        val kindY: Guide.Kind?
    )

    private const val PI_F = PI.toFloat()
    private const val HALF_PI_F = (0.5f * PI).toFloat()
    private const val THREE_HALVES_PI_F = (1.5f * PI).toFloat()
    private const val TWO_PI_F = (2f * PI).toFloat()

    init {
        listen<GuiEvent.NewFrame> {
            buildLayout {
                val vp = ImGui.getMainViewport()
                SnapManager.beginFrame(vp.sizeX, vp.sizeY, io.fontGlobalScale)

                val mouseDown = io.mouseDown[0]
                val mousePressedThisFrame = mouseDown && !mouseWasDown
                val mouseReleasedThisFrame = !mouseDown && mouseWasDown
                mouseWasDown = mouseDown
                if (mouseReleasedThisFrame) {
                    activeDragHudName = null
                }

                pendingPositions.clear()
                snapOverlays.clear()

                val (huds, notShown) = ModuleRegistry.modules
                    .filterIsInstance<HudModule>()
                    .partition { it.isEnabled }

                notShown.forEach { SnapManager.unregisterElement(it.name) }

                if (ClickGui.isEnabled && activeDragHudName == null && mousePressedThisFrame) {
                    tryBeginDrag(huds)
                }

                if (ClickGui.isEnabled && activeDragHudName != null && mouseDown) {
                    updateDragAndSnapping()
                }

                huds.forEach { hud ->
                    val override = pendingPositions[hud.name]
                    if (override != null) {
                        ImGui.setNextWindowPos(override.first, override.second)
                    }
                    window("##${hud.name}", flags = DEFAULT_HUD_FLAGS) {
                        val vis = snapOverlays[hud.name]
                        if (vis != null) {
                            SnapManager.drawSnapLines(
                                foregroundDrawList,
                                vis.snapX, vis.kindX,
                                vis.snapY, vis.kindY
                            )
                        }
                        with(hud) { buildLayout() }
                        if (ClickGui.isEnabled) {
                            drawHudOutline(foregroundDrawList, windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                        }
                        val rect = RectF(windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                        SnapManager.registerElement(hud.name, rect)
                        lastBounds[hud.name] = rect
                    }
                }
            }
        }
    }

    private fun ImGuiBuilder.tryBeginDrag(huds: List<HudModule>) {
        val mx = io.mousePos.x
        val my = io.mousePos.y
        huds.forEach { hud ->
            val r = lastBounds[hud.name] ?: return@forEach
            val inside = mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + r.h
            if (inside) {
                activeDragHudName = hud.name
                dragOffsetX = mx - r.x
                dragOffsetY = my - r.y
                return
            }
        }
    }

    private fun ImGuiBuilder.updateDragAndSnapping() {
        val id = activeDragHudName ?: return
        val last = lastBounds[id] ?: return
        val mx = io.mousePos.x
        val my = io.mousePos.y
        val targetX = mx - dragOffsetX
        val targetY = my - dragOffsetY
        val proposed = RectF(targetX, targetY, last.w, last.h)
        val snap = SnapManager.computeSnap(proposed, id)
        val finalX = targetX + snap.dx
        val finalY = targetY + snap.dy
        pendingPositions[id] = finalX to finalY
        snapOverlays[id] = SnapVisual(snap.snapX, snap.snapY, snap.kindX, snap.kindY)
    }

    private fun ImGuiBuilder.drawHudOutline(draw: ImDrawList, x: Float, y: Float, w: Float, h: Float) {
        val baseRadius = GuiSettings.hudOutlineCornerRadius
        val rounding = if (baseRadius > 0f) baseRadius else style.windowRounding
        val inflate = GuiSettings.hudOutlineCornerInflate
        // Soft halo corners (gray, slightly smaller)
        drawCornerArcs(
            draw,
            x, y, w, h,
            (rounding + inflate).coerceAtLeast(0f),
            GuiSettings.hudOutlineHaloColor.rgb,
            GuiSettings.hudOutlineHaloThickness
        )
        // Crisp inner corner arcs
        drawCornerArcs(
            draw,
            x, y, w, h,
            rounding.coerceAtLeast(0f),
            GuiSettings.hudOutlineBorderColor.rgb,
            GuiSettings.hudOutlineBorderThickness
        )
    }

    private fun drawCornerArcs(
        draw: ImDrawList,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float,
        color: Int,
        thickness: Float
    ) {
        if (radius <= 0f || thickness <= 0f) return
        val tlCx = x + radius
        val tlCy = y + radius
        val trCx = x + w - radius
        val trCy = y + radius
        val brCx = x + w - radius
        val brCy = y + h - radius
        val blCx = x + radius
        val blCy = y + h - radius

        fun strokeArc(cx: Float, cy: Float, start: Float, end: Float) {
            draw.pathClear()
            draw.pathArcTo(cx, cy, radius, start, end, 0)
            draw.pathStroke(color, ImDrawListFlags.None, thickness)
        }

        // TL: pi -> 1.5pi
        strokeArc(tlCx, tlCy, PI_F, THREE_HALVES_PI_F)
        // TR: 1.5pi -> 2pi
        strokeArc(trCx, trCy, THREE_HALVES_PI_F, TWO_PI_F)
        // BR: 0 -> 0.5pi
        strokeArc(brCx, brCy, 0f, HALF_PI_F)
        // BL: 0.5pi -> pi
        strokeArc(blCx, blCy, HALF_PI_F, PI_F)
    }
}