package com.lambda.client.module.modules.chat

import com.lambda.mixin.player.MixinEntityPlayerSP
import com.lambda.client.module.Category
import com.lambda.client.module.Module

/**
 * @see MixinEntityPlayerSP
 */
object PortalChat : Module(
    name = "PortalChat",
    description = "Allows you to open GUIs in portals",
    category = Category.CHAT,
    showOnArray = false
)
