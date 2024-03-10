package com.lambda.neoforge

import net.neoforged.fml.common.Mod
import com.lambda.Lambda

@Mod(Lambda.MOD_ID)
object LambdaNeoForge {
    init {
        Lambda.initialize()
    }
}
