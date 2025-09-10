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
import com.lambda.interaction.request.LogContext.Companion.buildLogContext
import com.lambda.module.hud.ManagerDebugLoggers.maxLogEntries
import com.lambda.util.math.a
import imgui.ImGui
import imgui.flag.ImGuiCol
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

    private fun log(message: String, logColor: LogType, extraContext: List<String?>) {
        if (logs.size + 1 > maxLogEntries) {
            logs.removeFirst()
        }
        logs.add(LogEntry(message, logColor, extraContext.filterNotNull()))
    }

    fun debug(message: String) = log(message, LogType.Debug, emptyList())
    fun debug(message: String, vararg extraContext: String?) = log(message, LogType.Debug, extraContext.toList())
    fun debug(message: String, vararg extraContext: LogContext?) =
        log(message, LogType.Debug, extraContext.filterNotNull().map { buildLogContext(builder = it.getLogContextBuilder()) })
    fun success(message: String) = log(message, LogType.Success, emptyList())
    fun success(message: String, vararg extraContext: String?) = log(message, LogType.Success, extraContext.toList())
    fun success(message: String, vararg extraContext: LogContext?) =
        log(message, LogType.Success, extraContext.filterNotNull().map { buildLogContext(builder = it.getLogContextBuilder()) })
    fun warning(message: String) = log(message, LogType.Warning, emptyList())
    fun warning(message: String, vararg extraContext: String?) = log(message, LogType.Warning, extraContext.toList())
    fun warning(message: String, vararg extraContext: LogContext?) =
        log(message, LogType.Warning, extraContext.filterNotNull().map { buildLogContext(builder = it.getLogContextBuilder()) })
    fun error(message: String) = log(message, LogType.Error, emptyList())
    fun error(message: String, vararg extraContext: String?) = log(message, LogType.Error, extraContext.toList())
    fun error(message: String, vararg extraContext: LogContext?) =
        log(message, LogType.Error, extraContext.filterNotNull().map { buildLogContext(builder = it.getLogContextBuilder()) })
    fun system(message: String) = log(message, LogType.System, emptyList())
    fun system(message: String, vararg extraContext: String?) = log(message, LogType.System, extraContext.toList())
    fun system(message: String, vararg extraContext: LogContext?) =
        log(message, LogType.System, extraContext.filterNotNull().map { buildLogContext(builder = it.getLogContextBuilder()) })

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
                        val type = logEntry.type
                        val (logTypeStr, color) = when (type) {
                            LogType.Debug -> Pair("[DEBUG]", type.color)
                            LogType.Success -> Pair("[SUCCESS]", type.color)
                            LogType.Warning -> Pair("[WARNING]", type.color)
                            LogType.Error -> Pair("[ERROR]", type.color)
                            LogType.System -> Pair("[SYSTEM]", type.color)
                        }

                        val floats = floatArrayOf(0f, 0f, 0f)
                        val (r, g, b) = color.getColorComponents(floats)
                        ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, color.a.toFloat())
                        if (logEntry.type == LogType.System) {
                            text("$logTypeStr ${logEntry.message}")
                        } else {
                            treeNode("$logTypeStr ${logEntry.message}", logEntry.uuid) {
                                logEntry.extraContext
                                    .filterNotNull()
                                    .forEach {
                                        text(it)
                                    }
                            }
                        }
                        ImGui.popStyleColor()
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

    class LogEntry(
        val message: String,
        val type: LogType,
        val extraContext: Collection<String?>
    ) {
        val uuid = UUID.randomUUID().toString()
    }

    enum class LogType(val color: Color) {
        Debug(Color(1.0f, 1.0f, 1.0f, 1f)),
        Success(Color(0.28f, 1.0f, 0.28f, 1f)),
        Warning(Color(1.0f, 1.0f, 0.28f, 1f)),
        Error(Color(1.0f, 0.28f, 0.28f, 1f)),
        System(Color(0.28f, 0.28f, 1.0f, 1f))
    }
}