package com.lambda.forge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import net.minecraftforge.fml.common.Mod


@Mod(Lambda.MOD_ID)
class LambdaForge {
    init {
        Lambda.initialize()
        LOG.info("$MOD_NAME Forge $VERSION initialized.")
    }
}
