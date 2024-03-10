package com.lambda.fabric

import net.fabricmc.api.ClientModInitializer
import com.lambda.common.Lambda
import com.lambda.common.Lambda.LOG

object LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize()
        LOG.info("Lambda Fabric initialized")
    }
}
