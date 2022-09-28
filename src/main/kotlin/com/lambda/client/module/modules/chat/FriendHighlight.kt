package com.lambda.client.module.modules.chat

import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.EnumTextColor
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.SoundEvents
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.client.event.ClientChatReceivedEvent

object FriendHighlight : Module(
    name = "FriendHighlight",
    description = "Highlights your friends names in chat",
    category = Category.CHAT,
    showOnArray = false
) {
    private val bold by setting("Bold", true)
    private val color by setting("Color", EnumTextColor.GRAY)
    private val sound by setting("Sound", true)

    private val regex1 = "<(.*?)>".toRegex()
    private val regex2 = "[<>]".toRegex()

    init {
        onEnable {
            noFriendsCheck()
        }

        listener<ClientChatReceivedEvent>(0) {
            if (noFriendsCheck() || !FriendManager.enabled) return@listener

            val playerName = regex1.find(it.message.unformattedText)?.value?.replace(regex2, "")
            if (playerName == null || !FriendManager.isFriend(playerName)) return@listener
            val modified = it.message.formattedText.replaceFirst(playerName, getReplacement(playerName))
            val textComponent = TextComponentString(modified)
            if (sound) mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))

            it.message = textComponent
        }
    }

    private fun noFriendsCheck() = FriendManager.empty.also {
        if (it) {
            MessageSendHelper.sendErrorMessage("$chatName You don't have any friends added, silly! Go add some friends before using the module")
            disable()
        }
    }

    private fun getReplacement(name: String) = "${color.textFormatting}${bold()}$name${TextFormatting.RESET}"

    private fun bold() = if (!bold) "" else TextFormatting.BOLD.toString()
}
