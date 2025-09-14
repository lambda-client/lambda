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

import com.lambda.config.Configurable
import com.lambda.config.configurations.HudConfig
import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.components.SettingsWidget.buildConfigSettingsContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.gui.snap.Guide
import com.lambda.gui.snap.RectF
import com.lambda.gui.snap.SnapManager
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.util.NamedEnum
import imgui.ImColor
import imgui.ImGui
import imgui.ImDrawList
import imgui.flag.ImDrawListFlags
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiStyleVar
import java.awt.Color
import kotlin.math.PI
import kotlin.math.max

object HudGuiLayout : Loadable, Configurable(HudConfig) {
    override val name = "HUD"

    enum class Group(override val displayName: String) : NamedEnum {
        Snapping("Snapping"),
        HudOutline("HUD Outline")
    }

    // Snapping
    val snapEnabled by setting("Enable Snapping", true, "Master toggle for HUD snapping").group(Group.Snapping)
    val gridSize by setting("Grid Size", 25f, 2f..128f, 1f, "Grid step in pixels") { snapEnabled }.group(Group.Snapping)
    val snapToEdges by setting("Snap To Element Edges", true) { snapEnabled }.group(Group.Snapping)
    val snapToCenters by setting("Snap To Element Centers", true) { snapEnabled }.group(Group.Snapping)
    val snapToScreenCenter by setting("Snap To Screen Center", true) { snapEnabled }.group(Group.Snapping)
    val snapToGrid by setting("Snap To Grid", true) { snapEnabled }.group(Group.Snapping)
    val snapDistanceElement by setting("Snap Distance (Elements)", 20f, 1f..48f, 1f, "Distance threshold in px") { snapEnabled }.group(Group.Snapping)
    val snapDistanceScreen by setting("Snap Distance (Screen Center)", 14f, 1f..48f, 1f) { snapEnabled }.group(Group.Snapping)
    val snapDistanceGrid by setting("Snap Distance (Grid)", 12f, 1f..48f, 1f) { snapEnabled }.group(Group.Snapping)
    val snapLineColor by setting("Snap Line Color", Color(255, 160, 0, 220)) { snapEnabled }.group(Group.Snapping)

    // HUD Outline
    val hudOutlineCornerRadius by setting("HUD Corner Radius", 6.0f, 0.0f..24.0f, 0.5f).group(Group.HudOutline)
    val hudOutlineHaloColor by setting("HUD Corner Halo Color", Color(140, 140, 140, 90)).group(Group.HudOutline)
    val hudOutlineBorderColor by setting("HUD Corner Border Color", Color(190, 190, 190, 200)).group(Group.HudOutline)
    val hudOutlineHaloThickness by setting("HUD Corner Halo Thickness", 3.0f, 1.0f..6.0f, 0.5f).group(Group.HudOutline)
    val hudOutlineBorderThickness by setting("HUD Corner Border Thickness", 1.5f, 1.0f..4.0f, 0.5f).group(Group.HudOutline)
    val hudOutlineCornerInflate by setting("HUD Corner Inflate", 1.0f, 0.0f..4.0f, 0.5f, "Extra radius for the halo arc").group(Group.HudOutline)

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
    private var mousePressedThisFrameGlobal = false

    var isShownInGUI = true
    var isLocked = false

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
                if (ClickGuiLayout.open && !isShownInGUI) {
                    popupContextVoid("##hud-background") {
                        menuItem(if (isShownInGUI) "Hide HUD" else "Show HUD") {
                            isShownInGUI = !isShownInGUI
                        }
                        separator()
                        menu("HUD Settings") {
                            buildConfigSettingsContext(this@HudGuiLayout)
                        }
                        menu("GUI Settings") {
                            buildConfigSettingsContext(ClickGuiLayout)
                        }
                    }
                    return@buildLayout
                }

                val vp = ImGui.getMainViewport()
                SnapManager.beginFrame(vp.sizeX, vp.sizeY, io.fontGlobalScale)

                val mouseDown = io.mouseDown[0]
                val mousePressedThisFrame = mouseDown && !mouseWasDown
                val mouseReleasedThisFrame = !mouseDown && mouseWasDown
                mouseWasDown = mouseDown
                mousePressedThisFrameGlobal = mousePressedThisFrame

                if (mouseReleasedThisFrame || !ClickGuiLayout.open || isLocked) {
                    activeDragHudName = null
                }

                pendingPositions.clear()
                snapOverlays.clear()

                val (huds, notShown) = ModuleRegistry.modules
                    .filterIsInstance<HudModule>()
                    .partition { it.isEnabled }

                notShown.forEach { SnapManager.unregisterElement(it.name) }

                registerContextMenu(notShown)

                if (ClickGuiLayout.open && !isLocked) {
                     if (activeDragHudName != null && mouseDown) updateDragAndSnapping()
                     if (activeDragHudName != null) drawDragGrid()
                }

