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

import com.lambda.Lambda.mc
import com.lambda.gui.LambdaScreen
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.hud.ManagerDebugLoggers.maxLogEntries
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import java.awt.Color
import java.util.*

class DebugLogger(
    val name: String
) {
    private val autoScroll = ImBoolean(true)
    private val wrapText = ImBoolean(true)
    private val showDebug = ImBoolean(true)
    private val showSuccess = ImBoolean(true)
    private val showWarning = ImBoolean(true)
    private val showError = ImBoolean(true)
    private val showSystem = ImBoolean(true)
    private val backgroundAlpha = ImFloat(0.3f)

    val logs = LinkedList<LogEntry>()

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

    fun ImGuiBuilder.buildLayout() {
        ImGui.setNextWindowSizeConstraints(300f, 400f, windowViewport.workSizeX, windowViewport.workSizeY)
        ImGui.setNextWindowBgAlpha(backgroundAlpha.get())
        window(name, flags = ImGuiWindowFlags.NoCollapse) {
            val noScroll = if (autoScroll.get()) ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse else 0
            if (mc.currentScreen == LambdaScreen) {
                checkbox("Auto-Scroll", autoScroll)
                sameLine()
                checkbox("Warp Text", wrapText)
                sameLine()
                checkbox("Show Debug", showDebug)
                checkbox("Show Success", showSuccess)
                sameLine()
                checkbox("Show Warning", showWarning)
                sameLine()
                checkbox("Show Error", showError)
                checkbox("Show System", showSystem)
                slider("Background Alpha", backgroundAlpha, 0.0f, 1.0f)
                button("Clear") { clear() }
            }
            child("Log Content", extraFlags = noScroll) {
                if (wrapText.get()) ImGui.pushTextWrapPos()

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

                if (wrapText.get()) ImGui.popTextWrapPos()

                if (autoScroll.get()) {
                    ImGui.setScrollHereY(1f)
                }
            }
        }
    }

    fun shouldDisplay(logEntry: LogEntry) =
        when (logEntry.type) {
            LogType.Debug -> showDebug.get()
            LogType.Success -> showSuccess.get()
            LogType.Warning -> showWarning.get()
            LogType.Error -> showError.get()
            LogType.System -> showSystem.get()
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