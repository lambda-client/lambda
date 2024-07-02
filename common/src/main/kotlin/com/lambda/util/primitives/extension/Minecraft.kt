package com.lambda.util.primitives.extension

import net.minecraft.client.MinecraftClient

val MinecraftClient.tickDelta: Float
    get() = renderTickCounter.tickDelta

val MinecraftClient.pausedTickDelta: Float
    get() = renderTickCounter.tickDeltaBeforePause
