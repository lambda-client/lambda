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

import com.lambda.Lambda.mc
import com.lambda.config.Configurable
import com.lambda.config.configurations.GuiConfig
import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.DearImGui
import com.lambda.gui.LambdaScreen
import com.lambda.gui.MenuBar
import com.lambda.gui.MenuBar.buildMenuBar
import com.lambda.gui.components.QuickSearch.renderQuickSearch
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.gui.snap.RectF
import com.lambda.gui.snap.SnapManager
import com.lambda.gui.snap.SnapManager.drawDragGrid
import com.lambda.gui.snap.SnapManager.drawSnapLines
import com.lambda.gui.snap.SnapManager.updateDragAndSnapping
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.module.tag.ModuleTag.Companion.shownTags
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.play
import com.lambda.util.Describable
import com.lambda.util.KeyCode
import com.lambda.util.NamedEnum
import com.lambda.util.WindowUtils.setLambdaWindowIcon
import imgui.ImGui
import imgui.extension.implot.ImPlot
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiHoveredFlags
import imgui.flag.ImGuiWindowFlags
import net.minecraft.SharedConstants
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen
import net.minecraft.client.gui.screen.ingame.SignEditScreen
import net.minecraft.client.util.Icons
import java.awt.Color

object ClickGuiLayout : Loadable, Configurable(GuiConfig) {
	override val name = "GUI"
	var open = false
	var developerMode = false
	val keybind by setting("Keybind", KeyCode.Y)
	private var initialLayoutComplete = false
	private var frameCount = 0
	private var activeDragWindowName: String? = null
	private var mouseWasDown = false
	private var mousePressedThisFrameGlobal = false
	private var dragOffsetX = 0f
	private var dragOffsetY = 0f
	private val lastBounds = mutableMapOf<String, RectF>()
	private val pendingPositions = mutableMapOf<String, Pair<Float, Float>>()
	private val snapOverlays = mutableMapOf<String, SnapManager.SnapVisual>()

