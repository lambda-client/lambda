package com.lambda.module.modules.player

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.module.Module
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.world.WorldUtils.searchBlocks
import net.minecraft.util.math.Vec3i

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you",
    defaultKeybind = KeyCode.Comma
) {
    private val flatten by setting("Flatten", true)

    private val range = Vec3i(4, 4, 4) // TODO: Customizable

    init {
        listener<TickEvent.Pre> {
            searchBlocks(player.blockPos, range, null,
                iterator = { state, pos, _ ->
                    state.getCollisionShape(world, pos).boundingBox
                        .getVisibleSurfaces(player.eyePos).firstOrNull()?.let {
                            interaction.updateBlockBreakingProgress(pos, it)
                        }
                    this@Nuker.info("Breaking ${state.block.name.string} at $pos with hardness ${state.getHardness(world, pos)}")
                },
            ) { state, pos ->
                state.isSolidBlock(world, pos)
                        && !state.isAir
                        && (!flatten || pos.y >= player.y)
                        //&& state.getHardness(world, pos) <= 1.0f
                        && state.getHardness(world, pos) > 0.0f
                        && player.eyePos dist pos.toCenterPos() <= interaction.reachDistance - 1
            }
        }
    }
}
