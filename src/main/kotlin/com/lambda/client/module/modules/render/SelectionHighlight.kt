package com.lambda.client.module.modules.render

import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.graphics.KamiTessellator
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.threads.safeListener
import com.lambda.event.listener.listener
import net.minecraft.util.math.RayTraceResult.Type
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object SelectionHighlight : Module(
    name = "SelectionHighlight",
    description = "Highlights object you are looking at",
    category = Category.RENDER
) {
    val block = setting("Block", true)
    private val entity = setting("Entity", false)
    private val hitSideOnly = setting("Hit Side Only", false)
    private val throughBlocks = setting("Through Blocks", false)
    private val filled = setting("Filled", true)
    private val outline = setting("Outline", true)
    private val r = setting("Red", 155, 0..255, 1)
    private val g = setting("Green", 144, 0..255, 1)
    private val b = setting("Blue", 255, 0..255, 1)
    private val aFilled = setting("Filled Alpha", 63, 0..255, 1, { filled.value })
    private val aOutline = setting("Outline Alpha", 200, 0..255, 1, { outline.value })
    private val thickness = setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f)

    private val renderer = ESPRenderer()

    init {
        listener<RenderWorldEvent> {
            val viewEntity = mc.renderViewEntity ?: mc.player ?: return@listener
            val eyePos = viewEntity.getPositionEyes(KamiTessellator.pTicks())
            if (!mc.world.isAirBlock(eyePos.toBlockPos())) return@listener
            val color = ColorHolder(r.value, g.value, b.value)
            val hitObject = mc.objectMouseOver ?: return@listener

            if (entity.value && hitObject.typeOfHit == Type.ENTITY) {
                val lookVec = viewEntity.lookVec
                val sightEnd = eyePos.add(lookVec.scale(6.0))
                val hitSide = hitObject.entityHit?.entityBoundingBox?.calculateIntercept(eyePos, sightEnd)?.sideHit
                val side = (if (hitSideOnly.value) GeometryMasks.FACEMAP[hitSide] else GeometryMasks.Quad.ALL)
                    ?: return@listener
                renderer.add(hitObject.entityHit, color, side)
            }

            if (block.value && hitObject.typeOfHit == Type.BLOCK) {
                val blockState = mc.world.getBlockState(hitObject.blockPos)
                val box = blockState.getSelectedBoundingBox(mc.world, hitObject.blockPos) ?: return@listener
                val side = (if (hitSideOnly.value) GeometryMasks.FACEMAP[hitObject.sideHit] else GeometryMasks.Quad.ALL)
                    ?: return@listener
                renderer.add(box.grow(0.002), color, side)
            }
            renderer.render(true)
        }

        safeListener<TickEvent.ClientTickEvent> {
            renderer.aFilled = if (filled.value) aFilled.value else 0
            renderer.aOutline = if (outline.value) aOutline.value else 0
            renderer.through = throughBlocks.value
            renderer.thickness = thickness.value
            renderer.fullOutline = true
        }
    }
}