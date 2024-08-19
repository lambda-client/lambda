package com.lambda.util.extension

import net.minecraft.client.MinecraftClient

val MinecraftClient.partialTicks
    get() = (if (paused) pausedTickDelta else tickDelta).toDouble()
