package com.lambda.client.module.modules.client

import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.notifications.Notification
import com.lambda.client.util.notifications.NotificationType
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11

object Notifications : Module(
    name = "Notifications",
    category = Category.CLIENT,
    description = "Shows notifications",
    alwaysListening = true,
    enabledByDefault = true
) {
    private val mode by setting("Mode", NotificationMode.RENDER)
    private val notificationHeight by setting("Notification Height", 15.0, 13.0..25.0, 1.0)
    private val renderLocation by setting("Render Location", RenderLocation.BOTTOM_RIGHT)
    private val horizontalPadding by setting("W Padding", 6f, 0f..40f, 1f)
    private val verticalPadding by setting("H Padding", 15f, 0f..40f, 1f)

    enum class RenderLocation(val renderDirection: Int) {
        BOTTOM_RIGHT(-1), TOP_RIGHT(1), TOP_LEFT(1)
    }

    enum class NotificationMode {
        RENDER, CHAT, RENDER_AND_CHAT
    }

    private val notifications = mutableListOf<Notification>()

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.END) return@safeListener

            val removalList = notifications.filter { notification ->
                notification.startTime + notification.duration < System.currentTimeMillis()
            }
            notifications.removeAll(removalList)
        }

        safeListener<RenderOverlayEvent> {
            val scaledResolution = ScaledResolution(mc)
            val vertexHelper = VertexHelper(GlStateUtils.useVbo())

            notifications.forEachIndexed { index, notification ->
                GlStateManager.pushMatrix()
                when (renderLocation) {
                    RenderLocation.BOTTOM_RIGHT -> GL11.glTranslatef((scaledResolution.scaledWidth_double - horizontalPadding - 90).toFloat(), (scaledResolution.scaledHeight_double - verticalPadding - notificationHeight).toFloat(), 0f)
                    RenderLocation.TOP_RIGHT -> GL11.glTranslatef((scaledResolution.scaledWidth_double - horizontalPadding - 90).toFloat(), verticalPadding, 0f)
                    RenderLocation.TOP_LEFT -> GL11.glTranslatef(horizontalPadding, verticalPadding, 0f)
                }
                GlStateManager.translate(0.0, index * (notificationHeight + 3.0) * renderLocation.renderDirection, 0.0)
                drawNotification(vertexHelper, notification)
                GlStateManager.popMatrix()
            }
        }
    }

    private fun drawNotification(vertexHelper: VertexHelper, notification: Notification) {

        val color = when (notification.type) {
            NotificationType.INFO -> ColorHolder(3, 169, 244)
            NotificationType.WARNING -> ColorHolder(255, 255, 0)
            NotificationType.ERROR -> ColorHolder(255, 0, 0)
        }

        val startTime = notification.startTime
        val duration = notification.duration

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - startTime

        val textScale = (notificationHeight / 16f).toFloat().coerceAtMost(1.0f)
        val textWidth = FontRenderAdapter.getStringWidth(notification.text, textScale, CustomFont.isEnabled)
        val textHeight = FontRenderAdapter.getFontHeight(textScale, CustomFont.isEnabled)
        val width: Double = if(textWidth > 88) textWidth + 25.0 else 90.0
        val clearWidth = width - 2

        val timeout = (clearWidth * elapsedTime) / duration
        val timeoutBarWidth = if (timeout > clearWidth) clearWidth else if (timeout < 0) 0 else timeout

        val borderPosBegin = if(renderLocation == RenderLocation.BOTTOM_RIGHT) Vec2d(width, 0.0) else Vec2d(0.0, 0.0)
        val borderPosEnd = if(renderLocation == RenderLocation.BOTTOM_RIGHT) Vec2d(clearWidth, notificationHeight) else Vec2d(2.0, notificationHeight)

        val timeoutBarPosBegin = if(renderLocation == RenderLocation.BOTTOM_RIGHT) Vec2d(timeoutBarWidth.toDouble(), notificationHeight)
        else Vec2d(2.0, notificationHeight)
        val timeoutBarPosEnd = if(renderLocation == RenderLocation.BOTTOM_RIGHT) Vec2d(clearWidth, notificationHeight - 1)
        else Vec2d(clearWidth - timeoutBarWidth.toDouble(), notificationHeight - 1)

        // Draw background
        RenderUtils2D.drawRectFilled(vertexHelper, Vec2d.ZERO, Vec2d(width, notificationHeight), GuiColors.backGround)

        // Draw timeout bar
        RenderUtils2D.drawRectFilled(vertexHelper, timeoutBarPosBegin, timeoutBarPosEnd, color)

        // Draw border
        RenderUtils2D.drawRectFilled(vertexHelper, borderPosBegin, borderPosEnd, color)

        // Draw text
        FontRenderAdapter.drawString(notification.text, 4.0f, ((notificationHeight / 2.5) - (textHeight / 2)).toFloat(), true, ColorHolder(), textScale, CustomFont.isEnabled)
    }
    fun addNotification(notification: Notification) {
        if (mode == NotificationMode.CHAT || mode == NotificationMode.RENDER_AND_CHAT) {
            MessageSendHelper.sendChatMessage(notification.text)
        }
        if (mode == NotificationMode.RENDER || mode == NotificationMode.RENDER_AND_CHAT) {
            notifications.add(notification)
        }
    }
}