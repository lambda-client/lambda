package com.lambda.forge

import net.minecraftforge.fml.common.Mod
import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION

@Mod(Lambda.MOD_ID)
object LambdaForge {
    init {
        Lambda.initialize()
        LOG.info("$MOD_NAME Forge $VERSION initialized.")
    }
}
