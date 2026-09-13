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

import com.lambda.gui.Layout
import com.lambda.gui.components.SettingsWidget.buildConfigSettingsContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.lambda.imgui.ImGui
import com.lambda.imgui.ImVec2
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.imgui.flag.ImGuiHoveredFlags
import com.lambda.imgui.flag.ImGuiMouseButton
import com.lambda.imgui.flag.ImGuiPopupFlags
import com.lambda.imgui.flag.ImGuiStyleVar
import com.lambda.module.Module

class ModuleEntry(val module: Module) : Layout {
    override fun ImGuiBuilder.buildLayout() {
        val isEnabled = module.isEnabled
        val isExpanded = ClickGuiLayout.isModuleExpanded(module.name)
        val primary = ClickGuiLayout.primaryColor
        val scale = ClickGuiLayout.currentScale

        val rowStartY = cursorPosY
        val textHeight = ImGui.getTextLineHeight()
        val rowHeight = textHeight + style.framePadding.y * 2f

        // Visual highlight / accent bar for enabled modules
        if (isEnabled && ClickGuiLayout.highlightEnabledModules) {
            val minX = ImGui.getWindowPosX() + style.windowPadding.x * 0.4f
            val maxX = ImGui.getWindowPosX() + ImGui.getWindowWidth() - style.windowPadding.x * 0.4f
            val minY = ImGui.getWindowPosY() + rowStartY - ImGui.getScrollY()
            val maxY = minY + rowHeight

            // Soft tint background
            val accentCol = ImColor.rgba(primary.red, primary.green, primary.blue, 25)
            windowDrawList.addRectFilled(minX, minY, maxX, maxY, accentCol, style.frameRounding)

            // Distinct left accent bar
            val barWidth = 2.5f * scale
            val barCol = ImColor.rgba(primary.red, primary.green, primary.blue, 240)
            windowDrawList.addRectFilled(minX, minY, minX + barWidth, maxY, barCol, style.frameRounding)
        }

        // Invisible selectable covering the entire row
        selectable("##sel-${module.name}", selected = false, 0, ImVec2(0f, rowHeight)) {
            module.toggle()
        }
        val isRowHovered = ImGui.isItemHovered()
        lambdaTooltip(module.description)

        // Right-click handling: toggle inline settings expansion inside the category window
        onItemHover(ImGuiHoveredFlags.AllowWhenBlockedByPopup) {
            if (isMouseClicked(ImGuiMouseButton.Right)) {
                ClickGuiLayout.toggleModuleExpanded(module.name)
            }
        }

        // Measure right-side elements
        val bind = module.keybind
        val hasBind = (bind.isKeyBind || bind.isMouseBind) && ClickGuiLayout.showKeybindBadges
        val bindText = if (hasBind) "[${bind.name}]" else ""
        val bindWidth = if (hasBind) ImGui.calcTextSize(bindText).x + 6f else 0f
        val indicatorWidth = 14f * scale

        val winLeft = ImGui.getWindowPosX()
        val winWidth = ImGui.getWindowWidth()
        val rightEdge = winLeft + winWidth - style.windowPadding.x - 4f

        // Draw keybind badge if present
        if (hasBind) {
            val bindX = rightEdge - indicatorWidth - bindWidth
            val textY = ImGui.getWindowPosY() + rowStartY + style.framePadding.y - ImGui.getScrollY()
            windowDrawList.addText(bindX, textY, ImColor.rgba(255, 205, 75, 230), bindText)
        }

        // Draw expander indicator (vector-drawn, never broken or missing glyph!)
        val triCenterY = ImGui.getWindowPosY() + rowStartY + rowHeight * 0.5f - ImGui.getScrollY()
        val triCenterX = rightEdge - 5f * scale
        val triSize = 3.5f * scale

        if (isExpanded) {
            // Downward pointing arrow for expanded
            windowDrawList.addTriangleFilled(
                triCenterX - triSize, triCenterY - triSize * 0.5f,
                triCenterX + triSize, triCenterY - triSize * 0.5f,
                triCenterX, triCenterY + triSize * 0.7f,
                ImColor.rgba(primary.red, primary.green, primary.blue, 220)
            )
        } else if (isRowHovered) {
            // Right-pointing subtle arrow on hover to indicate settings exist
            windowDrawList.addTriangleFilled(
                triCenterX - triSize * 0.5f, triCenterY - triSize,
                triCenterX - triSize * 0.5f, triCenterY + triSize,
                triCenterX + triSize * 0.7f, triCenterY,
                ImColor.rgba(200, 200, 200, 160)
            )
        }

        // Draw Module Name (clipped if needed to prevent overlap with badges)
        val nameIndent = if (isEnabled && ClickGuiLayout.highlightEnabledModules) 6f * scale else 2f
        val nameX = winLeft + style.windowPadding.x + nameIndent
        val nameY = ImGui.getWindowPosY() + rowStartY + style.framePadding.y - ImGui.getScrollY()
        val maxNameWidth = (rightEdge - indicatorWidth - bindWidth - nameX - 4f).coerceAtLeast(20f)

        val nameCol = if (isEnabled) {
            ImColor.rgba(primary.red, primary.green, primary.blue, 255)
        } else {
            val c = ClickGuiLayout.text
            ImColor.rgba(c.red, c.green, c.blue, c.alpha)
        }

        // Use clip rect to guarantee text NEVER bleeds into the keybind badge
        windowDrawList.pushClipRect(nameX, nameY - 2f, nameX + maxNameWidth, nameY + textHeight + 2f, true)
        windowDrawList.addText(nameX, nameY, nameCol, module.name)
        windowDrawList.popClipRect()


        // Inline expanded settings view
        if (isExpanded) {
            val childBgCol = ClickGuiLayout.childBg
            withStyleVar(ImGuiStyleVar.ChildRounding, 4f) {
                withStyleColor(ImGuiCol.ChildBg, childBgCol.red / 255f, childBgCol.green / 255f, childBgCol.blue / 255f, childBgCol.alpha / 255f) {
                    indent(8f)
                    buildConfigSettingsContext(module)
                    unindent(8f)
                    separator()
                }
            }
        }
    }
}
