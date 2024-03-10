package com.lambda.forge

import net.minecraftforge.fml.common.Mod
import com.lambda.Lambda
import com.lambda.Lambda.LOG

@Mod(Lambda.MOD_ID)
object LambdaForge {
    init {
        Lambda.initialize()
        LOG.info("Lambda Forge initialized")
    }
}
