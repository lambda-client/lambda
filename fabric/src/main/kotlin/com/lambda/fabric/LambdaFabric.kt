package com.lambda.fabric

import net.fabricmc.api.ClientModInitializer
import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION

class LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize()
        LOG.info("$MOD_NAME Fabric $VERSION initialized.")
    }
}