                huds.forEach { hud ->
                    registerHudElement(hud)
                }
            }
        }
    }

    private fun ImGuiBuilder.registerHudElement(hud: HudModule) {
        val override = pendingPositions[hud.name]
        if (override != null) {
            ImGui.setNextWindowPos(override.first, override.second)
        }

        val bg = hud.backgroundColor
        val hasBg = bg.alpha > 0
        val baseFlags = if (hasBg) {
            DEFAULT_HUD_FLAGS and ImGuiWindowFlags.NoBackground.inv()
        } else DEFAULT_HUD_FLAGS
        val hudFlags = if (!ClickGuiLayout.open || isLocked) {
            baseFlags or ImGuiWindowFlags.NoMove
        } else baseFlags

        val pushedColor = if (hasBg) {
            val packed = ImColor.rgba(bg.red, bg.green, bg.blue, bg.alpha)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.WindowBg, packed)
            true
        } else {
            false
        }

        val outlineWidth = if (hud.outline) hud.outlineWidth else 0f
        withStyleVar(ImGuiStyleVar.WindowBorderSize, outlineWidth) {
            window("##${hud.name}", flags = hudFlags) {
                if (ClickGuiLayout.open && !isLocked && activeDragHudName == null && mousePressedThisFrameGlobal && ImGui.isWindowHovered()) {
                    val mx = io.mousePos.x
                    val my = io.mousePos.y
                    activeDragHudName = hud.name
                    dragOffsetX = mx - windowPos.x
                    dragOffsetY = my - windowPos.y
                }

                val vis = snapOverlays[hud.name]
                if (vis != null) {
                    SnapManager.drawSnapLines(
                        foregroundDrawList,
                        vis.snapX, vis.kindX,
                        vis.snapY, vis.kindY
                    )
                }
                with(hud) { buildLayout() }

                popupContextWindow("##ctx-${hud.name}") {
                    menuItem("Remove HUD Element") {
                        hud.disable()
                        SnapManager.unregisterElement(hud.name)
                    }
                    separator()
                    buildConfigSettingsContext(hud)
                }

                if (ClickGuiLayout.open && !isLocked) {
                    drawHudCornerArcs(foregroundDrawList, windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                }
                val rect = RectF(windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                SnapManager.registerElement(hud.name, rect)
                lastBounds[hud.name] = rect
            }
        }

        if (pushedColor) {
            ImGui.popStyleColor()
        }
    }

    private fun ImGuiBuilder.registerContextMenu(notShown: List<HudModule>) {
        popupContextVoid("##hud-background") {
            menuItem(if (isLocked) "Unlock HUD" else "Lock HUD") {
                isLocked = !isLocked
            }
            menuItem(if (isShownInGUI) "Hide HUD" else "Show HUD") {
                isShownInGUI = !isShownInGUI
            }
            separator()
            if (notShown.isEmpty()) {
                textDisabled("No hidden HUD elements")
            } else {
                menu("Add HUD Element") {
                    notShown.sortedBy { it.name.lowercase() }.forEach { hud ->
                        menuItem("+ ${hud.name}") {
                            val mx = io.mousePos.x
                            val my = io.mousePos.y
                            hud.enable()
                            pendingPositions[hud.name] = mx to my
                        }
                    }
                }
            }
            separator()
            menu("HUD Settings") {
                buildConfigSettingsContext(this@HudGuiLayout)
            }
            menu("GUI Settings") {
                buildConfigSettingsContext(ClickGuiLayout)
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

    private fun ImGuiBuilder.drawDragGrid() {
        if (!snapEnabled || !snapToGrid) return
        val vp = ImGui.getMainViewport()
        val step = max(4f, gridSize * io.fontGlobalScale)
        if (step <= 0f) return

        val x0 = vp.posX
        val y0 = vp.posY
        val x1 = vp.posX + vp.sizeX
        val y1 = vp.posY + vp.sizeY

        val draw = backgroundDrawList
        val col = ImColor.rgba(255, 255, 255, 28)
        val thickness = 1f

        var x = x0
        while (x <= x1 + 0.5f) {
            draw.addLine(x, y0, x, y1, col, thickness)
            x += step
        }
        var y = y0
        while (y <= y1 + 0.5f) {
            draw.addLine(x0, y, x1, y, col, thickness)
            y += step
        }
    }

    private fun ImGuiBuilder.drawHudCornerArcs(draw: ImDrawList, x: Float, y: Float, w: Float, h: Float) {
        val baseRadius = hudOutlineCornerRadius
        val rounding = if (baseRadius > 0f) baseRadius else style.windowRounding
        val inflate = hudOutlineCornerInflate
        // Soft halo corners
        drawCornerArcs(
            draw,
            x, y, w, h,
            (rounding + inflate).coerceAtLeast(0f),
            hudOutlineHaloColor.rgb,
            hudOutlineHaloThickness
        )
        // Crisp inner corner arcs
        drawCornerArcs(
            draw,
            x, y, w, h,
            rounding.coerceAtLeast(0f),
            hudOutlineBorderColor.rgb,
            hudOutlineBorderThickness
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