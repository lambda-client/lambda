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

package com.lambda.gui.components

import com.lambda.config.Config
import com.lambda.config.categories.HudCategory
import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.components.SettingsWidget.buildConfigSettingsContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.gui.snap.RectF
import com.lambda.gui.snap.SnapHandler
import com.lambda.gui.snap.SnapHandler.drawDragGrid
import com.lambda.gui.snap.SnapHandler.drawSnapLines
import com.lambda.gui.snap.SnapHandler.updateDragAndSnapping
import com.lambda.imgui.ImColor
import com.lambda.imgui.ImDrawList
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImDrawListFlags
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.imgui.flag.ImGuiStyleVar
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import java.awt.Color
import kotlin.math.PI

object HudGuiLayout : Loadable, Config(
    "HUD",
    HudCategory
) {
    // HUD Outline
    val hudOutlineCornerRadius by setting("HUD Corner Radius", 6.0f, 0.5f..24.0f, 0.5f)
    val hudOutlineHaloColor by setting("HUD Corner Halo Color", Color(140, 140, 140, 90))
    val hudOutlineBorderColor by setting("HUD Corner Border Color", Color(190, 190, 190, 200))
    val hudOutlineHaloThickness by setting("HUD Corner Halo Thickness", 3.0f, 1.0f..6.0f, 0.5f)
    val hudOutlineBorderThickness by setting("HUD Corner Border Thickness", 1.5f, 1.0f..4.0f, 0.5f)
    val hudOutlineCornerInflate by setting("HUD Corner Inflate", 1.0f, 0.0f..4.0f, 0.5f, "Extra radius for the halo arc")

    const val DefaultHudFlags =
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
    private val snapOverlays = mutableMapOf<String, SnapHandler.SnapVisual>()
    private var mousePressedThisFrameGlobal = false

    var isShownInGUI = true
    var isLocked = false

    private const val PiF = PI.toFloat()
    private const val HalfPiF = (0.5f * PI).toFloat()
    private const val ThreeHalvesPiF = (1.5f * PI).toFloat()
    private const val TwoPiF = (2f * PI).toFloat()

    init {
        listen<GuiEvent.NewImguiFrame> {
            if (mc.options.hudHidden) return@listen

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
                notShown.forEach { SnapHandler.unregisterElement(it.name) }

                if (ClickGuiLayout.open) {
					registerContextMenu(notShown)

	                if (!isLocked) {
		                activeDragHudName?.let { drag ->
			                if (mouseDown) updateDragAndSnapping(
				                drag, lastBounds[drag]!!, dragOffsetX, dragOffsetY, pendingPositions, snapOverlays
			                )
			                drawDragGrid()
		                }
	                }
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

        val bg = hud.backgroundColor.value
        val hasBg = bg.alpha > 0
        val baseFlags = if (hasBg) {
            DefaultHudFlags and ImGuiWindowFlags.NoBackground.inv()
        } else DefaultHudFlags
        var hudFlags = if (!ClickGuiLayout.open || isLocked) {
            baseFlags or ImGuiWindowFlags.NoMove
        } else baseFlags
        if (!ClickGuiLayout.open) hudFlags = hudFlags or ImGuiWindowFlags.NoInputs

        val pushedColor = if (hasBg) {
            val packed = ImColor.rgba(bg.red, bg.green, bg.blue, bg.alpha)
            ImGui.pushStyleColor(ImGuiCol.WindowBg, packed)
            true
        } else false

        withStyleVar(ImGuiStyleVar.WindowBorderSize, 0f) {
            window("##${hud.name}", flags = hudFlags) {
                if (ClickGuiLayout.open && !isLocked && activeDragHudName == null && mousePressedThisFrameGlobal && ImGui.isWindowHovered()) {
                    val mx = io.mousePos.x
                    val my = io.mousePos.y
                    activeDragHudName = hud.name
                    dragOffsetX = mx - windowPos.x
                    dragOffsetY = my - windowPos.y
                }

                snapOverlays[hud.name]?.let { visual ->
					drawSnapLines(visual.snapX, visual.kindX, visual.snapY, visual.kindY)
                }
                with(hud) { buildLayout() }

                if (ClickGuiLayout.open) {
                    popupContextWindow("##ctx-${hud.name}") {
                        menuItem("Remove HUD Element") {
                            hud.disable()
                            SnapHandler.unregisterElement(hud.name)
                        }
                        separator()
                        buildConfigSettingsContext(hud)
                    }

                    if (!isLocked) drawHudCornerArcs(windowDrawList, windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                }
                val rect = RectF(windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                SnapHandler.registerElement(hud.name, rect)
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

    private fun ImGuiBuilder.drawHudCornerArcs(draw: ImDrawList, x: Float, y: Float, w: Float, h: Float) {
        val baseRadius = hudOutlineCornerRadius
        val rounding = if (baseRadius > 0f) baseRadius else style.windowRounding
        val inflate = hudOutlineCornerInflate

        draw.pushClipRectFullScreen()

        val haloRadius = (rounding + inflate + 0.5f * hudOutlineHaloThickness + 1.0f).coerceAtLeast(0f)
        val borderRadius = (rounding + 0.5f * hudOutlineBorderThickness + 0.75f).coerceAtLeast(0f)

        drawCornerArcs(
            draw,
            x, y, w, h,
            haloRadius,
            awtToImColor(hudOutlineHaloColor),
            hudOutlineHaloThickness
        )
        drawCornerArcs(
            draw,
            x, y, w, h,
            borderRadius,
            awtToImColor(hudOutlineBorderColor),
            hudOutlineBorderThickness
        )

        draw.popClipRect()
    }

    private fun awtToImColor(c: Color) = ImColor.rgba(c.red, c.green, c.blue, c.alpha)

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
        strokeArc(tlCx, tlCy, PiF, ThreeHalvesPiF)
        // TR: 1.5pi -> 2pi
        strokeArc(trCx, trCy, ThreeHalvesPiF, TwoPiF)
        // BR: 0 -> 0.5pi
        strokeArc(brCx, brCy, 0f, HalfPiF)
        // BL: 0.5pi -> pi
        strokeArc(blCx, blCy, HalfPiF, PiF)
    }
}