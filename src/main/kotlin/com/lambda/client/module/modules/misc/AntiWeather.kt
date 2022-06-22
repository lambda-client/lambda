package com.lambda.client.module.modules.misc

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.mixin.world.MixinWorld

/**
 * @see MixinWorld.getThunderStrengthHead
 * @see MixinWorld.getRainStrengthHead
 */
object AntiWeather : Module(
    name = "AntiWeather",
    description = "Removes rain and thunder from your world",
    category = Category.MISC
)
