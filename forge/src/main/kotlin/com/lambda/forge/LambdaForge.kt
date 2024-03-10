package com.lambda.forge

import net.minecraftforge.fml.common.Mod
import com.lambda.Lambda

@Mod(Lambda.MOD_ID)
object LambdaForge {
    init {
        Lambda.initialize()
    }
}
