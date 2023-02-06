package com.lambda.client.gui.hudgui.elements.client

import com.lambda.client.gui.hudgui.HudElement
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.graphics.font.HAlign
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeAsyncListener
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object Notifications : HudElement(
    name = "Notifications",
    category = Category.CLIENT,
    description = "Shows notifications"
) {

    private var cacheWidth = 90.0
    private var cacheHeight = 23.0

    override val hudWidth: Float
        get() = cacheWidth.toFloat()
    override val hudHeight: Float
        get() = cacheHeight.toFloat()

    private val notifications = mutableListOf<Notification>()

    init {
        safeAsyncListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.END) return@safeAsyncListener

            val removalList = notifications.filter { notification ->
                notification.startTime + notification.duration < System.currentTimeMillis()
            }
            notifications.removeAll(removalList)

            cacheHeight = if (notifications.isEmpty()) 23.0 else notifications.size * 26.0
        }
    }

    override fun renderHud(vertexHelper: VertexHelper) {
        super.renderHud(vertexHelper)

        runSafe {
            notifications.forEachIndexed { index, notification ->
                GlStateManager.pushMatrix()

                GlStateManager.translate(0.0, index * 28.0, 0.0)
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

        val textWidth = FontRenderAdapter.getStringWidth(notification.text, 0.7f, true)
        val width: Double = if(textWidth > 88) textWidth + 25.0 else 90.0
        val clearWidth = width - 2

        val timeout = (clearWidth * elapsedTime) / duration
        val timeoutBarWidth = if (timeout > clearWidth) clearWidth else if (timeout < 0) 0 else timeout

        val borderPosBegin = if(dockingH == HAlign.RIGHT) Vec2d(width, 0.0) else Vec2d(0.0, 0.0)
        val borderPosEnd = if(dockingH == HAlign.RIGHT) Vec2d(clearWidth, 23.0) else Vec2d(2.0, 23.0)

        val timeoutBarPosBegin = if(dockingH == HAlign.RIGHT) Vec2d(timeoutBarWidth.toDouble(), 23.0)
        else Vec2d(2.0, 23.0)
        val timeoutBarPosEnd = if(dockingH == HAlign.RIGHT) Vec2d(clearWidth, 22.0)
        else Vec2d(clearWidth - timeoutBarWidth.toDouble(), 22.0)

        // Draw background
        RenderUtils2D.drawRectFilled(vertexHelper, Vec2d.ZERO, Vec2d(width, 23.0), GuiColors.backGround)

        // Draw timeout bar
        RenderUtils2D.drawRectFilled(vertexHelper, timeoutBarPosBegin, timeoutBarPosEnd, color)

        // Draw border
        RenderUtils2D.drawRectFilled(vertexHelper, borderPosBegin, borderPosEnd, color)

        // Draw text
        FontRenderAdapter.drawString(notification.text, 10.0f, 10.5f, true, ColorHolder(), 0.7f, true)
    }

    fun addNotification(notification: Notification) {
        notifications.add(notification)
    }
}

data class Notification(
    val text: String,
    val type: NotificationType,
    val duration: Int,
    val startTime: Long = System.currentTimeMillis(),
)

enum class NotificationType {
    INFO,
    WARNING,
    ERROR
}
