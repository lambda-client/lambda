package com.lambda.common.context

import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld

open class SafeContext internal constructor(
    override val world: ClientWorld,
    override val player: ClientPlayerEntity,
    override val interaction: ClientPlayerInteractionManager,
    override val connection: ClientPlayNetworkHandler
) : AbstractContext()
