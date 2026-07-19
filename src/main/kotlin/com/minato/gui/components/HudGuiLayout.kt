
package com.minato.gui.components

import com.minato.config.Config
import com.minato.config.categories.HudCategory
import com.minato.core.Loadable
import com.minato.event.events.GuiEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.gui.components.SettingsWidget.buildConfigSettingsContext
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.gui.dsl.ImGuiBuilder.buildLayout
import com.minato.gui.snap.RectF
import com.minato.gui.snap.SnapHandler
import com.minato.gui.snap.SnapHandler.drawDragGrid
import com.minato.gui.snap.SnapHandler.drawSnapLines
import com.minato.gui.snap.SnapHandler.updateDragAndSnapping
import com.lambda.imgui.ImColor
import com.lambda.imgui.ImDrawList
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImDrawListFlags
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.imgui.flag.ImGuiStyleVar
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.minato.module.HudModule
import com.minato.module.ModuleRegistry
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
    private val snapOverlays = mutableMapOf<String, SnapHandler.SnapVisual>()
    private var mousePressedThisFrameGlobal = false

    var isShownInGUI = true
    var isLocked = false

    private const val PI_F = PI.toFloat()
    private const val HALF_PI_F = (0.5f * PI).toFloat()
    private const val THREE_HALVES_PI_F = (1.5f * PI).toFloat()
    private const val TWO_PI_F = (2f * PI).toFloat()

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
            DEFAULT_HUD_FLAGS and ImGuiWindowFlags.NoBackground.inv()
        } else DEFAULT_HUD_FLAGS
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
        strokeArc(tlCx, tlCy, PI_F, THREE_HALVES_PI_F)
        // TR: 1.5pi -> 2pi
        strokeArc(trCx, trCy, THREE_HALVES_PI_F, TWO_PI_F)
        // BR: 0 -> 0.5pi
        strokeArc(brCx, brCy, 0f, HALF_PI_F)
        // BL: 0.5pi -> pi
        strokeArc(blCx, blCy, HALF_PI_F, PI_F)
    }
}