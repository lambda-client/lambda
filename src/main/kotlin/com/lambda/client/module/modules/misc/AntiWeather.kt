package com.lambda.client.module.modules.misc

import com.lambda.client.mixin.client.world.MixinWorld
import com.lambda.client.module.Category
import com.lambda.client.module.Module

/**
 * @see MixinWorld.getThunderStrengthHead
 * @see MixinWorld.getRainStrengthHead
 */
internal object AntiWeather : Module(
    name = "AntiWeather",
    description = "Removes rain and thunder from your world",
    category = Category.MISC
)
