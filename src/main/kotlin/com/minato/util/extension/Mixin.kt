
package com.minato.util.extension

import net.minecraft.client.MinecraftClient

val MinecraftClient.tickDelta
    get() = tickDeltaF.toDouble()

val MinecraftClient.tickDeltaF
    get() = renderTickCounter.getTickProgress(true)
