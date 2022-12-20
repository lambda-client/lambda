package com.lambda.client.module.modules.render

import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.getInterpolatedAmount
import com.lambda.client.util.EntityUtils.getTargetList
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.GL_LINES
import kotlin.math.min

object EyeFinder : Module(
    name = "EyeFinder",
    description = "Draws lines from entity's heads to where they are looking",
    category = Category.RENDER
) {
    private val page by setting("Page", Page.ENTITY_TYPE)

    /* Entity type settings */
    private val players by setting("Players", true, { page == Page.ENTITY_TYPE })
    private val friends by setting("Friends", false, { page == Page.ENTITY_TYPE && players })
    private val sleeping by setting("Sleeping", false, { page == Page.ENTITY_TYPE && players })
    private val mobs by setting("Mobs", true, { page == Page.ENTITY_TYPE })
    private val passive by setting("Passive Mobs", false, { page == Page.ENTITY_TYPE && mobs })
    private val neutral by setting("Neutral Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val hostile by setting("Hostile Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val invisible by setting("Invisible", true, { page == Page.ENTITY_TYPE })
    private val range by setting("Range", 64, 8..128, 8, { page == Page.ENTITY_TYPE }, unit = " blocks")

    /* Rendering settings */
    private val color by setting("Color", ColorHolder(155, 144, 255, 200), visibility = { page == Page.RENDERING })
    private val thickness by setting("Thickness", 2.0f, 0.25f..5.0f, 0.25f, { page == Page.RENDERING })


    private enum class Page {
        ENTITY_TYPE, RENDERING
    }

    private val resultMap = HashMap<Entity, Pair<RayTraceResult, Float>>()

    init {
        listener<RenderWorldEvent> {
            if (resultMap.isEmpty()) return@listener
            for ((entity, pair) in resultMap) {
                drawLine(entity, pair)
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            alwaysListening = resultMap.isNotEmpty()

            val player = arrayOf(players, friends, sleeping)
            val mob = arrayOf(mobs, passive, neutral, hostile)
            val entityList = if (isEnabled) {
                getTargetList(player, mob, invisible, range.toFloat(), ignoreSelf = false)
            } else {
                ArrayList()
            }
            val cacheMap = HashMap<Entity, Pair<RayTraceResult, Float>>()
            for (entity in entityList) {
                val result = getRaytraceResult(entity) ?: continue
                cacheMap[entity] = Pair(result, 0.0f)
            }
            for ((entity, pair) in resultMap) {
                val result = getRaytraceResult(entity) ?: continue
                cacheMap.computeIfPresent(entity) { _, cachePair -> Pair(cachePair.first, min(pair.second + 0.07f, 1f)) }
                cacheMap.computeIfAbsent(entity) { Pair(result, pair.second - 0.05f) }
                if (pair.second < 0f) cacheMap.remove(entity)
            }
            resultMap.clear()
            resultMap.putAll(cacheMap)
        }
    }

    private fun getRaytraceResult(entity: Entity): RayTraceResult? {
        var result = entity.rayTrace(5.0, Minecraft.getMinecraft().renderPartialTicks)
            ?: return null /* Raytrace for block */
        if (result.typeOfHit == RayTraceResult.Type.MISS) { /* Raytrace for entity */
            val eyePos = entity.getPositionEyes(mc.renderPartialTicks)
            val entityLookVec = entity.getLook(mc.renderPartialTicks).scale(5.0)
            val entityLookEnd = eyePos.add(entityLookVec)
            for (otherEntity in mc.world.loadedEntityList) {
                if (otherEntity.getDistance(entity) > 10.0) continue /* Some entity has bigger bounding box */
                if (otherEntity == entity || otherEntity == mc.player) continue
                val box = otherEntity.entityBoundingBox
                result = box.calculateIntercept(eyePos, entityLookEnd) ?: continue
                result.typeOfHit = RayTraceResult.Type.ENTITY
                result.entityHit = otherEntity
            }
        }
        return result
    }

    private fun drawLine(entity: Entity, pair: Pair<RayTraceResult, Float>) {
        val eyePos = entity.getPositionEyes(mc.renderPartialTicks)
        val result = pair.first
        val alpha = (color.a * pair.second).toInt()

        /* Render line */
        val buffer = LambdaTessellator.buffer
        GlStateManager.glLineWidth(thickness)
        LambdaTessellator.begin(GL_LINES)
        buffer.pos(eyePos.x, eyePos.y, eyePos.z).color(color.r, color.g, color.b, alpha).endVertex()
        buffer.pos(result.hitVec.x, result.hitVec.y, result.hitVec.z).color(color.r, color.g, color.b, alpha).endVertex()
        LambdaTessellator.render()

        /* Render hit position */
        if (result.typeOfHit != RayTraceResult.Type.MISS) {
            val box = if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
                AxisAlignedBB(result.blockPos).grow(0.002)
            } else {
                val offset = getInterpolatedAmount(result.entityHit, LambdaTessellator.pTicks())
                result.entityHit.renderBoundingBox.offset(offset)
            }
            val renderer = ESPRenderer()
            renderer.aFilled = (alpha / 3)
            renderer.aOutline = alpha
            renderer.thickness = (thickness)
            renderer.through = false
            renderer.add(box, color)
            renderer.render(true)
        }
    }
}
