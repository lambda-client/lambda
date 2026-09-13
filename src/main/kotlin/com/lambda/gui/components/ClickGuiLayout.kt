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

import com.lambda.Lambda.mc
import com.lambda.config.Config
import com.lambda.config.Tab
import com.lambda.config.categories.GuiCategory
import com.lambda.config.entries.onValueChange
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPressUnsafe
import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.DearImGui
import com.lambda.gui.LambdaScreen
import com.lambda.gui.MenuBar
import com.lambda.gui.MenuBar.buildMenuBar
import com.lambda.gui.OverlayBackgroundScreen
import com.lambda.gui.components.QuickSearch.renderQuickSearch
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.gui.snap.Guide
import com.lambda.gui.snap.RectF
import com.lambda.gui.snap.SnapHandler
import com.lambda.gui.snap.SnapHandler.drawDragGrid
import com.lambda.gui.snap.SnapHandler.drawSnapLines
import com.lambda.gui.snap.SnapHandler.updateDragAndSnapping
import com.lambda.imgui.ImGui
import com.lambda.imgui.extension.implot.ImPlot
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.imgui.flag.ImGuiCond
import com.lambda.imgui.flag.ImGuiHoveredFlags
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.lambda.imgui.type.ImString
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag
import com.lambda.module.ModuleTag.Companion.shownTags
import com.lambda.module.modules.client.Client
import com.lambda.module.modules.combat.autodisconnect.AutoDisconnectScreen
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundHandler.play
import com.lambda.util.Describable
import com.lambda.util.KeyCode
import com.lambda.util.NamedEnum
import com.lambda.util.WindowUtils.setLambdaWindowIcon
import net.minecraft.SharedConstants
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen
import net.minecraft.client.gui.screen.ingame.SignEditScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import net.minecraft.client.util.Icons
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
@Suppress("unused")
object ClickGuiLayout : Loadable, Config(
	"GUI",
	GuiCategory
) {
	var open = false
	var developerMode = false
	val keybind by setting("Keybind", KeyCode.Y, screenCheck = false)
		.onPressUnsafe {
			if (DearImGui.io.wantTextInput) return@onPressUnsafe
			if (!open && !canOpenOver(mc.currentScreen)) return@onPressUnsafe
			toggle()
		}

	/**
	 * Screens (besides the in-game/null case) the GUI is allowed to open over.
	 * Add a class here to support opening the GUI on another screen.
	 */
	private val backgroundScreenTypes = mutableListOf(
		TitleScreen::class.java,
		MultiplayerScreen::class.java,
		AutoDisconnectScreen::class.java,
	)

	/** True when the GUI may be opened over [screen]; null means in-game. */
	fun canOpenOver(screen: Screen?): Boolean =
		screen == null || backgroundScreenTypes.any { it.isInstance(screen) }

	private var initialLayoutComplete = false
	private var frameCount = 0
	private var activeDragWindowName: String? = null
	private var mouseWasDown = false
	private var dragOffsetX = 0f
	private var dragOffsetY = 0f
	private val lastBounds = mutableMapOf<String, RectF>()
	private val pendingSizes = mutableMapOf<String, Pair<Float, Float>>()
	private var activeResizeWindowName: String? = null
	private var resizeLeftEdge = false
	private var resizeRightEdge = false
	private var resizeTopEdge = false
	private var resizeBottomEdge = false
	private var resizeGrabOffsetX = 0f
	private var resizeGrabOffsetY = 0f
	private val pendingPositions = mutableMapOf<String, Pair<Float, Float>>()
	private val snapOverlays = mutableMapOf<String, SnapHandler.SnapVisual>()

	private const val GENERAL_TAB = "General"
	private const val SNAPPING_TAB = "Snapping"
	private const val SIZING_TAB = "Sizing"
	private const val ROUNDING_TAB = "Rounding"
	private const val COLORS_TAB = "Colors"
	private const val FONT_TAB = "Font"

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
	const val BASE_SCALE_MULTI = 1.45

	fun deviceScaleMultiplier() = try {
		val monitorWidth = mc.window.monitor!!.currentVideoMode!!.width.toDouble()
		(monitorWidth / 1920.0).coerceIn(0.5, 4.0)
	} catch (_: Throwable) {
		1.0
	}

	val currentScale: Float
		get() {
			val userPercent = scaleSetting / 100.0
			val dpi = deviceScaleMultiplier()
			return (BASE_SCALE_MULTI * dpi * userPercent).toFloat()
		}
	// General
	@Tab(GENERAL_TAB) internal val scaleSetting by setting("Scale", BASE_SCALE, 50..300, 1, unit = "%")
	@Tab(GENERAL_TAB) val alpha by setting("Alpha", 1.0f, 0.0f..1.0f, 0.01f)
	@Tab(GENERAL_TAB) val disabledAlpha by setting("Disabled Alpha", 0.6f, 0.0f..1.0f, 0.01f)
	@Tab(GENERAL_TAB) val tooltipType by setting("Tooltip Type", TooltipType.Stationary, description = "When to show the tooltip.")
	@Tab(GENERAL_TAB) val setLambdaWindowIcon by setting("Set Lambda Window Icon", true)
		.onValueChange { _, to ->
			if (to) setLambdaWindowIcon()
			else {
				val icon = if (SharedConstants.getGameVersion().stable()) Icons.RELEASE else Icons.SNAPSHOT
				mc.window.setIcon(mc.defaultResourcePack, icon)
			}
		}
	@Tab(GENERAL_TAB) @JvmStatic val setLambdaWindowTitle by setting("Set Lambda Window Title", true).onValueChange { _, _ -> mc.updateWindowTitle() }
	@Tab(GENERAL_TAB) val lambdaTitleAppendixName by setting("Append Username", true) { setLambdaWindowTitle }.onValueChange { _, _ -> mc.updateWindowTitle() }
	@Tab(GENERAL_TAB) val backgroundBlur by setting("Background Blur", true)
	@Tab(GENERAL_TAB) val backgroundDarkening by setting("Background Darkening", true)

	enum class SettingsMode(override val displayName: String) : NamedEnum {
		INLINE("Inline"),
		POPUP("Popup"),
		BOTH("Both")
	}
	@Tab(GENERAL_TAB) var settingsMode by setting("Settings Mode", SettingsMode.INLINE, "How module settings open (Inline in category window, Popup menu, or Both)")
	@Tab(GENERAL_TAB) var showKeybindBadges by setting("Show Keybind Badges", true, "Show assigned keybind pill on module entries")
	@Tab(GENERAL_TAB) var highlightEnabledModules by setting("Highlight Enabled Modules", true, "Show visual accent bar and highlight on active modules")
	@Tab(GENERAL_TAB) var allowWindowCollapse by setting("Allow Window Collapse", true, "Allow category windows to collapse by clicking arrow or double-clicking title bar")
	@Tab(GENERAL_TAB) var categoryWindowSearch by setting("Category Window Search", false, "Show a search filter at the top of category windows")
	@Tab(GENERAL_TAB) var showCategoryCounts by setting("Show Category Counts", true, "Display enabled/total module count in window titles [X/Y]")

	// Snapping
	@Tab(SNAPPING_TAB) val snapEnabled by setting("Enable Snapping", true, "Master toggle for GUI/HUD snapping")
	@Tab(SNAPPING_TAB) val gridSize by setting("Grid Size", 25f, 2f..128f, 1f, "Grid step in pixels") { snapEnabled }
	@Tab(SNAPPING_TAB) val snapToEdges by setting("Snap To Element Edges", true) { snapEnabled }
	@Tab(SNAPPING_TAB) val snapToCenters by setting("Snap To Element Centers", true) { snapEnabled }
	@Tab(SNAPPING_TAB) val snapToScreenCenter by setting("Snap To Screen Center", true) { snapEnabled }
	@Tab(SNAPPING_TAB) val snapToGrid by setting("Snap To Grid", true) { snapEnabled }
	@Tab(SNAPPING_TAB) val snapDistanceElement by setting("Snap Distance (Elements)", 20f, 1f..48f, 1f, "Distance threshold in px") { snapEnabled }
	@Tab(SNAPPING_TAB) val snapDistanceScreen by setting("Snap Distance (Screen Center)", 14f, 1f..48f, 1f) { snapEnabled }
	@Tab(SNAPPING_TAB) val snapDistanceGrid by setting("Snap Distance (Grid)", 12f, 1f..48f, 1f) { snapEnabled }
	@Tab(SNAPPING_TAB) val snapLineColor by setting("Snap Line Color", Color(255, 160, 0, 220)) { snapEnabled }

	// Sizing
	@Tab(SIZING_TAB) val windowPaddingX by setting("Window Padding X", 8.0f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val windowPaddingY by setting("Window Padding Y", 8.0f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val windowMinSizeX by setting("Window Min Size X", 180.0f, 50.0f..300.0f, 1.0f)
	@Tab(SIZING_TAB) val windowMinSizeY by setting("Window Min Size Y", 32.0f, 0.0f..100.0f, 1.0f)
	@Tab(SIZING_TAB) val windowTitleAlignX by setting("Window Title Align X", 0.0f, 0.0f..1.0f, 0.01f)
	@Tab(SIZING_TAB) val windowTitleAlignY by setting("Window Title Align Y", 0.5f, 0.0f..1.0f, 0.01f)
	@Tab(SIZING_TAB) val framePaddingX by setting("Frame Padding X", 5.0f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val framePaddingY by setting("Frame Padding Y", 3.5f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val itemSpacingX by setting("Item Spacing X", 8.0f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val itemSpacingY by setting("Item Spacing Y", 3.0f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val itemInnerSpacingX by setting("Item Inner Spacing X", 4.0f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val itemInnerSpacingY by setting("Item Inner Spacing Y", 4.0f, 0.0f..20.0f, 0.1f)
	@Tab(SIZING_TAB) val indentSpacing by setting("Indent Spacing", 21.0f, 0.0f..50.0f, 0.1f)
	@Tab(SIZING_TAB) val scrollbarSize by setting("Scrollbar Size", 8.4f, 0.0f..30.0f, 0.1f)
	@Tab(SIZING_TAB) val grabMinSize by setting("Grab Min Size", 10.0f, 0.0f..30.0f, 0.1f)
	@Tab(SIZING_TAB) val windowBorderSize by setting("Window Border Size", 1.0f, 0.0f..5.0f, 0.1f)
	@Tab(SIZING_TAB) val childBorderSize by setting("Child Border Size", 1.0f, 0.0f..5.0f, 0.1f)
	@Tab(SIZING_TAB) val popupBorderSize by setting("Popup Border Size", 1.0f, 0.0f..5.0f, 0.1f)
	@Tab(SIZING_TAB) val frameBorderSize by setting("Frame Border Size", 0.0f, 0.0f..5.0f, 0.1f)
	@Tab(SIZING_TAB) val tabBorderSize by setting("Tab Border Size", 0.0f, 0.0f..5.0f, 0.1f)

	// Rounding
	@Tab(ROUNDING_TAB) val windowRounding by setting("Window Rounding", 4.6f, 0.0f..12.0f, 0.1f)
	@Tab(ROUNDING_TAB) val childRounding by setting("Child Rounding", 0.0f, 0.0f..12.0f, 0.1f)
	@Tab(ROUNDING_TAB) val frameRounding by setting("Frame Rounding", 4.6f, 0.0f..12.0f, 0.1f)
	@Tab(ROUNDING_TAB) val popupRounding by setting("Popup Rounding", 4.6f, 0.0f..12.0f, 0.1f)
	@Tab(ROUNDING_TAB) val scrollbarRounding by setting("Scrollbar Rounding", 9.0f, 0.0f..12.0f, 0.1f)
	@Tab(ROUNDING_TAB) val grabRounding by setting("Grab Rounding", 4.6f, 0.0f..12.0f, 0.1f)
	@Tab(ROUNDING_TAB) val tabRounding by setting("Tab Rounding", 4.6f, 0.0f..12.0f, 0.1f)
	@Tab(ROUNDING_TAB) val curveTessellationTol by setting("Curve Tessellation Tol", 1.25f, 0.1f..10.0f, 0.05f)

	// Font
	@Tab(FONT_TAB) val fontScale by setting("Font Scale", 1.0, 0.5..2.0, 0.1)

	// Colors
	enum class ThemePreset(override val displayName: String) : NamedEnum {
		CUSTOM("Custom"),
		LAMBDA_VIOLET("Lambda Violet"),
		MIDNIGHT_BLUE("Midnight Blue"),
		NORDIC_FROST("Nordic Frost"),
		CYBERPUNK_NEON("Cyberpunk Neon"),
		DRACULA("Dracula"),
		EMERALD_OBSIDIAN("Emerald Obsidian"),
		MONOKAI_GOLD("Monokai Gold")
	}

	@Tab(COLORS_TAB) val themePreset by setting("Theme Preset", ThemePreset.LAMBDA_VIOLET, "Predefined theme palettes for the ClickGUI")
		.onValueChange { _, preset -> applyTheme(preset) }

	@Tab(COLORS_TAB) var primaryColor by setting("Primary Color", Color(130, 200, 255))
	@Tab(COLORS_TAB) var secondaryColor by setting("Secondary Color", Color(225, 130, 225))

	@Tab(COLORS_TAB) var text by setting("Text", Color(255, 255, 255, 255))
	@Tab(COLORS_TAB) var textDisabled by setting("Text Disabled", Color(128, 128, 128, 255))
	@Tab(COLORS_TAB) var windowBg by setting("Window Background", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var childBg by setting("Child Background", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var popupBg by setting("Popup Background", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var border by setting("Border", Color(130, 12, 60, 240))
	@Tab(COLORS_TAB) var borderShadow by setting("Border Shadow", Color(51, 0, 21, 240))
	@Tab(COLORS_TAB) var frameBg by setting("Frame Background", Color(171, 32, 93, 102))
	@Tab(COLORS_TAB) var frameBgHovered by setting("Frame Background Hovered", Color(214, 45, 119, 102))
	@Tab(COLORS_TAB) var frameBgActive by setting("Frame Background Active", Color(255, 50, 140, 102))
	@Tab(COLORS_TAB) var titleBg by setting("Title Background", Color(125, 0, 50, 240))
	@Tab(COLORS_TAB) var titleBgActive by setting("Title Background Active", Color(162, 0, 68, 240))
	@Tab(COLORS_TAB) var titleBgCollapsed by setting("Title Background Collapsed", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var menuBarBg by setting("MenuBar Background", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var scrollbarBg by setting("Scrollbar Background", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var scrollbarGrab by setting("Scrollbar Grab", Color(159, 30, 83, 240))
	@Tab(COLORS_TAB) var scrollbarGrabHovered by setting("Scrollbar Grab Hovered", Color(198, 40, 105, 240))
	@Tab(COLORS_TAB) var scrollbarGrabActive by setting("Scrollbar Grab Active", Color(235, 49, 126, 240))
	@Tab(COLORS_TAB) var checkMark by setting("Check Mark", Color(255, 64, 148, 220))
	@Tab(COLORS_TAB) var sliderGrab by setting("Slider Grab", Color(207, 46, 117, 200))
	@Tab(COLORS_TAB) var sliderGrabActive by setting("Slider Grab Active", Color(241, 67, 143, 200))
	@Tab(COLORS_TAB) var button by setting("Button", Color(171, 32, 93, 102))
	@Tab(COLORS_TAB) var buttonHovered by setting("Button Hovered", Color(214, 45, 119, 102))
	@Tab(COLORS_TAB) var buttonActive by setting("Button Active", Color(255, 50, 140, 102))
	@Tab(COLORS_TAB) var header by setting("Header", Color(192, 30, 94, 115))
	@Tab(COLORS_TAB) var headerHovered by setting("Header Hovered", Color(255, 59, 136, 115))
	@Tab(COLORS_TAB) var headerActive by setting("Header Active", Color(202, 36, 101, 115))
	@Tab(COLORS_TAB) var separator by setting("Separator", Color(107, 0, 47, 128))
	@Tab(COLORS_TAB) var separatorHovered by setting("Separator Hovered", Color(146, 0, 64, 128))
	@Tab(COLORS_TAB) var separatorActive by setting("Separator Active", Color(186, 0, 82, 128))
	@Tab(COLORS_TAB) var resizeGrip by setting("Resize Grip", Color(214, 45, 119, 102))
	@Tab(COLORS_TAB) var resizeGripHovered by setting("Resize Grip Hovered", Color(214, 45, 119, 102))
	@Tab(COLORS_TAB) var resizeGripActive by setting("Resize Grip Active", Color(214, 45, 119, 102))
	@Tab(COLORS_TAB) var tab by setting("Tab", Color(121, 21, 65, 140))
	@Tab(COLORS_TAB) var tabHovered by setting("Tab Hovered", Color(169, 34, 94, 140))
	@Tab(COLORS_TAB) var tabActive by setting("Tab Active", Color(209, 34, 112, 140))
	@Tab(COLORS_TAB) var tabUnfocused by setting("Tab Unfocused", Color(121, 21, 65, 120))
	@Tab(COLORS_TAB) var tabUnfocusedActive by setting("Tab Unfocused Active", Color(196, 36, 107, 120))
	@Tab(COLORS_TAB) var dockingPreview by setting("Docking Preview", Color(208, 47, 117, 102))
	@Tab(COLORS_TAB) var dockingEmptyBg by setting("Docking Empty Background", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var plotLines by setting("Plot Lines", Color(178, 36, 95, 240))
	@Tab(COLORS_TAB) var plotLinesHovered by setting("Plot Lines Hovered", Color(209, 40, 110, 240))
	@Tab(COLORS_TAB) var plotHistogram by setting("Plot Histogram", Color(192, 32, 91, 255))
	@Tab(COLORS_TAB) var plotHistogramHovered by setting("Plot Histogram Hovered", Color(226, 38, 108, 255))
	@Tab(COLORS_TAB) var tableHeaderBg by setting("Table Header Background", Color(75, 0, 31, 240))
	@Tab(COLORS_TAB) var tableBorderStrong by setting("Table Border Strong", Color(88, 0, 36, 240))
	@Tab(COLORS_TAB) var tableBorderLight by setting("Table Border Light", Color(67, 0, 28, 240))
	@Tab(COLORS_TAB) var tableRowBg by setting("Table Row Background", Color(35, 0, 14, 240))
	@Tab(COLORS_TAB) var tableRowBgAlt by setting("Table Row Background Alt", Color(242, 140, 182, 240))
	@Tab(COLORS_TAB) var textSelectedBg by setting("Text Selected Background", Color(218, 54, 121, 240))
	@Tab(COLORS_TAB) var dragDropTarget by setting("Drag Drop Target", Color(218, 54, 121, 240))
	@Tab(COLORS_TAB) var navHighlight by setting("Nav Highlight", Color(218, 54, 121, 240))
	@Tab(COLORS_TAB) var navWindowingHighlight by setting("Nav Windowing Highlight", Color(242, 140, 182, 240))
	@Tab(COLORS_TAB) var navWindowingDimBg by setting("Nav Windowing Dim Background", Color(242, 140, 182, 240))
	@Tab(COLORS_TAB) var modalWindowDimBg by setting("Modal Window Dim Background", Color(35, 0, 14, 90))

	val expandedModules = mutableSetOf<String>()
	fun isModuleExpanded(name: String) = expandedModules.contains(name)
	fun toggleModuleExpanded(name: String) {
		if (expandedModules.contains(name)) expandedModules.remove(name)
		else expandedModules.add(name)
	}
	fun collapseAllModules() = expandedModules.clear()
	fun expandAllModules(tag: ModuleTag) = ModuleRegistry.modules.filter { it.tag == tag }.forEach { expandedModules.add(it.name) }

	private val categoryFilters = mutableMapOf<String, ImString>()
	fun getCategoryFilter(name: String) = categoryFilters.getOrPut(name) { ImString(64) }

	fun tileWindows() {
		val startX = 20f
		val startY = MenuBar.height + 15f
		val padding = 14f
		val scale = currentScale
		val windowWidth = 200f * scale
		val displayWidth = DearImGui.io.displaySize.x
		val cols = max(1, ((displayWidth - startX * 2) / (windowWidth + padding)).toInt())

		var col = 0
		var row = 0
		val tags = if (developerMode) shownTags + ModuleTag.DEBUG else shownTags
		tags.forEach { tag ->
			val x = startX + col * (windowWidth + padding)
			val y = startY + row * (280f * scale)
			pendingPositions[tag.name] = x to y
			col++
			if (col >= cols) {
				col = 0
				row++
			}
		}
	}

	fun resetWindowPositions() {
		pendingPositions.clear()
		pendingSizes.clear()
		val startX = 20f
		val baseY = MenuBar.height + 12f
		val padding = 14f
		val scale = currentScale
		val windowWidth = 200f * scale
		val displayWidth = DearImGui.io.displaySize.x
		val cols = max(1, ((displayWidth - startX * 2) / (windowWidth + padding)).toInt())

		var col = 0
		var row = 0
		val tags = if (developerMode) shownTags + ModuleTag.DEBUG else shownTags
		tags.forEach { tag ->
			val x = startX + col * (windowWidth + padding)
			val y = baseY + row * (280f * scale)
			pendingPositions[tag.name] = x to y
			col++
			if (col >= cols) {
				col = 0
				row++
			}
		}
	}

	fun applyTheme(preset: ThemePreset) {
		when (preset) {
			ThemePreset.CUSTOM -> {}
			ThemePreset.LAMBDA_VIOLET -> {
				primaryColor = Color(130, 200, 255)
				secondaryColor = Color(225, 130, 225)
				windowBg = Color(35, 0, 14, 240)
				childBg = Color(35, 0, 14, 240)
				popupBg = Color(35, 0, 14, 240)
				border = Color(130, 12, 60, 240)
				borderShadow = Color(51, 0, 21, 240)
				frameBg = Color(171, 32, 93, 102)
				frameBgHovered = Color(214, 45, 119, 102)
				frameBgActive = Color(255, 50, 140, 102)
				titleBg = Color(125, 0, 50, 240)
				titleBgActive = Color(162, 0, 68, 240)
				titleBgCollapsed = Color(35, 0, 14, 240)
				menuBarBg = Color(35, 0, 14, 240)
				button = Color(171, 32, 93, 102)
				buttonHovered = Color(214, 45, 119, 102)
				buttonActive = Color(255, 50, 140, 102)
				header = Color(192, 30, 94, 115)
				headerHovered = Color(255, 59, 136, 115)
				headerActive = Color(202, 36, 101, 115)
				checkMark = Color(255, 64, 148, 220)
				sliderGrab = Color(207, 46, 117, 200)
				sliderGrabActive = Color(241, 67, 143, 200)
				scrollbarBg = Color(35, 0, 14, 240)
				scrollbarGrab = Color(159, 30, 83, 240)
				scrollbarGrabHovered = Color(198, 40, 105, 240)
				scrollbarGrabActive = Color(235, 49, 126, 240)
				separator = Color(107, 0, 47, 128)
				separatorHovered = Color(146, 0, 64, 128)
				separatorActive = Color(186, 0, 82, 128)
			}
			ThemePreset.MIDNIGHT_BLUE -> {
				primaryColor = Color(56, 189, 248)
				secondaryColor = Color(129, 140, 248)
				windowBg = Color(15, 20, 28, 245)
				childBg = Color(15, 20, 28, 245)
				popupBg = Color(18, 24, 36, 245)
				border = Color(40, 60, 90, 240)
				borderShadow = Color(10, 14, 20, 240)
				frameBg = Color(30, 45, 70, 140)
				frameBgHovered = Color(40, 65, 105, 160)
				frameBgActive = Color(56, 189, 248, 140)
				titleBg = Color(20, 30, 48, 245)
				titleBgActive = Color(30, 50, 85, 245)
				titleBgCollapsed = Color(15, 20, 28, 245)
				menuBarBg = Color(12, 16, 24, 245)
				button = Color(35, 55, 88, 140)
				buttonHovered = Color(48, 75, 120, 160)
				buttonActive = Color(56, 189, 248, 160)
				header = Color(32, 50, 80, 160)
				headerHovered = Color(45, 70, 115, 180)
				headerActive = Color(56, 189, 248, 180)
				checkMark = Color(56, 189, 248, 240)
				sliderGrab = Color(56, 189, 248, 200)
				sliderGrabActive = Color(125, 211, 252, 240)
				scrollbarBg = Color(15, 20, 28, 245)
				scrollbarGrab = Color(35, 55, 88, 240)
				scrollbarGrabHovered = Color(50, 75, 120, 240)
				scrollbarGrabActive = Color(56, 189, 248, 240)
				separator = Color(30, 45, 70, 180)
				separatorHovered = Color(45, 70, 110, 180)
				separatorActive = Color(56, 189, 248, 180)
			}
			ThemePreset.NORDIC_FROST -> {
				primaryColor = Color(0, 210, 255)
				secondaryColor = Color(136, 192, 208)
				windowBg = Color(24, 28, 36, 245)
				childBg = Color(24, 28, 36, 245)
				popupBg = Color(30, 35, 46, 245)
				border = Color(55, 75, 95, 240)
				borderShadow = Color(12, 16, 20, 240)
				frameBg = Color(40, 52, 68, 140)
				frameBgHovered = Color(55, 72, 95, 160)
				frameBgActive = Color(0, 210, 255, 140)
				titleBg = Color(30, 40, 52, 245)
				titleBgActive = Color(42, 60, 80, 245)
				titleBgCollapsed = Color(24, 28, 36, 245)
				menuBarBg = Color(18, 22, 28, 245)
				button = Color(44, 58, 76, 140)
				buttonHovered = Color(60, 80, 105, 160)
				buttonActive = Color(0, 210, 255, 160)
				header = Color(40, 56, 75, 160)
				headerHovered = Color(55, 78, 105, 180)
				headerActive = Color(0, 210, 255, 180)
				checkMark = Color(0, 210, 255, 240)
				sliderGrab = Color(0, 210, 255, 200)
				sliderGrabActive = Color(136, 235, 255, 240)
				scrollbarBg = Color(24, 28, 36, 245)
				scrollbarGrab = Color(44, 58, 76, 240)
				scrollbarGrabHovered = Color(60, 80, 105, 240)
				scrollbarGrabActive = Color(0, 210, 255, 240)
				separator = Color(40, 55, 72, 180)
				separatorHovered = Color(55, 75, 100, 180)
				separatorActive = Color(0, 210, 255, 180)
			}
			ThemePreset.CYBERPUNK_NEON -> {
				primaryColor = Color(255, 42, 133)
				secondaryColor = Color(0, 240, 255)
				windowBg = Color(12, 12, 16, 245)
				childBg = Color(12, 12, 16, 245)
				popupBg = Color(18, 18, 24, 245)
				border = Color(255, 42, 133, 180)
				borderShadow = Color(0, 0, 0, 240)
				frameBg = Color(35, 20, 30, 150)
				frameBgHovered = Color(60, 25, 45, 180)
				frameBgActive = Color(255, 42, 133, 160)
				titleBg = Color(25, 14, 22, 245)
				titleBgActive = Color(180, 20, 90, 245)
				titleBgCollapsed = Color(12, 12, 16, 245)
				menuBarBg = Color(8, 8, 12, 245)
				button = Color(45, 20, 35, 150)
				buttonHovered = Color(80, 25, 55, 180)
				buttonActive = Color(255, 42, 133, 180)
				header = Color(60, 20, 42, 160)
				headerHovered = Color(110, 25, 70, 180)
				headerActive = Color(255, 42, 133, 180)
				checkMark = Color(0, 240, 255, 255)
				sliderGrab = Color(0, 240, 255, 220)
				sliderGrabActive = Color(255, 42, 133, 240)
				scrollbarBg = Color(12, 12, 16, 245)
				scrollbarGrab = Color(60, 20, 42, 240)
				scrollbarGrabHovered = Color(100, 25, 65, 240)
				scrollbarGrabActive = Color(255, 42, 133, 240)
				separator = Color(60, 20, 40, 180)
				separatorHovered = Color(100, 25, 65, 180)
				separatorActive = Color(0, 240, 255, 180)
			}
			ThemePreset.DRACULA -> {
				primaryColor = Color(189, 147, 249)
				secondaryColor = Color(255, 121, 198)
				windowBg = Color(40, 42, 54, 245)
				childBg = Color(40, 42, 54, 245)
				popupBg = Color(48, 50, 64, 245)
				border = Color(98, 114, 164, 200)
				borderShadow = Color(20, 20, 28, 240)
				frameBg = Color(68, 71, 90, 150)
				frameBgHovered = Color(98, 114, 164, 180)
				frameBgActive = Color(189, 147, 249, 150)
				titleBg = Color(33, 34, 44, 245)
				titleBgActive = Color(68, 71, 90, 245)
				titleBgCollapsed = Color(33, 34, 44, 245)
				menuBarBg = Color(28, 29, 38, 245)
				button = Color(68, 71, 90, 150)
				buttonHovered = Color(98, 114, 164, 180)
				buttonActive = Color(189, 147, 249, 180)
				header = Color(68, 71, 90, 160)
				headerHovered = Color(98, 114, 164, 180)
				headerActive = Color(189, 147, 249, 180)
				checkMark = Color(80, 250, 123, 240)
				sliderGrab = Color(189, 147, 249, 220)
				sliderGrabActive = Color(255, 121, 198, 240)
				scrollbarBg = Color(40, 42, 54, 245)
				scrollbarGrab = Color(68, 71, 90, 240)
				scrollbarGrabHovered = Color(98, 114, 164, 240)
				scrollbarGrabActive = Color(189, 147, 249, 240)
				separator = Color(68, 71, 90, 180)
				separatorHovered = Color(98, 114, 164, 180)
				separatorActive = Color(189, 147, 249, 180)
			}
			ThemePreset.EMERALD_OBSIDIAN -> {
				primaryColor = Color(16, 185, 129)
				secondaryColor = Color(52, 211, 153)
				windowBg = Color(15, 23, 19, 245)
				childBg = Color(15, 23, 19, 245)
				popupBg = Color(20, 30, 25, 245)
				border = Color(30, 70, 50, 220)
				borderShadow = Color(8, 14, 10, 240)
				frameBg = Color(25, 50, 38, 140)
				frameBgHovered = Color(35, 75, 55, 160)
				frameBgActive = Color(16, 185, 129, 150)
				titleBg = Color(18, 32, 25, 245)
				titleBgActive = Color(25, 60, 42, 245)
				titleBgCollapsed = Color(15, 23, 19, 245)
				menuBarBg = Color(10, 16, 12, 245)
				button = Color(28, 58, 42, 140)
				buttonHovered = Color(38, 80, 58, 160)
				buttonActive = Color(16, 185, 129, 170)
				header = Color(25, 55, 40, 160)
				headerHovered = Color(35, 80, 56, 180)
				headerActive = Color(16, 185, 129, 180)
				checkMark = Color(16, 185, 129, 240)
				sliderGrab = Color(16, 185, 129, 220)
				sliderGrabActive = Color(52, 211, 153, 240)
				scrollbarBg = Color(15, 23, 19, 245)
				scrollbarGrab = Color(28, 58, 42, 240)
				scrollbarGrabHovered = Color(38, 80, 58, 240)
				scrollbarGrabActive = Color(16, 185, 129, 240)
				separator = Color(28, 55, 40, 180)
				separatorHovered = Color(38, 75, 55, 180)
				separatorActive = Color(16, 185, 129, 180)
			}
			ThemePreset.MONOKAI_GOLD -> {
				primaryColor = Color(230, 219, 116)
				secondaryColor = Color(253, 151, 31)
				windowBg = Color(39, 40, 34, 245)
				childBg = Color(39, 40, 34, 245)
				popupBg = Color(48, 49, 42, 245)
				border = Color(90, 85, 65, 220)
				borderShadow = Color(20, 20, 16, 240)
				frameBg = Color(60, 58, 46, 140)
				frameBgHovered = Color(85, 80, 60, 160)
				frameBgActive = Color(230, 219, 116, 140)
				titleBg = Color(30, 31, 26, 245)
				titleBgActive = Color(65, 62, 48, 245)
				titleBgCollapsed = Color(30, 31, 26, 245)
				menuBarBg = Color(24, 25, 20, 245)
				button = Color(60, 58, 46, 140)
				buttonHovered = Color(85, 80, 60, 160)
				buttonActive = Color(230, 219, 116, 160)
				header = Color(60, 58, 46, 160)
				headerHovered = Color(85, 80, 60, 180)
				headerActive = Color(230, 219, 116, 180)
				checkMark = Color(166, 226, 46, 240)
				sliderGrab = Color(230, 219, 116, 220)
				sliderGrabActive = Color(253, 151, 31, 240)
				scrollbarBg = Color(39, 40, 34, 245)
				scrollbarGrab = Color(60, 58, 46, 240)
				scrollbarGrabHovered = Color(85, 80, 60, 240)
				scrollbarGrabActive = Color(230, 219, 116, 240)
				separator = Color(60, 58, 46, 180)
				separatorHovered = Color(85, 80, 60, 180)
				separatorActive = Color(230, 219, 116, 180)
			}
		}
	}

	fun syncThemeToAccent(accent: Color) {
		primaryColor = accent
		val r = accent.red
		val g = accent.green
		val b = accent.blue
		header = Color(r, g, b, 120)
		headerHovered = Color((r * 1.15f).coerceAtMost(255f).toInt(), (g * 1.15f).coerceAtMost(255f).toInt(), (b * 1.15f).coerceAtMost(255f).toInt(), 160)
		headerActive = Color((r * 0.9f).toInt(), (g * 0.9f).toInt(), (b * 0.9f).toInt(), 180)
		buttonActive = Color(r, g, b, 150)
		checkMark = Color(r, g, b, 230)
		sliderGrab = Color(r, g, b, 200)
		sliderGrabActive = Color((r * 1.2f).coerceAtMost(255f).toInt(), (g * 1.2f).coerceAtMost(255f).toInt(), (b * 1.2f).coerceAtMost(255f).toInt(), 230)
		scrollbarGrabActive = Color(r, g, b, 230)
		border = Color((r * 0.6f).toInt(), (g * 0.6f).toInt(), (b * 0.6f).toInt(), 200)
	}
	init {
		listenUnsafe<GuiEvent.NewImguiFrame> {
			if (!open) return@listenUnsafe

			buildLayout {
				buildMenuBar()
				val vp = ImGui.getMainViewport()
				SnapHandler.beginFrame(vp.sizeX, vp.sizeY, io.fontGlobalScale)

				val mouseDown = io.mouseDown[0]
				val mouseReleasedThisFrame = !mouseDown && mouseWasDown
				mouseWasDown = mouseDown

				if (mouseReleasedThisFrame) {
					activeDragWindowName = null
					activeResizeWindowName?.let { pendingSizes.remove(it) }
					activeResizeWindowName = null
				}

				pendingPositions.clear()
				snapOverlays.clear()

				if (mouseDown) {
					activeDragWindowName?.let { name ->
						lastBounds[name]?.let { bounds ->
							updateDragAndSnapping(
								name, bounds, dragOffsetX, dragOffsetY, pendingPositions, snapOverlays
							)
							drawDragGrid()
						}
					}
					activeResizeWindowName?.let { name ->
						lastBounds[name]?.let { bounds ->
							updateResizeAndSnapping(name, bounds)
							drawDragGrid()
						}
					}
				}

				val tags = if (developerMode) shownTags + ModuleTag.DEBUG else shownTags
				if (tags.isEmpty()) return@buildLayout

				var nextX = 20f
				val baseY = MenuBar.height + 10f

				tags.forEach { tag ->
					val override = pendingPositions[tag.name]
					if (override != null) {
						ImGui.setNextWindowPos(override.first, override.second)
					} else if (frameCount >= 1) {
						ImGui.setNextWindowPos(nextX, baseY, ImGuiCond.FirstUseEver)
					}

					val scale = currentScale
					ImGui.setNextWindowSize(200f * scale, 0f, ImGuiCond.FirstUseEver)
					pendingSizes[tag.name]?.let { (w, h) ->
						ImGui.setNextWindowSizeConstraints(w, h, w, h)
					} ?: run {
						ImGui.setNextWindowSizeConstraints(185f * scale, 0f, 360f * scale, io.displaySize.y * 0.88f)
					}
					val windowFlags = if (allowWindowCollapse) ImGuiWindowFlags.None else ImGuiWindowFlags.NoCollapse

					val categoryModules = ModuleRegistry.modules.filter { it.tag == tag && it.showInClickGui.value }
					val enabledCount = categoryModules.count { it.isEnabled }
					val totalCount = categoryModules.size

					val windowTitle = if (showCategoryCounts) {
						"${tag.name}  [$enabledCount/$totalCount]###Category-${tag.name}"
					} else {
						"${tag.name}###Category-${tag.name}"
					}

					window(windowTitle, flags = windowFlags) {
						// Optional category search filter
						if (categoryWindowSearch) {
							val filter = getCategoryFilter(tag.name)
							withItemWidth(ImGui.getContentRegionAvailX()) {
								ImGui.inputTextWithHint("##cat-filter-${tag.name}", "Filter ${tag.name}...", filter)
							}
							separator()
						}

						val filterText = if (categoryWindowSearch) getCategoryFilter(tag.name).get().trim() else ""
						categoryModules
							.filter { filterText.isEmpty() || it.name.contains(filterText, ignoreCase = true) }
							.forEach { with(ModuleEntry(it)) { buildLayout() } }

						popupContextWindow("##cat-ctx-${tag.name}") {
							menuItem("Tile Windows (Auto-Arrange)") { tileWindows() }
							menuItem("Reset Layout") { resetWindowPositions() }
							separator()
							menuItem("Expand All in ${tag.name}") { expandAllModules(tag) }
							menuItem("Collapse All") { collapseAllModules() }
						}

						snapOverlays[tag.name]?.let { vis ->
							drawSnapLines(vis.snapX, vis.kindX, vis.snapY, vis.kindY)
						}

						val rect = RectF(windowPos.x, windowPos.y, windowSize.x, windowSize.y)
						if (mouseDown) claimInteraction(tag.name, rect)

						SnapHandler.registerElement(tag.name, rect)
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
	}

	private fun ImGuiBuilder.claimInteraction(name: String, rect: RectF) {
		if (activeDragWindowName != null || activeResizeWindowName != null) return
		val previous = lastBounds[name] ?: return

		val movedX = abs(rect.x - previous.x) > 0.5f
		val movedY = abs(rect.y - previous.y) > 0.5f
		val sizedX = abs(rect.w - previous.w) > 0.5f
		val sizedY = abs(rect.h - previous.h) > 0.5f

		when {
			sizedX || sizedY -> {
				val grip = ImGui.getFontSize() * 1.35f
				val toLeft = io.mousePos.x - rect.left
				val toRight = rect.right - io.mousePos.x
				val toTop = io.mousePos.y - rect.top
				val toBottom = rect.bottom - io.mousePos.y

				activeResizeWindowName = name
				resizeLeftEdge = toLeft <= grip && toLeft < toRight
				resizeRightEdge = toRight <= grip && toRight <= toLeft
				resizeTopEdge = toTop <= grip && toTop < toBottom
				resizeBottomEdge = toBottom <= grip && toBottom <= toTop

				if (sizedX && !resizeLeftEdge && !resizeRightEdge) resizeRightEdge = true
				if (sizedY && !resizeTopEdge && !resizeBottomEdge) resizeBottomEdge = true

				resizeGrabOffsetX = io.mousePos.x - if (resizeLeftEdge) rect.left else rect.right
				resizeGrabOffsetY = io.mousePos.y - if (resizeTopEdge) rect.top else rect.bottom
			}
			movedX || movedY -> {
				activeDragWindowName = name
				dragOffsetX = io.mousePos.x - rect.x
				dragOffsetY = io.mousePos.y - rect.y
			}
		}
	}

	private fun ImGuiBuilder.updateResizeAndSnapping(name: String, bounds: RectF) {
		var x = bounds.x
		var y = bounds.y
		var width = bounds.w
		var height = bounds.h
		var snapX: Float? = null
		var snapY: Float? = null
		var kindX: Guide.Kind? = null
		var kindY: Guide.Kind? = null

		fun snapped(offset: Float, mouse: Float, orientation: Guide.Orientation): Pair<Float, Guide.Kind?> {
			val proposed = mouse - offset
			val snap = SnapHandler.snapEdge(proposed, orientation, name)
			return (snap.pos ?: proposed) to snap.kind
		}

		if (resizeLeftEdge || resizeRightEdge) {
			val (edge, kind) = snapped(resizeGrabOffsetX, io.mousePos.x, Guide.Orientation.Vertical)
			if (resizeLeftEdge) {
				x = edge.coerceAtMost(bounds.right - style.windowMinSize.x)
				width = bounds.right - x
			} else {
				width = (edge - bounds.left).coerceAtLeast(style.windowMinSize.x)
			}
			snapX = edge
			kindX = kind
		}

		if (resizeTopEdge || resizeBottomEdge) {
			val (edge, kind) = snapped(resizeGrabOffsetY, io.mousePos.y, Guide.Orientation.Horizontal)
			if (resizeTopEdge) {
				y = edge.coerceAtMost(bounds.bottom - style.windowMinSize.y)
				height = bounds.bottom - y
			} else {
				height = (edge - bounds.top).coerceAtLeast(style.windowMinSize.y)
			}
			snapY = edge
			kindY = kind
		}

		pendingSizes[name] = width to height
		if (resizeLeftEdge || resizeTopEdge) pendingPositions[name] = x to y
		snapOverlays[name] = SnapHandler.SnapVisual(snapX, snapY, kindX, kindY)
	}

	val Screen?.hasInput: Boolean
		get() = this is ChatScreen ||
				this is SignEditScreen ||
				this is AnvilScreen ||
				this is CommandBlockScreen

	fun toggle() {
		if (open) {
			LambdaScreen.close()
		} else {
			val current = mc.currentScreen
			if (current.hasInput) return
			if (Client.clientSounds) LambdaSound.ModuleOn.play()
			LambdaScreen.parentScreen = if (current is LambdaScreen) null else current
			(current as? OverlayBackgroundScreen)?.onOverlaidByGui()
			mc.setScreen(LambdaScreen)
			open = true
			frameCount = 0
			initialLayoutComplete = false
		}
	}

	fun close() {
		if (Client.clientSounds) LambdaSound.ModuleOff.play()
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
