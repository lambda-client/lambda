package com.lambda.forge

import net.minecraftforge.fml.common.Mod
import com.lambda.common.Lambda
import com.lambda.common.Lambda.LOG

@Mod(Lambda.MOD_ID)
object LambdaForge {
    init {
        Lambda.initialize()
        LOG.info("Lambda Forge initialized")
    }
}
