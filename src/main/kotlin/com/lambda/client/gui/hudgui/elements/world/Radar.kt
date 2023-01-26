package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.event.LambdaEventBus.post
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderRadarEvent
import com.lambda.client.gui.hudgui.HudElement
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.EntityUtils.isNeutral
import com.lambda.client.util.EntityUtils.isPassive
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.RenderUtils2D.drawCircleFilled
import com.lambda.client.util.graphics.RenderUtils2D.drawCircleOutline
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.runSafe
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.opengl.GL11.glRotatef
import org.lwjgl.opengl.GL11.glTranslated
import kotlin.math.abs
import kotlin.math.min

internal object Radar : HudElement(
    name = "Radar",
    category = Category.WORLD,
    description = "Shows entities and new chunks"
) {
    private val zoom by setting("Zoom", 3f, 1f..10f, 0.1f)
    private val chunkLines by setting("Chunk Lines", true)

    private val players = setting("Players", true)
    private val passive = setting("Passive Mobs", false)
    private val neutral = setting("Neutral Mobs", true)
    private val hostile = setting("Hostile Mobs", true)
    private val invisible = setting("Invisible Entities", true)

    override val hudWidth: Float = 130.0f
    override val hudHeight: Float = 130.0f

    private val radius get() = min(hudWidth, hudHeight) / 2

    override fun renderHud(vertexHelper: VertexHelper) {
        super.renderHud(vertexHelper)

        runSafe {
            drawBorder(vertexHelper)
            post(RenderRadarEvent(vertexHelper, radius, zoom, chunkLines)) // Let other modules display radar elements
            drawEntities(vertexHelper)
            drawLabels()
        }
    }

    private fun SafeClientEvent.drawBorder(vertexHelper: VertexHelper) {
        glTranslated(radius.toDouble(), radius.toDouble(), 0.0)
        drawCircleFilled(vertexHelper, radius = radius.toDouble(), color = GuiColors.backGround)
        drawCircleOutline(vertexHelper, radius = radius.toDouble(), lineWidth = 1.8f, color = primaryColor)
        glRotatef(player.rotationYaw + 180, 0f, 0f, -1f)
    }

    private fun SafeClientEvent.drawEntities(vertexHelper: VertexHelper) {
        drawCircleFilled(vertexHelper, radius = 1.0, color = primaryColor) // player marker

        val playerTargets = arrayOf(players.value, true, true) // Enable friends and sleeping
        val mobTargets = arrayOf(true, passive.value, neutral.value, hostile.value) // Enable mobs

        for (entity in EntityUtils.getTargetList(playerTargets, mobTargets, invisible.value, radius * zoom, ignoreSelf = true)) {
            val entityPosDelta = entity.position.subtract(player.position)
            if (abs(entityPosDelta.y) > 30) continue
            drawCircleFilled(vertexHelper, Vec2d(entityPosDelta.x.toDouble(), entityPosDelta.z.toDouble()).div(zoom.toDouble()), 2.5 / zoom, color = getColor(entity))
        }
    }

    private fun drawLabels() {
        FontRenderAdapter.drawString("Z+", -FontRenderAdapter.getStringWidth("+z") / 2f, radius - FontRenderAdapter.getFontHeight(), drawShadow = true, color = secondaryColor)
        glRotatef(90f, 0f, 0f, 1f)
        FontRenderAdapter.drawString("X-", -FontRenderAdapter.getStringWidth("+x") / 2f, radius - FontRenderAdapter.getFontHeight(), drawShadow = true, color = secondaryColor)
        glRotatef(90f, 0f, 0f, 1f)
        FontRenderAdapter.drawString("Z-", -FontRenderAdapter.getStringWidth("-z") / 2f, radius - FontRenderAdapter.getFontHeight(), drawShadow = true, color = secondaryColor)
        glRotatef(90f, 0f, 0f, 1f)
        FontRenderAdapter.drawString("X+", -FontRenderAdapter.getStringWidth("+x") / 2f, radius - FontRenderAdapter.getFontHeight(), drawShadow = true, color = secondaryColor)
    }

    private fun getColor(entity: EntityLivingBase): ColorHolder {
        return if (entity.isPassive || FriendManager.isFriend(entity.name)) { // green
            ColorHolder(32, 224, 32, 224)
        } else if (entity.isNeutral) { // yellow
            ColorHolder(255, 240, 32)
        } else { // red
            ColorHolder(255, 32, 32)
        }
    }

}