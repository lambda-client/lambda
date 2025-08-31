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

package com.lambda.interaction.request

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import java.awt.Color
import java.util.*

abstract class DebugLogger(
    name: String,
    description: String
) : HudModule(
    name,
    description,
    ModuleTag.HUD,
    customWindow = true
) {
    private val logs = LinkedList<LogEntry>()

    private val autoScroll by setting("Auto-Scroll", true, "Automatically scrolls to the bottom of the log")
    private val wrapText by setting("Wrap Text", false, "Wraps the text to the next line if it gets too long")
    private val showDebug by setting("Show Debug", true, "Shows debug logs")
    private val showSuccess by setting("Show Success", true, "Shows success logs")
    private val showWarning by setting("Show Warning", true, "Shows warning logs")
    private val showError by setting("Show Errors", true, "Shows error logs")
    private val maxLogEntries by setting("Max Log Entries", 100, 1..1000, 1, "Maximum amount of entries in the log")
        .onValueChange { from, to ->
            if (to < from) {
                while(logs.size > to) {
                    logs.removeFirst()
                }
            }
        }
    private val backgroundAlpha by setting("Background Alpha", 0.3f, 0f..1f, 0.01f, "Sets the opacity for the elements background")

    private fun log(message: String, logColor: LogType) {
        logs.add(LogEntry(message, logColor))
        if (logs.size > maxLogEntries) {
            logs.removeFirst()
        }
    }

    fun debug(message: String) = log(message, LogType.Debug)
    fun success(message: String) = log(message, LogType.Success)
    fun warning(message: String) = log(message, LogType.Warning)
    fun error(message: String) = log(message, LogType.Error)
    fun system(message: String) = log(message, LogType.System)

    override fun ImGuiBuilder.buildLayout() {
        ImGui.setNextWindowSizeConstraints(300f, 400f, windowViewport.workSizeX, windowViewport.workSizeY)
        ImGui.setNextWindowBgAlpha(backgroundAlpha)
        window(name, flags = ImGuiWindowFlags.NoCollapse) {
            val noScroll = if (autoScroll) ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse else 0
            button("Clear") { clear() }
            child("Log Content", extraFlags = noScroll) {
                if (wrapText) ImGui.pushTextWrapPos()

                logs.forEach { logEntry ->
                    if (shouldDisplay(logEntry)) {
                        when (val type = logEntry.type) {
                            LogType.Debug -> textColored("[DEBUG]", type.color)
                            LogType.Success -> textColored("[SUCCESS]", type.color)
                            LogType.Warning -> textColored("[WARNING]", type.color)
                            LogType.Error -> textColored("[ERROR]", type.color)
                            LogType.System -> textColored("[SYSTEM]", type.color)
                        }

                        sameLine()
                        textColored(logEntry.message, logEntry.type.color)
                    }
                }

                if (wrapText) ImGui.popTextWrapPos()

                if (autoScroll) {
                    ImGui.setScrollHereY(1f)
                }
            }
        }
    }

    fun shouldDisplay(logEntry: LogEntry) =
        when (logEntry.type) {
            LogType.Debug -> showDebug
            LogType.Success -> showSuccess
            LogType.Warning -> showWarning
            LogType.Error -> showError
            LogType.System -> true
        }

    fun clear() = logs.clear()

    data class LogEntry(
        val message: String,
        val type: LogType
    )

    enum class LogType(val color: Color) {
        Debug(Color(255, 255, 255)),
        Success(Color(70, 255, 70)),
        Warning(Color(255, 255, 70)),
        Error(Color(255, 70, 70)),
        System(Color(70, 70, 255))
    }
}