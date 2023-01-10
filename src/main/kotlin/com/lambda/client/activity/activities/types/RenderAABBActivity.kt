package com.lambda.client.activity.activities.types

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.LambdaTessellator
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

interface RenderAABBActivity {
    val toRender: MutableSet<RenderAABBCompound>

    companion object {
        val normalizedRender: MutableSet<RenderAABB> = mutableSetOf()

        fun SafeClientEvent.checkRender() {
            normalizedRender.clear()

            ActivityManager.getAllSubActivities()
                .filterIsInstance<RenderAABBActivity>()
                .forEach { activity ->
                    activity.toRender.forEach { compound ->
                        when (compound) {
                            is RenderAABB -> normalizedRender.add(compound)
                            is RenderBlockPos -> {
                                with(compound) {
                                    normalizedRender.add(toRenderAABB())
                                }
                            }
                            is RenderEntity -> {
                                with(compound) {
                                    normalizedRender.add(toRenderAABB())
                                }
                            }
                        }
                    }
                }
        }

        interface RenderAABBCompound

        data class RenderAABB(
            var renderAABB: AxisAlignedBB,
            var color: ColorHolder
        ) : RenderAABBCompound

        data class RenderBlockPos(
            var renderBlockPos: BlockPos,
            var color: ColorHolder
        ) : RenderAABBCompound {
            fun SafeClientEvent.toRenderAABB() =
                RenderAABB(world.getBlockState(renderBlockPos).getSelectedBoundingBox(world, renderBlockPos), color)
        }

        data class RenderEntity(
            var renderEntity: Entity,
            var color: ColorHolder
        ) : RenderAABBCompound {
            fun toRenderAABB() =
                RenderAABB(renderEntity.renderBoundingBox.offset(
                    EntityUtils.getInterpolatedAmount(renderEntity, LambdaTessellator.pTicks())
                ), color)
        }
    }
}