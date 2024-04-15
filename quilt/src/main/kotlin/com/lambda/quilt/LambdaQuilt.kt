package com.lambda.quilt

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer

class LambdaQuilt : ClientModInitializer {
    override fun onInitializeClient(mod: ModContainer) {
        Lambda.initialize()
        LOG.info("$MOD_NAME Quilt $VERSION initialized.")
    }
}
