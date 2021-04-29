package com.lambda.client.module.modules.render

import com.lambda.client.mixin.client.render.MixinEntityRenderer
import com.lambda.client.module.Category
import com.lambda.client.module.Module

/**
 * @see MixinEntityRenderer.rayTraceBlocks
 */
internal object CameraClip : Module(
    name = "CameraClip",
    category = Category.RENDER,
    description = "Allows your 3rd person camera to pass through blocks",
    showOnArray = false
)
