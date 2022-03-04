package com.lambda.client.module.modules.chat

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import net.minecraft.client.gui.ChatLine
import net.minecraft.util.text.ITextComponent
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

object ExtraChatHistory : Module(
    name = "ExtraChatHistory",
    description = "Show more messages in the chat history",
    category = Category.CHAT,
    alias = arrayOf("InfiniteChat", "InfiniteChatHistory"),
    showOnArray = false
) {
    private val maxMessages by setting("Max Message", 1000, 100..5000, 100)

    @JvmStatic
    fun handleSetChatLine(
        drawnChatLines: MutableList<ChatLine>,
        chatLines: MutableList<ChatLine>,
        chatComponent: ITextComponent,
        chatLineId: Int,
        updateCounter: Int,
        displayOnly: Boolean,
        ci: CallbackInfo
    ) {
        if (isDisabled) return

        while (drawnChatLines.isNotEmpty() && drawnChatLines.size > maxMessages) {
            drawnChatLines.removeLast()
        }

        if (!displayOnly) {
            chatLines.add(0, ChatLine(updateCounter, chatComponent, chatLineId))

            while (chatLines.isNotEmpty() && chatLines.size > maxMessages) {
                chatLines.removeLast()
            }
        }

        ci.cancel()
    }
}