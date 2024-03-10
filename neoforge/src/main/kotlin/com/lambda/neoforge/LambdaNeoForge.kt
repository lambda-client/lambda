package com.lambda.neoforge

import net.neoforged.fml.common.Mod
import com.lambda.common.Lambda
import com.lambda.common.Lambda.LOG

@Mod(Lambda.MOD_ID)
object LambdaNeoForge {
    init {
        Lambda.initialize()
        LOG.info("Lambda NeoForge initialized")
    }
}
