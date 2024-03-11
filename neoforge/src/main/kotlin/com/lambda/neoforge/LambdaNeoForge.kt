package com.lambda.neoforge

import net.neoforged.fml.common.Mod
import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION

@Mod(Lambda.MOD_ID)
object LambdaNeoForge {
    init {
        Lambda.initialize()
        LOG.info("$MOD_NAME Fabric $VERSION initialized.")
    }
}
