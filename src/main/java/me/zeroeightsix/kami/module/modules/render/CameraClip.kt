package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.module.Module

/**
 * Created by 086 on 11/12/2017.
 *
 * [me.zeroeightsix.kami.mixin.client.MixinEntityRenderer.rayTraceBlocks]
 */
@Module.Info(
        name = "CameraClip",
        category = Module.Category.RENDER,
        description = "Allows your 3rd person camera to pass through blocks",
        showOnArray = Module.ShowOnArray.OFF
)
class CameraClip : Module()