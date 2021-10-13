package com.lambda.client.module.modules.render

import com.lambda.client.mixin.client.render.MixinEntityRenderer
import com.lambda.client.module.Category
import com.lambda.client.module.Module

/**
 * @see MixinEntityRenderer.orientCameraStoreRayTraceBlocks
 */
object CameraClip : Module(
    name = "CameraClip",
    category = Category.RENDER,
    description = "Allows your 3rd person camera to pass through blocks",
    showOnArray = false
) {
    val distance by setting("Distance", 4.0, 1.0..10.0, 0.1, description = "Distance to player")
}
