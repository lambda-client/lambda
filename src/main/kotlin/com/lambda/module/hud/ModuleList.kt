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

package com.lambda.module.hud

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import java.awt.Color

@Suppress("unused")
object ModuleList : HudModule(
    name = "ModuleList",
    tag = ModuleTag.HUD,
) {
	private val onlyBound by setting("Only Bound", false, "Only displays modules with a keybind")
	private val showKeybind by setting("Show Keybind", true, "Display keybind next to a module")
	private val textColor by setting(
		"Text Color",
		Color(80, 210, 255),
		"Module name color"
	)
	private val alignment by setting("Alignment", Alignment.Left, "Align shorter names to the left or right")
	private val sortOrder by setting("Sort Order", SortOrder.LengthLongToShort, "Order the module list")
	private val boundKeybindColor by setting(
		"Bound Keybind Color",
		Color(90, 255, 120),
		"Color for modules with a keybind",
		visibility = { showKeybind }
	)
	private val unboundKeybindColor by setting(
		"Unbound Keybind Color",
		Color(255, 80, 80),
		"Color for modules without a keybind",
		visibility = { showKeybind && !onlyBound }
	)

    init {
        drawSetting.value = false
    }

    override fun ImGuiBuilder.buildLayout() {
        val rows = ModuleRegistry.modules
            .asSequence()
            .filter { it.isEnabled && it.draw }
            .mapNotNull {
                val bound = it.keybind.key != 0 || it.keybind.mouse != -1
                if (onlyBound && !bound) return@mapNotNull null

                val keybindText = if (showKeybind) " [${it.keybind.name}]" else ""
                val fullText = it.name + keybindText

                ModuleRow(
                    name = it.name,
                    keybindText = keybindText,
                    bound = bound,
                    width = ImGui.calcTextSize(fullText).x
                )
            }
            .toList()
            .sorted()

        val maxWidth = rows.maxOfOrNull { it.width } ?: return
        val lineStartX = cursorPosX

        rows.forEach { row ->
            cursorPosX = when (alignment) {
                Alignment.Left -> lineStartX
                Alignment.Right -> lineStartX + maxWidth - row.width
            }

            withStyleColor(ImGuiCol.Text, textColor.toImColor()) {
                text(row.name)
            }

            if (row.keybindText.isNotEmpty()) {
                val keybindColor = if (row.bound) boundKeybindColor else unboundKeybindColor
                sameLine(0f, 0f)
                withStyleColor(ImGuiCol.Text, keybindColor.toImColor()) {
                    text(row.keybindText)
                }
            }
        }
    }

	private fun List<ModuleRow>.sorted() =
		when (sortOrder) {
			SortOrder.Default -> this
			SortOrder.LengthLongToShort -> sortedByDescending { it.width }
			SortOrder.LengthShortToLong -> sortedBy { it.width }
			SortOrder.NameAToZ -> sortedBy { it.name.lowercase() }
			SortOrder.NameZToA -> sortedByDescending { it.name.lowercase() }
		}

	private fun Color.toImColor() = ImColor.rgba(red, green, blue, alpha)

	private data class ModuleRow(
		val name: String,
		val keybindText: String,
		val bound: Boolean,
		val width: Float,
	)

	private enum class Alignment(override val displayName: String) : NamedEnum {
		Left("Left"),
		Right("Right")
	}

	private enum class SortOrder(override val displayName: String) : NamedEnum {
		Default("Default"),
		LengthLongToShort("Length: Long to Short"),
		LengthShortToLong("Length: Short to Long"),
		NameAToZ("Name: A to Z"),
		NameZToA("Name: Z to A")
	}
}
