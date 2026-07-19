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
import com.lambda.imgui.flag.ImDrawFlags
import com.lambda.imgui.flag.ImDrawListFlags
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.imgui.flag.ImGuiStyleVar
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.minato.module.HudModule
import com.minato.module.ModuleRegistry
import com.minato.module.hud.HudTheme
import com.minato.util.CommunicationUtils.info
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

    // HUD Editor overlay
    var hudEditorEnabled by setting("HUD Editor", false, "Enable visual HUD editor with drag & drop and resize")

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
    private val pendingScales = mutableMapOf<String, Float>()
    private val snapOverlays = mutableMapOf<String, SnapHandler.SnapVisual>()
    private var mousePressedThisFrameGlobal = false

    var isShownInGUI = true
    var isLocked = false

    // ── Preset management ──
    private var presetNameInput = ""
    private var showSavePresetDialog = false
    private var presetToDelete: String? = null
    private var lastPresetListRefresh = 0L
    private var cachedPresetList: List<String> = emptyList()

    /** Get (and cache) the current list of presets. */
    private fun getPresetList(): List<String> {
        val now = System.currentTimeMillis()
        if (now - lastPresetListRefresh > 2000) {
            cachedPresetList = HudPreset.listPresets()
            lastPresetListRefresh = now
        }
        return cachedPresetList
    }

    private fun refreshPresetList() {
        cachedPresetList = HudPreset.listPresets()
        lastPresetListRefresh = System.currentTimeMillis()
    }

    private const val PI_F = PI.toFloat()
    private const val HALF_PI_F = (0.5f * PI).toFloat()
    private const val THREE_HALVES_PI_F = (1.5f * PI).toFloat()
    private const val TWO_PI_F = (2f * PI).toFloat()

    /**
     * Toggle HUD editor on/off.
     */
    fun toggleEditor() {
        hudEditorEnabled = !hudEditorEnabled
    }

    /**
     * Whether to show the alignment grid while HUD editor is active.
     */
    fun hudEditorShowGridWhileActive(): Boolean = true

    /**
     * Whether the HUD editor is currently active.
     */
    fun isEditorActive(): Boolean = hudEditorEnabled

    init {
        listen<GuiEvent.NewImguiFrame> {
            if (mc.options.hudHidden) return@listen

            buildLayout {
                if (ClickGuiLayout.open && !isShownInGUI && !hudEditorEnabled) {
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

                // In editor mode: allow drag even when GUI is closed
                val isEditing = hudEditorEnabled
                val allowInteraction = ClickGuiLayout.open || isEditing
                val allowDrag = allowInteraction && (!isLocked || isEditing)

                if (mouseReleasedThisFrame || !allowDrag) {
                    // Persist position when drag ends
                    if (activeDragHudName != null && mouseReleasedThisFrame) {
                        pendingPositions[activeDragHudName!!]?.let { (x, y) ->
                            val hud = ModuleRegistry.modules
                                .filterIsInstance<HudModule>()
                                .find { it.name == activeDragHudName }
                            hud?.let {
                                it.hudX.value = x
                                it.hudY.value = y
                            }
                        }
                    }
                    activeDragHudName = null
                }

                pendingPositions.clear()
                pendingScales.clear()
                snapOverlays.clear()

                val (huds, notShown) = ModuleRegistry.modules
                    .filterIsInstance<HudModule>()
                    .partition { it.isEnabled }
                notShown.forEach { SnapHandler.unregisterElement(it.name) }

                if (allowInteraction) {
                    registerContextMenu(notShown)

                    // Draw grid continuously when editor is active (for alignment reference)
                    if (isEditing) {
                        drawDragGrid()
                    }
                    if (allowDrag) {
                        activeDragHudName?.let { drag ->
                            if (mouseDown) updateDragAndSnapping(
                                drag, lastBounds[drag]!!, dragOffsetX, dragOffsetY, pendingPositions, snapOverlays
                            )
                            // Grid during drag (covers both editor and non-editor modes)
                            drawDragGrid()
                        }
                    }
                }

                huds.forEach { hud ->
                    registerHudElement(hud, isEditing)
                }
            }
        }
    }

    private fun ImGuiBuilder.registerHudElement(hud: HudModule, isEditing: Boolean = false) {
        val override = pendingPositions[hud.name]
        val scaleOverride = pendingScales[hud.name]

        val posX = override?.first ?: hud.hudX.value
        val posY = override?.second ?: hud.hudY.value
        val scale = scaleOverride ?: hud.hudScale.value

        ImGui.setNextWindowPos(posX, posY)

        val bg = hud.backgroundColor.value
        val hasBg = bg.alpha > 0
        val baseFlags = if (hasBg) {
            DEFAULT_HUD_FLAGS and ImGuiWindowFlags.NoBackground.inv()
        } else DEFAULT_HUD_FLAGS
        var hudFlags = if (!isEditing && (!ClickGuiLayout.open || isLocked)) {
            baseFlags or ImGuiWindowFlags.NoMove
        } else baseFlags
        if (!isEditing && !ClickGuiLayout.open) hudFlags = hudFlags or ImGuiWindowFlags.NoInputs

        val pushedColor = if (hasBg) {
            val packed = ImColor.rgba(bg.red, bg.green, bg.blue, bg.alpha)
            ImGui.pushStyleColor(ImGuiCol.WindowBg, packed)
            true
        } else false

        // Apply scale
        if (scale != 1.0f) {
            // Scale is applied via window content scaling
        }

        withStyleVar(ImGuiStyleVar.WindowBorderSize, 0f) {
            window("##${hud.name}", flags = hudFlags) {
                val canDrag = (ClickGuiLayout.open && !isLocked) || isEditing
                if (canDrag && activeDragHudName == null && mousePressedThisFrameGlobal && ImGui.isWindowHovered()) {
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

                if (ClickGuiLayout.open || isEditing) {
                    popupContextWindow("##ctx-${hud.name}") {
                        menuItem("Remove HUD Element") {
                            hud.disable()
                            SnapHandler.unregisterElement(hud.name)
                        }
                        separator()
                        buildConfigSettingsContext(hud)
                    }

                    if (canDrag) {
                        drawHudCornerArcs(windowDrawList, windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                    }
                }

                // Editor overlay: hover highlight + scroll-to-scale
                if (isEditing) {
                    val hovered = ImGui.isWindowHovered()

                    // Scroll wheel to scale
                    val mouseWheel = io.mouseWheel
                    if (hovered && mouseWheel != 0f) {
                        val newScale = (hud.hudScale.value + mouseWheel * 0.05f).coerceIn(0.25f, 3.0f)
                        hud.hudScale.value = newScale
                        pendingScales[hud.name] = newScale
                    }

                    // Draw editor outline + label (solid outline — ImGui doesn't support dashes natively)
                    drawEditorOutline(windowDrawList, windowPos.x, windowPos.y, windowSize.x, windowSize.y, hud.name, scale, hovered)
                }

                val rect = RectF(windowPos.x, windowPos.y, windowSize.x, windowSize.y)
                SnapHandler.registerElement(hud.name, rect)
                lastBounds[hud.name] = rect
            }
        }

        // Scale pop is handled above

        if (pushedColor) {
            ImGui.popStyleColor()
        }
    }

    private fun ImGuiBuilder.drawEditorOutline(draw: ImDrawList, x: Float, y: Float, w: Float, h: Float, name: String, scale: Float, hovered: Boolean) {
        val theme = HudTheme.current
        val alpha = if (hovered) 0.9f else 0.5f
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)

        // Theme-aware colors
        val accent = theme.accentColor
        val outlineColor = ImColor.rgba(accent.red, accent.green, accent.blue, alphaInt)
        val fillColor = ImColor.rgba(accent.red, accent.green, accent.blue, (alphaInt * 0.08f).toInt().coerceIn(0, 255))
        val pad = 3f

        // Fill background
        draw.addRectFilled(x - pad, y - pad, x + w + pad, y + h + pad, fillColor, 6f)

        // Outline — solid since ImGui doesn't support dash natively
        draw.addRect(x - pad, y - pad, x + w + pad, y + h + pad, outlineColor, 6f, ImDrawFlags.None, 1.5f)

        // Label with position and scale info — use theme primary text
        val textColor = ImColor.rgba(accent.red, accent.green, accent.blue, alphaInt.coerceAtLeast(200))
        val label = "$name [${(x).toInt()}, ${(y).toInt()}] x${String.format("%.1f", scale)}"
        draw.addText(x - pad, y - pad - 14f, textColor, label)
    }

    private fun ImGuiBuilder.registerContextMenu(notShown: List<HudModule>) {
        popupContextVoid("##hud-background") {
            if (hudEditorEnabled) {
                menuItem("Exit HUD Editor") {
                    hudEditorEnabled = false
                }
                menuItem(if (isLocked) "Unlock HUD" else "Lock HUD") {
                    isLocked = !isLocked
                }
                menuItem("Theme: ${HudTheme.current.displayName}") {
                    HudTheme.toggle()
                }
                separator()
            } else {
                menuItem(if (isLocked) "Unlock HUD" else "Lock HUD") {
                    isLocked = !isLocked
                }
                menuItem("Open HUD Editor") {
                    hudEditorEnabled = true
                }
                menuItem(if (isShownInGUI) "Hide HUD" else "Show HUD") {
                    isShownInGUI = !isShownInGUI
                }
                separator()
            }

            // ── Preset management ──
            menu("Save Preset...") {
                // Quick-save to existing presets
                val presets = getPresetList()
                if (presets.isNotEmpty()) {
                    presets.forEach { presetName ->
                        menuItem("Overwrite \"$presetName\"") {
                            HudPreset.save(HudPreset.snapshot(presetName))
                            info("HUD preset \"$presetName\" saved.")
                        }
                    }
                    separator()
                }
                menuItem("Save As New...") {
                    showSavePresetDialog = true
                }
            }

            menu("Load Preset") {
                val presets = getPresetList()
                if (presets.isEmpty()) {
                    textDisabled("No saved presets")
                } else {
                    presets.forEach { presetName ->
                        menuItem(presetName) {
                            val loaded = HudPreset.load(presetName)
                            if (loaded != null) {
                                HudPreset.apply(loaded)
                                info("HUD preset \"$presetName\" loaded.")
                            } else {
                                info("Failed to load HUD preset \"$presetName\" — file may be corrupted.")
                            }
                        }
                    }
                }
            }

            menu("Delete Preset") {
                val presets = getPresetList()
                if (presets.isEmpty()) {
                    textDisabled("No saved presets")
                } else {
                    presets.forEach { presetName ->
                        menuItem(presetName) {
                            presetToDelete = presetName
                        }
                    }
                }
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
                            hud.hudX.value = mx
                            hud.hudY.value = my
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

        // ── Save Preset dialog ──
        if (showSavePresetDialog) {
            ImGui.openPopup("Save HUD Preset")
            showSavePresetDialog = false
        }

        popupModal("Save HUD Preset", windowFlags = ImGuiWindowFlags.AlwaysAutoResize) {
            text("Enter a name for the new preset:")
            inputText("##presetName", ::presetNameInput)

            // Warn if name conflicts with existing
            val existingPresets = getPresetList()
            val nameConflict = presetNameInput.isNotEmpty() && existingPresets.any { it.equals(presetNameInput, ignoreCase = true) }
            if (nameConflict) {
                ImGui.textColored(1.0f, 0.7f, 0.2f, 1.0f, "A preset with this name already exists — it will be overwritten.")
            }

            ImGui.separator()

            if (presetNameInput.isBlank()) ImGui.beginDisabled()
            button("Save") {
                val finalName = presetNameInput.trim()
                if (finalName.isNotEmpty()) {
                    HudPreset.save(HudPreset.snapshot(finalName))
                    info("HUD preset \"$finalName\" saved.")
                    refreshPresetList()
                    presetNameInput = ""
                    ImGui.closeCurrentPopup()
                }
            }
            if (presetNameInput.isBlank()) ImGui.endDisabled()
            ImGui.sameLine()
            button("Cancel") {
                presetNameInput = ""
                ImGui.closeCurrentPopup()
            }
        }

        // ── Delete Preset confirmation ──
        presetToDelete?.let { name ->
            ImGui.openPopup("Delete HUD Preset")
            popupModal("Delete HUD Preset", windowFlags = ImGuiWindowFlags.AlwaysAutoResize) {
                text("Delete preset \"$name\"?")
                textColored("This cannot be undone.", Color(200, 200, 200, 200))

                ImGui.separator()

                button("Delete", width = 80f) {
                    HudPreset.delete(name)
                    info("HUD preset \"$name\" deleted.")
                    refreshPresetList()
                    presetToDelete = null
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                button("Cancel", width = 80f) {
                    presetToDelete = null
                    ImGui.closeCurrentPopup()
                }
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
