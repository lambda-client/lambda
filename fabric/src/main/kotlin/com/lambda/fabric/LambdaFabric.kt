package com.lambda.fabric

import net.fabricmc.api.ClientModInitializer
import com.lambda.Lambda

object LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize()
    }
}
