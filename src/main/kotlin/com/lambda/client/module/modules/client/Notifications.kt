package com.lambda.client.module.modules.client

import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.AnimationUtils
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
    showOnArray = false,
    enabledByDefault = true
) {
    private val page by setting("Page", Page.GENERAL)

    // General settings
    private val mode by setting("Mode", NotificationMode.RENDER, { page == Page.GENERAL })
    private val notificationHeight by setting("Notification Height", 15.0, 13.0..25.0, 1.0, { page == Page.GENERAL })
    private val renderLocation by setting("Render Location", RenderLocation.BOTTOM_RIGHT, { page == Page.GENERAL })
    private val horizontalPadding by setting("W Padding", 6f, 0f..40f, 1f, { page == Page.GENERAL })
    private val verticalPadding by setting("H Padding", 15f, 0f..40f, 1f, { page == Page.GENERAL })

    // Timeout settings
    private val infoTimeout by setting("Info Timeout", 3000, 1000..10000, 100, { page == Page.TIMEOUT })
    private val warningTimeout by setting("Warning Timeout", 4000, 1000..10000, 100, { page == Page.TIMEOUT })
    private val errorTimeout by setting("Error Timeout", 7000, 1000..10000, 100, { page == Page.TIMEOUT })

    enum class RenderLocation(val xValue:Int, val yValue: Int) {
        BOTTOM_RIGHT(1,-1), BOTTOM_LEFT(-1,-1), TOP_RIGHT(1,1), TOP_LEFT(-1,1)
    }

    enum class NotificationMode {
        RENDER, CHAT, RENDER_AND_CHAT
    }

    private enum class Page {
        GENERAL, TIMEOUT
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

            var y = 0.0
            notifications.forEach { notification ->
                GlStateManager.pushMatrix()
                when (renderLocation) {
                    RenderLocation.BOTTOM_RIGHT -> GL11.glTranslatef((scaledResolution.scaledWidth_double - horizontalPadding - 90).toFloat(), (scaledResolution.scaledHeight_double - verticalPadding - notificationHeight).toFloat(), 0f)
                    RenderLocation.BOTTOM_LEFT -> GL11.glTranslatef(horizontalPadding, (scaledResolution.scaledHeight_double - verticalPadding - notificationHeight).toFloat(), 0f)
                    RenderLocation.TOP_RIGHT -> GL11.glTranslatef((scaledResolution.scaledWidth_double - horizontalPadding - 90).toFloat(), verticalPadding, 0f)
                    RenderLocation.TOP_LEFT -> GL11.glTranslatef(horizontalPadding, verticalPadding, 0f)
                }
                y += renderLocation.yValue * ((notificationHeight + 3.0) * notification.animate())
                GlStateManager.translate(0.0, y, 0.0)
                drawNotification(vertexHelper, notification)
                GlStateManager.popMatrix()
            }
        }
    }

    private fun drawNotification(vertexHelper: VertexHelper, notification: Notification) {

        val startTime = notification.startTime
        val duration = notification.duration

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - startTime

        val textScale = (notificationHeight / 32f).toFloat().coerceAtMost(1.0f)
        val textWidth = FontRenderAdapter.getStringWidth(notification.text, textScale, CustomFont.isEnabled)
        val textHeight = FontRenderAdapter.getFontHeight(textScale, CustomFont.isEnabled)
        val textPosY = ((notificationHeight / 2.5) - (textHeight / 2)).toFloat()

        val width: Double = if (textWidth > 88) textWidth + 25.0 else 90.0
        val clearWidth = width - 2

        val timeout = (clearWidth * elapsedTime) / duration
        val timeoutBarWidth = if (timeout > clearWidth) clearWidth else if (timeout < 0) 0 else timeout

        val borderPosBegin = if (renderLocation.name.contains("RIGHT")) Vec2d(width, 0.0) else Vec2d(0.0, 0.0)
        val borderPosEnd = if (renderLocation.name.contains("RIGHT")) Vec2d(clearWidth, notificationHeight) else Vec2d(2.0, notificationHeight)

        val timeoutBarPosBegin = if (renderLocation.name.contains("RIGHT")) Vec2d(timeoutBarWidth.toDouble(), notificationHeight)
        else Vec2d(2.0, notificationHeight)
        val timeoutBarPosEnd = if (renderLocation.name.contains("RIGHT")) Vec2d(clearWidth, notificationHeight - 1)
        else Vec2d(clearWidth - timeoutBarWidth.toDouble(), notificationHeight - 1)

        val alpha: Int = when (elapsedTime) {
            // "Fade in" alpha
            in 0..duration - 200 ->
                ((255 * elapsedTime) / 200).toInt().coerceAtMost(255).coerceAtLeast(0)

            // "Fade out" alpha
            in duration - 200..duration -> {
                val timeLeft = duration - elapsedTime
                ((255 * timeLeft) / 200).toInt().coerceAtMost(255).coerceAtLeast(0)
            }

            else -> 0
        }

        val color = colorFromType(notification.type, alpha)
        val backgroundColor: ColorHolder = when (alpha) {
            in 0..GuiColors.backGround.a -> ColorHolder(GuiColors.backGround.r, GuiColors.backGround.g,
                GuiColors.backGround.b, alpha)

            else -> GuiColors.backGround
        }

        GlStateManager.pushMatrix()
        val animatedPercent = notification.animate()
        val animationXOffset = textWidth * renderLocation.xValue *(1.0f - animatedPercent)
        GlStateManager.translate(animationXOffset,0.0f,0.0f)


        // Draw background
        RenderUtils2D.drawRectFilled(vertexHelper, Vec2d.ZERO, Vec2d(width, notificationHeight), backgroundColor)

        // Draw timeout bar
        RenderUtils2D.drawRectFilled(vertexHelper, timeoutBarPosBegin, timeoutBarPosEnd, color)

        // Draw border
        RenderUtils2D.drawRectFilled(vertexHelper, borderPosBegin, borderPosEnd, color)

        // Draw text
        FontRenderAdapter.drawString(notification.text, 4.0f, textPosY, true,
            ColorHolder(255, 255, 255, alpha), textScale, CustomFont.isEnabled)

        GlStateManager.popMatrix()
    }

    fun addNotification(notification: Notification) {
        if (notification.duration == 0) when (notification.type) {
            NotificationType.INFO -> notification.duration = infoTimeout
            NotificationType.WARNING -> notification.duration = warningTimeout
            NotificationType.ERROR -> notification.duration = errorTimeout
        }

        if (mode == NotificationMode.CHAT || mode == NotificationMode.RENDER_AND_CHAT) {
            MessageSendHelper.sendChatMessage(notification.text)
        }
        if (mode == NotificationMode.RENDER || mode == NotificationMode.RENDER_AND_CHAT) {
            notifications.add(notification)
        }
    }

    private fun colorFromType(notificationType: NotificationType, alpha: Int = 255): ColorHolder = when (notificationType) {
        NotificationType.INFO -> ColorHolder(3, 169, 244, alpha)
        NotificationType.WARNING -> ColorHolder(255, 255, 0, alpha)
        NotificationType.ERROR -> ColorHolder(255, 0, 0, alpha)
    }
    private fun Notification.animate() : Float{
        return if ((System.currentTimeMillis() - startTime) < (duration.toLong() / 2)){
            AnimationUtils.exponentInc(AnimationUtils.toDeltaTimeFloat(startTime), 200.0f)
        }else{
            AnimationUtils.exponentDec(AnimationUtils.toDeltaTimeFloat(startTime + duration - 200), 200.0f)
        }
    }
}