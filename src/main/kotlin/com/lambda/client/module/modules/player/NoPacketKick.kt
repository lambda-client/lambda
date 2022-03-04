package com.lambda.client.module.modules.player

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper.sendWarningMessage
import com.lambda.mixin.network.MixinNetworkManager

/**
 * @see MixinNetworkManager
 */
object NoPacketKick : Module(
    name = "NoPacketKick",
    description = "Suppress network exceptions and prevent getting kicked",
    category = Category.PLAYER,
    showOnArray = false,
    enabledByDefault = true
) {
    @JvmStatic
    fun sendWarning(throwable: Throwable) {
        sendWarningMessage("$chatName Caught exception - \"$throwable\" check log for more info.")
        throwable.printStackTrace()
    }
}
