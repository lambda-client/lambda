package com.lambda.fabric

import net.fabricmc.api.ClientModInitializer
import com.lambda.Lambda
import com.lambda.Lambda.LOG

object LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize()
        LOG.info("Lambda Fabric initialized")
    }
}
