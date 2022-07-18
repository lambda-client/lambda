package com.lambda.client.buildtools

import com.lambda.client.buildtools.pathfinding.Navigator
import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.buildtools.task.build.BreakTask
import com.lambda.client.buildtools.task.build.DoneTask
import com.lambda.client.buildtools.task.build.PlaceTask
import com.lambda.client.module.modules.client.BuildTools.aFilled
import com.lambda.client.module.modules.client.BuildTools.aOutline
import com.lambda.client.module.modules.client.BuildTools.filled
import com.lambda.client.module.modules.client.BuildTools.outline
import com.lambda.client.module.modules.client.BuildTools.popUp
import com.lambda.client.module.modules.client.BuildTools.popUpSpeed
import com.lambda.client.module.modules.client.BuildTools.showCurrentPos
import com.lambda.client.module.modules.client.BuildTools.showDebugRender
import com.lambda.client.module.modules.client.BuildTools.textScale
import com.lambda.client.module.modules.client.BuildTools.thickness
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.ProjectionUtils
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import net.minecraft.init.Blocks
import org.lwjgl.opengl.GL11
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Renderer {
    private val renderer = ESPRenderer()

    fun renderWorld() {
        renderer.clear()
        renderer.aFilled = if (filled) aFilled else 0
        renderer.aOutline = if (outline) aOutline else 0
        renderer.thickness = thickness
        val currentTime = System.currentTimeMillis()

        if (showCurrentPos) renderer.add(Navigator.origin, ColorHolder(255, 255, 255))

        TaskProcessor.tasks.values.forEach {
            if (it.targetBlock == Blocks.AIR && it is DoneTask) return@forEach
            if (it.toRemove) {
                addToRenderer(it, currentTime, true)
            } else {
                addToRenderer(it, currentTime)
            }
        }
        renderer.render(false)
    }

    fun renderOverlay() {
        if (!showDebugRender) return
        GlStateUtils.rescaleActual()

        TaskProcessor.tasks.values.filter {
            it !is DoneTask
        }.forEach { buildTask ->
            updateOverlay(buildTask)
        }
    }

    private fun updateOverlay(buildTask: BuildTask) {
        val blockPos = buildTask.blockPos

        GL11.glPushMatrix()
        val screenPos = ProjectionUtils.toScreenPos(blockPos.toVec3dCenter())
        GL11.glTranslated(screenPos.x, screenPos.y, 0.0)
        GL11.glScalef(textScale * 2.0f, textScale * 2.0f, 1.0f)

        buildTask.gatherAllDebugInfo().forEachIndexed { index, pair ->
            val text = if (pair.second == "") {
                pair.first
            } else {
                "${pair.first}: ${pair.second}"
            }
            val halfWidth = FontRenderAdapter.getStringWidth(text) / -2.0f
            FontRenderAdapter.drawString(
                text,
                halfWidth,
                (FontRenderAdapter.getFontHeight() + 2.0f) * index,
                color = ColorHolder(255, 255, 255, 255)
            )
        }

        GL11.glPopMatrix()
    }

    private fun addToRenderer(buildTask: BuildTask, currentTime: Long, reverse: Boolean = false) {
        var aabb = buildTask.aabb

        if (popUp) {
            val age = (currentTime - buildTask.timeStamp).toDouble()
            val ageX = age.coerceAtMost(popUpSpeed * PI / 2) / popUpSpeed

            val sizeFactor = if (reverse) cos(ageX) else sin(ageX)

            aabb = buildTask.aabb.shrink((0.5 - sizeFactor * 0.5))
        }

        renderer.add(aabb, buildTask.color)

        when (buildTask) {
            is BreakTask -> {
                buildTask.breakInfo?.let { breakInfo ->
                    GeometryMasks.FACEMAP[breakInfo.side]?.let { geoSide ->
                        renderer.add(aabb, buildTask.color.multiply(1.1f), geoSide)
                    }
                }
            }
            is PlaceTask -> {
                buildTask.placeInfo?.let { placeInfo ->
                    GeometryMasks.FACEMAP[placeInfo.side]?.let { geoSide ->
                        renderer.add(aabb, buildTask.color.multiply(1.1f), geoSide)
                    }
                }
            }
        }
    }
}