	private enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Snapping("Snapping"),
		Sizing("Sizing"),
		Rounding("Rounding"),
		Colors("Colors"),
		Font("Font")
	}

	@Suppress("unused")
	enum class TooltipType(
		override val displayName: String,
		override val description: String,
		val flag: Int
	) : NamedEnum, Describable {
		Stationary("Stationary", "Show tooltip after the mouse stays still for a brief moment (~0.15s). Once shown, you can keep moving over the same item/window without waiting again.", ImGuiHoveredFlags.Stationary),
		NoDelay("No Delay", "Show tooltip immediately when hovering (no waiting).", ImGuiHoveredFlags.DelayNone),
		ShortDelay("Short Delay", "Show tooltip after a short delay (~0.15s), and only after the mouse has been still briefly on the item.", ImGuiHoveredFlags.DelayShort),
		LongDelay("Long Delay", "Show tooltip after a longer delay (~0.40s), and only after the mouse has been still briefly on the item.", ImGuiHoveredFlags.DelayNormal)
	}

	const val BASE_SCALE = 100
	const val BASE_SCALE_MULTI = 1.8

	fun deviceScaleMultiplier() = try {
		val monitorWidth = mc.window.monitor!!.currentVideoMode!!.width.toDouble()
		(monitorWidth / 1920.0).coerceIn(0.5, 4.0)
	} catch (_: Throwable) {
		1.0
	}

	// General
	internal val scaleSetting by setting("Scale", BASE_SCALE, 50..300, 1, unit = "%").group(Group.General)
	val alpha by setting("Alpha", 1.0f, 0.0f..1.0f, 0.01f).group(Group.General)
	val disabledAlpha by setting("Disabled Alpha", 0.6f, 0.0f..1.0f, 0.01f).group(Group.General)
	val tooltipType by setting("Tooltip Type", TooltipType.Stationary, description = "When to show the tooltip.").group(Group.General)
	val setLambdaWindowIcon by setting("Set Lambda Window Icon", true).group(Group.General).onValueChange { _, to ->
		if (to) {
			setLambdaWindowIcon()
		} else {
			val icon = if (SharedConstants.getGameVersion().isStable) Icons.RELEASE else Icons.SNAPSHOT
			mc.window.setIcon(mc.defaultResourcePack, icon)
		}
	}
	@JvmStatic
	val setLambdaWindowTitle by setting("Set Lambda Window Title", true).onValueChange { _, _ -> mc.updateWindowTitle() }.group(Group.General)
	val lambdaTitleAppendixName by setting("Append Username", true) { setLambdaWindowTitle }.onValueChange { _, _ -> mc.updateWindowTitle() }.group(Group.General)

	// Snapping
	val snapEnabled by setting("Enable Snapping", true, "Master toggle for GUI/HUD snapping").group(Group.Snapping)
	val gridSize by setting("Grid Size", 25f, 2f..128f, 1f, "Grid step in pixels") { snapEnabled }.group(Group.Snapping)
	val snapToEdges by setting("Snap To Element Edges", true) { snapEnabled }.group(Group.Snapping)
	val snapToCenters by setting("Snap To Element Centers", true) { snapEnabled }.group(Group.Snapping)
	val snapToScreenCenter by setting("Snap To Screen Center", true) { snapEnabled }.group(Group.Snapping)
	val snapToGrid by setting("Snap To Grid", true) { snapEnabled }.group(Group.Snapping)
	val snapDistanceElement by setting("Snap Distance (Elements)", 20f, 1f..48f, 1f, "Distance threshold in px") { snapEnabled }.group(Group.Snapping)
	val snapDistanceScreen by setting("Snap Distance (Screen Center)", 14f, 1f..48f, 1f) { snapEnabled }.group(Group.Snapping)
	val snapDistanceGrid by setting("Snap Distance (Grid)", 12f, 1f..48f, 1f) { snapEnabled }.group(Group.Snapping)
	val snapLineColor by setting("Snap Line Color", Color(255, 160, 0, 220)) { snapEnabled }.group(Group.Snapping)

	// Sizing
	val windowPaddingX by setting("Window Padding X", 8.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val windowPaddingY by setting("Window Padding Y", 8.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val windowMinSizeX by setting("Window Min Size X", 32.0f, 0.0f..100.0f, 1.0f).group(Group.Sizing)
	val windowMinSizeY by setting("Window Min Size Y", 32.0f, 0.0f..100.0f, 1.0f).group(Group.Sizing)
	val windowTitleAlignX by setting("Window Title Align X", 0.0f, 0.0f..1.0f, 0.01f).group(Group.Sizing)
	val windowTitleAlignY by setting("Window Title Align Y", 0.5f, 0.0f..1.0f, 0.01f).group(Group.Sizing)
	val framePaddingX by setting("Frame Padding X", 4.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val framePaddingY by setting("Frame Padding Y", 3.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val itemSpacingX by setting("Item Spacing X", 8.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val itemSpacingY by setting("Item Spacing Y", 4.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val itemInnerSpacingX by setting("Item Inner Spacing X", 4.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val itemInnerSpacingY by setting("Item Inner Spacing Y", 4.0f, 0.0f..20.0f, 0.1f).group(Group.Sizing)
	val indentSpacing by setting("Indent Spacing", 21.0f, 0.0f..50.0f, 0.1f).group(Group.Sizing)
	val scrollbarSize by setting("Scrollbar Size", 8.4f, 0.0f..30.0f, 0.1f).group(Group.Sizing)
	val grabMinSize by setting("Grab Min Size", 10.0f, 0.0f..30.0f, 0.1f).group(Group.Sizing)
	val windowBorderSize by setting("Window Border Size", 1.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
	val childBorderSize by setting("Child Border Size", 1.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
	val popupBorderSize by setting("Popup Border Size", 1.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
	val frameBorderSize by setting("Frame Border Size", 0.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
	val tabBorderSize by setting("Tab Border Size", 0.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)

	// Rounding
	val windowRounding by setting("Window Rounding", 4.6f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
	val childRounding by setting("Child Rounding", 0.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
	val frameRounding by setting("Frame Rounding", 4.6f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
	val popupRounding by setting("Popup Rounding", 4.6f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
	val scrollbarRounding by setting("Scrollbar Rounding", 9.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
	val grabRounding by setting("Grab Rounding", 4.6f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
	val tabRounding by setting("Tab Rounding", 4.6f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
	val curveTessellationTol by setting("Curve Tessellation Tol", 1.25f, 0.1f..10.0f, 0.05f).group(Group.Rounding)

	// Font
	val fontScale by setting("Font Scale", 1.0, 0.5..2.0, 0.1).group(Group.Font)

	// Colors
	val primaryColor by setting("Primary Color", Color(130, 200, 255)).group(Group.Colors)
	val secondaryColor by setting("Secondary Color", Color(225, 130, 225)).group(Group.Colors)

	@Suppress("unused")
	val shade by setting("Shade", true).group(Group.Colors)
	val colorWidth by setting("Shade Width", 200.0, 10.0..1000.0, 10.0).group(Group.Colors)
	val colorHeight by setting("Shade Height", 200.0, 10.0..1000.0, 10.0).group(Group.Colors)
	val colorSpeed by setting("Color Speed", 1.0, 0.1..5.0, 0.1).group(Group.Colors)
	val text by setting("Text", Color(255, 255, 255, 255)).group(Group.Colors)
	val textDisabled by setting("Text Disabled", Color(128, 128, 128, 255)).group(Group.Colors)
	val windowBg by setting("Window Background", Color(35, 0, 14, 240)).group(Group.Colors)
	val childBg by setting("Child Background", Color(35, 0, 14, 240)).group(Group.Colors)
	val popupBg by setting("Popup Background", Color(35, 0, 14, 240)).group(Group.Colors)
	val border by setting("Border", Color(130, 12, 60, 240)).group(Group.Colors)
	val borderShadow by setting("Border Shadow", Color(51, 0, 21, 240)).group(Group.Colors)
	val frameBg by setting("Frame Background", Color(171, 32, 93, 102)).group(Group.Colors)
	val frameBgHovered by setting("Frame Background Hovered", Color(214, 45, 119, 102)).group(Group.Colors)
	val frameBgActive by setting("Frame Background Active", Color(255, 50, 140, 102)).group(Group.Colors)
	val titleBg by setting("Title Background", Color(125, 0, 50, 240)).group(Group.Colors)
	val titleBgActive by setting("Title Background Active", Color(162, 0, 68, 240)).group(Group.Colors)
	val titleBgCollapsed by setting("Title Background Collapsed", Color(35, 0, 14, 240)).group(Group.Colors)
	val menuBarBg by setting("MenuBar Background", Color(35, 0, 14, 240)).group(Group.Colors)
	val scrollbarBg by setting("Scrollbar Background", Color(35, 0, 14, 240)).group(Group.Colors)
	val scrollbarGrab by setting("Scrollbar Grab", Color(159, 30, 83, 240)).group(Group.Colors)
	val scrollbarGrabHovered by setting("Scrollbar Grab Hovered", Color(198, 40, 105, 240)).group(Group.Colors)
	val scrollbarGrabActive by setting("Scrollbar Grab Active", Color(235, 49, 126, 240)).group(Group.Colors)
	val checkMark by setting("Check Mark", Color(255, 64, 148, 220)).group(Group.Colors)
	val sliderGrab by setting("Slider Grab", Color(207, 46, 117, 200)).group(Group.Colors)
	val sliderGrabActive by setting("Slider Grab Active", Color(241, 67, 143, 200)).group(Group.Colors)
	val button by setting("Button", Color(171, 32, 93, 102)).group(Group.Colors)
	val buttonHovered by setting("Button Hovered", Color(214, 45, 119, 102)).group(Group.Colors)
	val buttonActive by setting("Button Active", Color(255, 50, 140, 102)).group(Group.Colors)
	val header by setting("Header", Color(192, 30, 94, 115)).group(Group.Colors)
	val headerHovered by setting("Header Hovered", Color(255, 59, 136, 115)).group(Group.Colors)
	val headerActive by setting("Header Active", Color(202, 36, 101, 115)).group(Group.Colors)
	val separator by setting("Separator", Color(107, 0, 47, 128)).group(Group.Colors)
	val separatorHovered by setting("Separator Hovered", Color(146, 0, 64, 128)).group(Group.Colors)
	val separatorActive by setting("Separator Active", Color(186, 0, 82, 128)).group(Group.Colors)
	val resizeGrip by setting("Resize Grip", Color(214, 45, 119, 102)).group(Group.Colors)
	val resizeGripHovered by setting("Resize Grip Hovered", Color(214, 45, 119, 102)).group(Group.Colors)
	val resizeGripActive by setting("Resize Grip Active", Color(214, 45, 119, 102)).group(Group.Colors)
	val tab by setting("Tab", Color(121, 21, 65, 140)).group(Group.Colors)
	val tabHovered by setting("Tab Hovered", Color(169, 34, 94, 140)).group(Group.Colors)
	val tabActive by setting("Tab Active", Color(209, 34, 112, 140)).group(Group.Colors)
	val tabUnfocused by setting("Tab Unfocused", Color(121, 21, 65, 120)).group(Group.Colors)
	val tabUnfocusedActive by setting("Tab Unfocused Active", Color(196, 36, 107, 120)).group(Group.Colors)
	val dockingPreview by setting("Docking Preview", Color(208, 47, 117, 102)).group(Group.Colors)
	val dockingEmptyBg by setting("Docking Empty Background", Color(35, 0, 14, 240)).group(Group.Colors)
	val plotLines by setting("Plot Lines", Color(178, 36, 95, 240)).group(Group.Colors)
	val plotLinesHovered by setting("Plot Lines Hovered", Color(209, 40, 110, 240)).group(Group.Colors)
	val plotHistogram by setting("Plot Histogram", Color(192, 32, 91, 255)).group(Group.Colors)
	val plotHistogramHovered by setting("Plot Histogram Hovered", Color(226, 38, 108, 255)).group(Group.Colors)
	val tableHeaderBg by setting("Table Header Background", Color(75, 0, 31, 240)).group(Group.Colors)
	val tableBorderStrong by setting("Table Border Strong", Color(88, 0, 36, 240)).group(Group.Colors)
	val tableBorderLight by setting("Table Border Light", Color(67, 0, 28, 240)).group(Group.Colors)
	val tableRowBg by setting("Table Row Background", Color(35, 0, 14, 240)).group(Group.Colors)
	val tableRowBgAlt by setting("Table Row Background Alt", Color(242, 140, 182, 240)).group(Group.Colors)
	val textSelectedBg by setting("Text Selected Background", Color(218, 54, 121, 240)).group(Group.Colors)
	val dragDropTarget by setting("Drag Drop Target", Color(218, 54, 121, 240)).group(Group.Colors)
	val navHighlight by setting("Nav Highlight", Color(218, 54, 121, 240)).group(Group.Colors)
	val navWindowingHighlight by setting("Nav Windowing Highlight", Color(242, 140, 182, 240)).group(Group.Colors)
	val navWindowingDimBg by setting("Nav Windowing Dim Background", Color(242, 140, 182, 240)).group(Group.Colors)
	val modalWindowDimBg by setting("Modal Window Dim Background", Color(35, 0, 14, 90)).group(Group.Colors)

	init {
		listen<GuiEvent.NewFrame> {
			if (!open) return@listen

			buildLayout {
				buildMenuBar()
				val vp = ImGui.getMainViewport()
				SnapManager.beginFrame(vp.sizeX, vp.sizeY, io.fontGlobalScale)

				val mouseDown = io.mouseDown[0]
				val mousePressedThisFrame = mouseDown && !mouseWasDown
				val mouseReleasedThisFrame = !mouseDown && mouseWasDown
				mouseWasDown = mouseDown
				mousePressedThisFrameGlobal = mousePressedThisFrame

				if (mouseReleasedThisFrame) {
					activeDragWindowName = null
				}

				pendingPositions.clear()
				snapOverlays.clear()

				val mouseDownGlobal = io.mouseDown[0]
				activeDragWindowName?.let { drag ->
					if (mouseDownGlobal) {
						updateDragAndSnapping(
							drag,
							lastBounds[activeDragWindowName]!!,
							dragOffsetX,
							dragOffsetY,
							pendingPositions,
							snapOverlays
						)
						drawDragGrid()
					}
				}

				val tags = if (developerMode) shownTags + ModuleTag.DEBUG else shownTags
				if (tags.isEmpty()) return@buildLayout

				var nextX = 20f
				val baseY = MenuBar.height + 10f

				tags.forEach { tag ->
					val mouseDownGlobal = io.mouseDown[0]
					activeDragWindowName?.let { drag ->
						if (mouseDownGlobal) {
							updateDragAndSnapping(
								drag, lastBounds[drag]!!, dragOffsetX, dragOffsetY, pendingPositions, snapOverlays
							)
						}
					}

					val override = pendingPositions[tag.name]
					if (override != null) {
						ImGui.setNextWindowPos(override.first, override.second)
					} else if (frameCount >= 1) {
						ImGui.setNextWindowPos(nextX, baseY, ImGuiCond.FirstUseEver)
					}

					// FixMe:
					//  Due to the auto resize of windows, if a tag has no module names that is at least the
					//  same length as the tag name, the title of the window will clip out the window box.
					//  For the time being I have removed the ability to collapse the windows so the titles
					//  have more space lol.
					window(tag.name, flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoCollapse) {
						if (activeDragWindowName == null && mousePressedThisFrameGlobal && ImGui.isWindowHovered()) {
							val mx = io.mousePos.x
							val my = io.mousePos.y
							val titleBarHeight = ImGui.getFrameHeight()
							if (my >= windowPos.y && my <= windowPos.y + titleBarHeight) {
								activeDragWindowName = tag.name
								dragOffsetX = mx - windowPos.x
								dragOffsetY = my - windowPos.y
							}
						}

						ModuleRegistry.modules
							.filter { it.tag == tag }
							.forEach { with(ModuleEntry(it)) { buildLayout() } }

						val vis = snapOverlays[tag.name]
						if (vis != null) {
							drawSnapLines(vis.snapX, vis.kindX, vis.snapY, vis.kindY)
						}

						val rect = RectF(windowPos.x, windowPos.y, windowSize.x, windowSize.y)
						SnapManager.registerElement(tag.name, rect)
						lastBounds[tag.name] = rect

						nextX += ImGui.getWindowWidth() + 20f
					}
				}

				if (frameCount++ == 1) {
					initialLayoutComplete = true
				}

				renderQuickSearch()

				if (developerMode) {
					ImGui.showDemoWindow()
					ImPlot.showDemoWindow()
				}
			}
		}

		listen<KeyboardEvent.Press>(alwaysListen = true) { event ->
			if (!event.isPressed) return@listen
			if (mc.options.commandKey.isPressed) return@listen
			if (!event.satisfies(keybind)) return@listen
			if (!open && mc.currentScreen != null) return@listen
			if (open && DearImGui.io.wantTextInput) return@listen

			toggle()
		}
	}

	val Screen?.hasInput: Boolean
		get() = this is ChatScreen ||
				this is SignEditScreen ||
				this is AnvilScreen ||
				this is CommandBlockScreen

	fun toggle() {
		if (open) {
			close()
			LambdaScreen.close()
		} else {
			if (!mc.currentScreen.hasInput) {
				LambdaSound.ModuleOn.play()
				mc.setScreen(LambdaScreen)
				open = true
				frameCount = 0
				initialLayoutComplete = false
			}
		}
	}

	fun close() {
		LambdaSound.ModuleOff.play()
		open = false
	}

	fun applyStyle(scale: Float) {
		val style = ImGui.getStyle()

		style.alpha = alpha
		style.disabledAlpha = disabledAlpha
		style.windowPadding.set(windowPaddingX * scale, windowPaddingY * scale)
		style.windowMinSize.set(windowMinSizeX * scale, windowMinSizeY * scale)
		style.windowTitleAlign.set(windowTitleAlignX, windowTitleAlignY)
		style.windowRounding = windowRounding * scale
		style.windowBorderSize = windowBorderSize * scale
		style.childRounding = childRounding * scale
		style.childBorderSize = childBorderSize * scale
		style.popupRounding = popupRounding * scale
		style.popupBorderSize = popupBorderSize * scale
		style.framePadding.set(framePaddingX * scale, framePaddingY * scale)
		style.frameRounding = frameRounding * scale
		style.frameBorderSize = frameBorderSize * scale
		style.itemSpacing.set(itemSpacingX * scale, itemSpacingY * scale)
		style.itemInnerSpacing.set(itemInnerSpacingX * scale, itemInnerSpacingY * scale)
		style.indentSpacing = indentSpacing * scale
		style.scrollbarSize = scrollbarSize * scale
		style.scrollbarRounding = scrollbarRounding * scale
		style.grabMinSize = grabMinSize * scale
		style.grabRounding = grabRounding * scale
		style.tabRounding = tabRounding * scale
		style.tabBorderSize = tabBorderSize * scale
		style.curveTessellationTol = curveTessellationTol * scale

		setColor(ImGuiCol.Text, text)
		setColor(ImGuiCol.TextDisabled, textDisabled)
		setColor(ImGuiCol.WindowBg, windowBg)
		setColor(ImGuiCol.ChildBg, childBg)
		setColor(ImGuiCol.PopupBg, popupBg)
		setColor(ImGuiCol.Border, border)
		setColor(ImGuiCol.BorderShadow, borderShadow)
		setColor(ImGuiCol.FrameBg, frameBg)
		setColor(ImGuiCol.FrameBgHovered, frameBgHovered)
		setColor(ImGuiCol.FrameBgActive, frameBgActive)
		setColor(ImGuiCol.TitleBg, titleBg)
		setColor(ImGuiCol.TitleBgActive, titleBgActive)
		setColor(ImGuiCol.TitleBgCollapsed, titleBgCollapsed)
		setColor(ImGuiCol.MenuBarBg, menuBarBg)
		setColor(ImGuiCol.ScrollbarBg, scrollbarBg)
		setColor(ImGuiCol.ScrollbarGrab, scrollbarGrab)
		setColor(ImGuiCol.ScrollbarGrabHovered, scrollbarGrabHovered)
		setColor(ImGuiCol.ScrollbarGrabActive, scrollbarGrabActive)
		setColor(ImGuiCol.CheckMark, checkMark)
		setColor(ImGuiCol.SliderGrab, sliderGrab)
		setColor(ImGuiCol.SliderGrabActive, sliderGrabActive)
		setColor(ImGuiCol.Button, button)
		setColor(ImGuiCol.ButtonHovered, buttonHovered)
		setColor(ImGuiCol.ButtonActive, buttonActive)
		setColor(ImGuiCol.Header, header)
		setColor(ImGuiCol.HeaderHovered, headerHovered)
		setColor(ImGuiCol.HeaderActive, headerActive)
		setColor(ImGuiCol.Separator, separator)
		setColor(ImGuiCol.SeparatorHovered, separatorHovered)
		setColor(ImGuiCol.SeparatorActive, separatorActive)
		setColor(ImGuiCol.ResizeGrip, resizeGrip)
		setColor(ImGuiCol.ResizeGripHovered, resizeGripHovered)
		setColor(ImGuiCol.ResizeGripActive, resizeGripActive)
		setColor(ImGuiCol.Tab, tab)
		setColor(ImGuiCol.TabHovered, tabHovered)
		setColor(ImGuiCol.TabActive, tabActive)
		setColor(ImGuiCol.TabUnfocused, tabUnfocused)
		setColor(ImGuiCol.TabUnfocusedActive, tabUnfocusedActive)
		setColor(ImGuiCol.DockingPreview, dockingPreview)
		setColor(ImGuiCol.DockingEmptyBg, dockingEmptyBg)
		setColor(ImGuiCol.PlotLines, plotLines)
		setColor(ImGuiCol.PlotLinesHovered, plotLinesHovered)
		setColor(ImGuiCol.PlotHistogram, plotHistogram)
		setColor(ImGuiCol.PlotHistogramHovered, plotHistogramHovered)
		setColor(ImGuiCol.TableHeaderBg, tableHeaderBg)
		setColor(ImGuiCol.TableBorderStrong, tableBorderStrong)
		setColor(ImGuiCol.TableBorderLight, tableBorderLight)
		setColor(ImGuiCol.TableRowBg, tableRowBg)
		setColor(ImGuiCol.TableRowBgAlt, tableRowBgAlt)
		setColor(ImGuiCol.TextSelectedBg, textSelectedBg)
		setColor(ImGuiCol.DragDropTarget, dragDropTarget)
		setColor(ImGuiCol.NavHighlight, navHighlight)
		setColor(ImGuiCol.NavWindowingHighlight, navWindowingHighlight)
		setColor(ImGuiCol.NavWindowingDimBg, navWindowingDimBg)
		setColor(ImGuiCol.ModalWindowDimBg, modalWindowDimBg)
	}

	private fun setColor(imGuiCol: Int, color: Color) {
		val style = ImGui.getStyle()
		val comp = color.getRGBComponents(null)
		style.setColor(imGuiCol, comp[0], comp[1], comp[2], comp[3])
	}
}
