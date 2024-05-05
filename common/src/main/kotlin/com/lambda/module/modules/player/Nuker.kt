package com.lambda.module.modules.player

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.module.Module
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import com.lambda.util.math.VecUtils.dist
import net.minecraft.util.math.BlockPos

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you",
    defaultKeybind = KeyCode.Comma
) {
    private val flatten by setting("Flatten", true)

    init {
        listener<TickEvent.Pre> {
            BlockPos.iterateOutwards(player.blockPos, 4, 4, 4).map {
                it.toImmutable()
            }.filter {
                val state = world.getBlockState(it)
                state.isSolidBlock(world, it)
                        && !state.isAir
                        && (!flatten || it.y >= player.y)
                        && /*state.getHardness(world, it) <= 1.0f &&*/ state.getHardness(world, it) > 0.0f
                        && player.eyePos dist it.toCenterPos() <= interaction.reachDistance - 1
            }.sortedBy {
                player.eyePos dist it.toCenterPos()
            }.forEach { pos ->
                val state = world.getBlockState(pos)
                state.getCollisionShape(world, pos).boundingBox
                    .getVisibleSurfaces(player.eyePos).firstOrNull()?.let {
                        interaction.updateBlockBreakingProgress(pos, it)
                    }
                this@Nuker.info("Breaking ${state.block.name.string} at $pos with hardness ${state.getHardness(world, pos)}")
            }
        }
    }
}