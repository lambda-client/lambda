package com.lambda.client.module.modules.chat

import com.lambda.client.mixin.client.player.MixinEntityPlayerSP
import com.lambda.client.module.Category
import com.lambda.client.module.Module

/**
 * @see MixinEntityPlayerSP
 */
internal object PortalChat : Module(
    name = "PortalChat",
    category = Category.CHAT,
    description = "Allows you to open GUIs in portals",
    showOnArray = false
)
