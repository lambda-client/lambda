package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.module.modules.player.PacketLogger
import java.util.*

internal object PacketLogViewer: LabelHud(
    name = "PacketLogViewer",
    category = Category.MISC,
    description = "Displays the packet log"
) {

    private val maxLines by setting("Max Lines", 50, 1..200, 1)
    private val logs: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val onlyWhenLoggerEnabled by setting("Only When Logger Enabled", true)
    private val clearButton by setting("Clear", false, consumer = { _, _ ->
        clear()
        false
    })

    init {
        visibleSetting.valueListeners.add { _, it ->
            if (!it) {
                clear()
            }
        }
    }

    override fun SafeClientEvent.updateText() {
        displayText.clear()
        if (onlyWhenLoggerEnabled && PacketLogger.isDisabled) return
        if (logs.isNotEmpty() && (PacketLogger.logMode == PacketLogger.LogMode.ALL || PacketLogger.logMode == PacketLogger.LogMode.ONLY_HUD)) {
            displayText.addLine("PacketLog Viewer", secondaryColor)
            synchronized(logs) {
                logs.forEach { log ->
                    displayText.addLine(log, primaryColor)
                }
            }
        }
    }

    fun addPacketLog(log: String) {
        synchronized(logs) {
            logs.add(log)
            if (logs.size > maxLines) {
                logs.removeAt(0)
            }
        }
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
    }
}