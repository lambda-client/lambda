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

    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Sizing("Sizing"),
        Rounding("Rounding"),
        Colors("Colors"),
        Font("Font")
    }

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

    const val RELATION = 0.02604
    const val BASE_SCALE = 130
    val width = mc.window.monitor!!.currentVideoMode!!.width

    // don't worry, I'm a professional
    // linear interpolation :3
    val defaultScale = (RELATION * width + BASE_SCALE).toInt()

    // General
    internal val scaleSetting by setting("Scale", defaultScale, 50..300, 1, unit = "%").group(Group.General)
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
    @JvmStatic val setLambdaWindowTitle by setting("Set Lambda Window Title", true).onValueChange { _, _ -> mc.updateWindowTitle() }.group(Group.General)
    val lambdaTitleAppendixName by setting("Append Username", true) { setLambdaWindowTitle }.onValueChange { _, _ -> mc.updateWindowTitle() }.group(Group.General)

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

                val tags = if (developerMode) shownTags + ModuleTag.DEBUG else shownTags
                if (tags.isEmpty()) return@buildLayout

                var nextX = mc.window.width / 2.6f // FixMe: hardcoded to fit the combat tag at the most significant position
                val baseY = MenuBar.height + 10f

                tags.forEach { tag ->
                    // FixMe:
                    //  Ok so, ImGui has many different conditions and after having tried for multiple hours
                    //  I could not get either ImGuiCond.Appearing or ImGuiCond.FirstEverUse to work correctly
                    //  for this use case so for the time being we will leave the positions fixed. Too bad!
                    ImGui.setNextWindowPos(nextX, baseY)

                    // FixMe:
                    //  Due to the auto resize of windows, if a tag has no module names that is at least the
                    //  same length as the tag name, the title of the window will clip out the window box.
                    //  For the time being I have removed the ability to collapse the windows so the titles
                    //  have more space lol.
                    window(tag.name, flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoCollapse) {
                        ModuleRegistry.modules
                            .filter { it.tag == tag }
                            .forEach { with(ModuleEntry(it)) { buildLayout() } }

                        nextX += windowContentRegionMaxX + 20f // hard coded offset, need to find a get to get the outer position of the window
                    }
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
