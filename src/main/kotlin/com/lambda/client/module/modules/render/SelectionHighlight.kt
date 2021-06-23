package com.lambda.client.module.modules.render

import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.math.RayTraceResult.Type
import net.minecraftforge.fml.common.gameevent.TickEvent

object SelectionHighlight : Module(
    name = "SelectionHighlight",
    description = "Highlights object you are looking at",
    category = Category.RENDER
) {
    val block by setting("Block", true)
    private val entity by setting("Entity", false)
    private val hitSideOnly by setting("Hit Side Only", false)
    private val throughBlocks by setting("Through Blocks", false)
    private val filled by setting("Filled", true)
    private val outline by setting("Outline", true)
    private val color by setting("Color", ColorHolder(155, 144, 255))
    private val aFilled by setting("Filled Alpha", 63, 0..255, 1, { filled })
    private val aOutline by setting("Outline Alpha", 200, 0..255, 1, { outline })
    private val thickness by setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f)

    private val renderer = ESPRenderer()

    init {
        safeListener<RenderWorldEvent> {
            val viewEntity = mc.renderViewEntity ?: player
            val eyePos = viewEntity.getPositionEyes(LambdaTessellator.pTicks())
            if (!world.isAirBlock(eyePos.toBlockPos())) return@safeListener
            val hitObject = mc.objectMouseOver ?: return@safeListener

            if (entity && hitObject.typeOfHit == Type.ENTITY) {
                val lookVec = viewEntity.lookVec
                val sightEnd = eyePos.add(lookVec.scale(6.0))
                val hitSide = hitObject.entityHit?.entityBoundingBox?.calculateIntercept(eyePos, sightEnd)?.sideHit
                val side = (if (hitSideOnly) GeometryMasks.FACEMAP[hitSide] else GeometryMasks.Quad.ALL)
                    ?: return@safeListener
                renderer.add(hitObject.entityHit, color, side)
            }

            if (block && hitObject.typeOfHit == Type.BLOCK) {
                val blockState = world.getBlockState(hitObject.blockPos)
                val box = blockState.getSelectedBoundingBox(world, hitObject.blockPos) ?: return@safeListener
                val side = (if (hitSideOnly) GeometryMasks.FACEMAP[hitObject.sideHit] else GeometryMasks.Quad.ALL)
                    ?: return@safeListener
                renderer.add(box.grow(0.002), color, side)
            }
            renderer.render(true)
        }

        safeListener<TickEvent.ClientTickEvent> {
            renderer.aFilled = if (filled) aFilled else 0
            renderer.aOutline = if (outline) aOutline else 0
            renderer.through = throughBlocks
            renderer.thickness = thickness
            renderer.fullOutline = true
        }
    }
}