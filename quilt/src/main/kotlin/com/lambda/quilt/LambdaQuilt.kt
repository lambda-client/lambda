package com.lambda.quilt

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer

// Cannot use `object` because Quilt has a class fetish (object is a class in Kotlin with a private constructor)
class LambdaQuilt : ModInitializer {
    // If the mod is marked as non nullable, Quilt will panic because it can't find the instrisic library
    override fun onInitialize(mod: ModContainer?) {
        Lambda.initialize()
        LOG.info("$MOD_NAME Quilt $VERSION initialized.")
    }
}
