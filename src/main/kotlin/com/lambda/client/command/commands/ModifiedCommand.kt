package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.command.CommandManager
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.event.ClickEvent
import net.minecraft.util.text.event.HoverEvent

object ModifiedCommand : ClientCommand(
    name = "modified",
    description = "View modified settings in a module"
) {
    init {
        module("module") { module ->
            execute("List modified settings") {
                var anyChanged = false

                for (setting in module.value.settingList) {
                    if (!setting.isModified) continue
                    anyChanged = true

                    val component = TextComponentString("${setting.name} has been changed to ${setting.value}")
                    // horrible, however this is mojang code that we are working on.
                    component.style.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "${CommandManager.prefix}set ${module.value.name} ${setting.name.replace(" ", "")} ${setting.defaultValue}")
                    component.style.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponentString("Click to reset to default settings"))

                    sendChatMessage(component)
                }

                if (!anyChanged) {
                    sendChatMessage("${module.value.name}'s settings are not modified from default")
                }
            }
        }
    }
}