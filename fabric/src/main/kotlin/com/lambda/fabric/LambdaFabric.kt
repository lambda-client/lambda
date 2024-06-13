package com.lambda.fabric

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import net.fabricmc.api.ClientModInitializer

object LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize()
        LOG.info("$MOD_NAME Fabric $VERSION initialized.")
    }
}
