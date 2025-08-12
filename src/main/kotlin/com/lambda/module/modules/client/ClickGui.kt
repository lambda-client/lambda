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

package com.lambda.module.modules.client

import com.lambda.gui.LambdaScreen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.NamedEnum
import imgui.ImGui
import imgui.flag.ImGuiCol
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen
import net.minecraft.client.gui.screen.ingame.SignEditScreen
import java.awt.Color

object ClickGui : Module(
    name = "ClickGui",
    description = "ImGui",
    tag = ModuleTag.CLIENT,
    defaultKeybind = KeyCode.Y,
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Sizing("Sizing"),
        Rounding("Rounding"),
        Colors("Colors"),
        Font("Font")
    }

    // General
    val alpha by setting("Alpha", 1.0f, 0.0f..1.0f, 0.01f).group(Group.General)
    val disabledAlpha by setting("Disabled Alpha", 0.6f, 0.0f..1.0f, 0.01f).group(Group.General)

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
    val scrollbarSize by setting("Scrollbar Size", 14.0f, 0.0f..30.0f, 0.1f).group(Group.Sizing)
    val grabMinSize by setting("Grab Min Size", 10.0f, 0.0f..30.0f, 0.1f).group(Group.Sizing)
    val windowBorderSize by setting("Window Border Size", 1.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
    val childBorderSize by setting("Child Border Size", 1.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
    val popupBorderSize by setting("Popup Border Size", 1.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
    val frameBorderSize by setting("Frame Border Size", 0.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)
    val tabBorderSize by setting("Tab Border Size", 0.0f, 0.0f..5.0f, 0.1f).group(Group.Sizing)

    // Rounding
    val windowRounding by setting("Window Rounding", 0.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
    val childRounding by setting("Child Rounding", 0.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
    val frameRounding by setting("Frame Rounding", 0.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
    val popupRounding by setting("Popup Rounding", 0.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
    val scrollbarRounding by setting("Scrollbar Rounding", 9.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
    val grabRounding by setting("Grab Rounding", 0.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
    val tabRounding by setting("Tab Rounding", 4.0f, 0.0f..12.0f, 0.1f).group(Group.Rounding)
    val curveTessellationTol by setting("Curve Tessellation Tol", 1.25f, 0.1f..10.0f, 0.05f).group(Group.Rounding)

    // Font
    val fontScale by setting("Font Scale", 1.0, 0.5..2.0, 0.1).group(Group.Font)

    // Colors
    val text by setting("Text", Color(255, 255, 255)).group(Group.Colors)
    val textDisabled by setting("Text Disabled", Color(128, 128, 128)).group(Group.Colors)
    val windowBg by setting("Window Background", Color(15, 15, 15, 240)).group(Group.Colors)
    val childBg by setting("Child Background", Color(0, 0, 0, 0)).group(Group.Colors)
    val popupBg by setting("Popup Background", Color(20, 20, 20, 240)).group(Group.Colors)
    val border by setting("Border", Color(110, 110, 128, 128)).group(Group.Colors)
    val borderShadow by setting("Border Shadow", Color(0, 0, 0, 0)).group(Group.Colors)
    val frameBg by setting("Frame Background", Color(66, 66, 66, 138)).group(Group.Colors)
    val frameBgHovered by setting("Frame Background Hovered", Color(66, 150, 255, 102)).group(Group.Colors)
    val frameBgActive by setting("Frame Background Active", Color(66, 150, 255, 171)).group(Group.Colors)
    val titleBg by setting("Title Background", Color(20, 20, 20, 255)).group(Group.Colors)
    val titleBgActive by setting("Title Background Active", Color(41, 79, 120, 255)).group(Group.Colors)
    val titleBgCollapsed by setting("Title Background Collapsed", Color(0, 0, 0, 130)).group(Group.Colors)
    val menuBarBg by setting("MenuBar Background", Color(36, 36, 36, 255)).group(Group.Colors)
    val scrollbarBg by setting("Scrollbar Background", Color(5, 5, 5, 135)).group(Group.Colors)
    val scrollbarGrab by setting("Scrollbar Grab", Color(79, 79, 79, 255)).group(Group.Colors)
    val scrollbarGrabHovered by setting("Scrollbar Grab Hovered", Color(105, 105, 105, 255)).group(Group.Colors)
    val scrollbarGrabActive by setting("Scrollbar Grab Active", Color(130, 130, 130, 255)).group(Group.Colors)
    val checkMark by setting("Check Mark", Color(66, 150, 255, 255)).group(Group.Colors)
    val sliderGrab by setting("Slider Grab", Color(61, 134, 204, 255)).group(Group.Colors)
    val sliderGrabActive by setting("Slider Grab Active", Color(66, 150, 255, 255)).group(Group.Colors)
    val button by setting("Button", Color(66, 150, 255, 102)).group(Group.Colors)
    val buttonHovered by setting("Button Hovered", Color(66, 150, 255, 255)).group(Group.Colors)
    val buttonActive by setting("Button Active", Color(16, 110, 199, 255)).group(Group.Colors)
    val header by setting("Header", Color(66, 150, 255, 79)).group(Group.Colors)
    val headerHovered by setting("Header Hovered", Color(66, 150, 255, 204)).group(Group.Colors)
    val headerActive by setting("Header Active", Color(66, 150, 255, 255)).group(Group.Colors)
    val separator by setting("Separator", Color(110, 110, 128, 128)).group(Group.Colors)
    val separatorHovered by setting("Separator Hovered", Color(26, 96, 171, 199)).group(Group.Colors)
    val separatorActive by setting("Separator Active", Color(26, 96, 171, 255)).group(Group.Colors)
    val resizeGrip by setting("Resize Grip", Color(66, 150, 255, 51)).group(Group.Colors)
    val resizeGripHovered by setting("Resize Grip Hovered", Color(66, 150, 255, 171)).group(Group.Colors)
    val resizeGripActive by setting("Resize Grip Active", Color(66, 150, 255, 242)).group(Group.Colors)
    val tab by setting("Tab", Color(46, 81, 122, 219)).group(Group.Colors)
    val tabHovered by setting("Tab Hovered", Color(66, 150, 255, 204)).group(Group.Colors)
    val tabActive by setting("Tab Active", Color(51, 105, 166, 255)).group(Group.Colors)
    val tabUnfocused by setting("Tab Unfocused", Color(18, 32, 48, 248)).group(Group.Colors)
    val tabUnfocusedActive by setting("Tab Unfocused Active", Color(36, 71, 110, 255)).group(Group.Colors)
    val dockingPreview by setting("Docking Preview", Color(66, 150, 255, 179)).group(Group.Colors)
    val dockingEmptyBg by setting("Docking Empty Background", Color(51, 51, 51, 255)).group(Group.Colors)
    val plotLines by setting("Plot Lines", Color(156, 156, 156, 255)).group(Group.Colors)
    val plotLinesHovered by setting("Plot Lines Hovered", Color(255, 110, 89, 255)).group(Group.Colors)
    val plotHistogram by setting("Plot Histogram", Color(230, 179, 0, 255)).group(Group.Colors)
    val plotHistogramHovered by setting("Plot Histogram Hovered", Color(255, 153, 0, 255)).group(Group.Colors)
    val tableHeaderBg by setting("Table Header Background", Color(48, 48, 51, 255)).group(Group.Colors)
    val tableBorderStrong by setting("Table Border Strong", Color(84, 84, 92, 255)).group(Group.Colors)
    val tableBorderLight by setting("Table Border Light", Color(68, 68, 74, 255)).group(Group.Colors)
    val tableRowBg by setting("Table Row Background", Color(0, 0, 0, 0)).group(Group.Colors)
    val tableRowBgAlt by setting("Table Row Background Alt", Color(255, 255, 255, 15)).group(Group.Colors)
    val textSelectedBg by setting("Text Selected Background", Color(66, 150, 255, 89)).group(Group.Colors)
    val dragDropTarget by setting("Drag Drop Target", Color(255, 255, 0, 230)).group(Group.Colors)
    val navHighlight by setting("Nav Highlight", Color(66, 150, 255, 255)).group(Group.Colors)
    val navWindowingHighlight by setting("Nav Windowing Highlight", Color(255, 255, 255, 179)).group(Group.Colors)
    val navWindowingDimBg by setting("Nav Windowing Dim Background", Color(204, 204, 204, 51)).group(Group.Colors)
    val modalWindowDimBg by setting("Modal Window Dim Background", Color(20, 20, 20, 89)).group(Group.Colors)


    val Screen?.hasInput: Boolean
        get() = this is ChatScreen ||
                this is SignEditScreen ||
                this is AnvilScreen ||
                this is CommandBlockScreen

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

    init {
        onEnable {
            // When there is a screen active, we don't want to replace the screen because it will interfere with the
            // game.
            if (!mc.currentScreen.hasInput) {
                mc.setScreen(LambdaScreen)
            }
        }
    }
}