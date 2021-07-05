package com.lambda.client.module.modules.chat

import com.lambda.client.event.events.ModuleToggleEvent
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.EnumTextColor
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.format
import com.lambda.client.util.threads.safeListener

object ToggleNotifier : Module(
    name = "ToggleNotifier",
    category = Category.CHAT,
    description = "Notifies when a module is enabled/disabled.",
) {
    init {
        safeListener<ModuleToggleEvent> {
            val mod : AbstractModule = it.module
            if (mod.name == "ClickGUI") return@safeListener

            // !mod.isEnabled is ok
            MessageSendHelper.sendChatMessage("${EnumTextColor.LIGHT_PURPLE.textFormatting format mod.name} " +  if (!mod.isEnabled) "enabled" else "disabled")
        }
    }
}
