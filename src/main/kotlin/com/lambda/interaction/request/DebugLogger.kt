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
import imgui.type.ImBoolean
import java.awt.Color
import java.util.*

class DebugLogger(name: String, description: String) : HudModule(
    name, description, ModuleTag.HUD
) {
    private val logs = LinkedList<LogEntry>()
    private var maxLogEntries: Int = 100

    private val autoScroll = ImBoolean(true)
    private val wrapText = ImBoolean(false)
    private val showDebug = ImBoolean(true)
    private val showSuccess = ImBoolean(true)
    private val showWarning = ImBoolean(true)
    private val showError = ImBoolean(true)

    fun log(message: String, logColor: LogType) {
        logs.add(LogEntry(message, logColor))
        if (logs.size > maxLogEntries) {
            logs.removeFirst()
        }
    }

    fun logDebug(message: String) = log(message, LogType.Debug)
    fun logSuccess(message: String) = log(message, LogType.Success)
    fun logWarning(message: String) = log(message, LogType.Warning)
    fun logError(message: String) = log(message, LogType.Error)

    override fun ImGuiBuilder.buildLayout() {
        checkbox("Auto-scroll", autoScroll)
        sameLine()
        checkbox("Debug", showDebug)
        sameLine()
        checkbox("Info", showSuccess)
        sameLine()
        checkbox("Warn", showWarning)
        sameLine()
        checkbox("Error", showError)

        separator()

        child("Log Content") {
            if (wrapText.get()) ImGui.pushTextWrapPos()

            logs.forEach { logEntry ->
                if (shouldDisplay(logEntry)) {
                    when (val type = logEntry.type) {
                        LogType.Debug -> textColored("[DEBUG]", type.color)
                        LogType.Success -> textColored("[SUCCESS]", type.color)
                        LogType.Warning -> textColored("[WARNING]", type.color)
                        LogType.Error -> textColored("[ERROR]", type.color)
                    }

                    sameLine()
                }
            }

            if (wrapText.get()) ImGui.popTextWrapPos()

            if (autoScroll.get()) {
                ImGui.setScrollHereY(1f)
            }
        }

        button("Clear") { clear() }
    }

    fun shouldDisplay(logEntry: LogEntry) =
        when (logEntry.type) {
            LogType.Debug -> showDebug.get()
            LogType.Success -> showSuccess.get()
            LogType.Warning -> showWarning.get()
            LogType.Error -> showError.get()
        }

    fun clear() = logs.clear()


    data class LogEntry(
        val message: String,
        val type: LogType
    )

    enum class LogType(val color: Color) {
        Debug(Color.WHITE),
        Success(Color.GREEN),
        Warning(Color.YELLOW),
        Error(Color.RED)
    }
}