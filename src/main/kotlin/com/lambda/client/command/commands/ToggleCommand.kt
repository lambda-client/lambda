package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.module.modules.client.CommandConfig
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import net.minecraft.util.text.TextFormatting

object ToggleCommand : ClientCommand(
    name = "toggle",
    alias = arrayOf("switch", "t"),
    description = "Toggle a module on and off!"
) {
    init {
        module("module") { moduleArg ->
            execute {
                val module = moduleArg.value
                module.toggle()
                if (module !is ClickGUI && !CommandConfig.toggleMessages) {
                    sendChatMessage(module.name +
                        if (module.isEnabled) " ${TextFormatting.GREEN}enabled"
                        else " ${TextFormatting.RED}disabled"
                    )
                }
            }
        }
    }
}