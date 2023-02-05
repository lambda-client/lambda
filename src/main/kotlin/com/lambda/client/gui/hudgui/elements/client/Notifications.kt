package com.lambda.client.gui.hudgui.elements.client

import com.lambda.client.gui.hudgui.HudElement
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
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
        val timeout = (88 * elapsedTime) / duration
        val timeoutBarWidth = if (timeout > 88) 88 else if (timeout < 0) 0 else timeout

        // Draw background
        RenderUtils2D.drawRectFilled(vertexHelper, Vec2d.ZERO, Vec2d(90.0, 23.0), ColorHolder(0, 0, 0, 127))

        // Draw timeout bar
        RenderUtils2D.drawRectFilled(vertexHelper, Vec2d(timeoutBarWidth.toDouble(), 23.0), Vec2d(88.0, 22.0), color)

        // Draw right border
        RenderUtils2D.drawRectFilled(vertexHelper, Vec2d(90.0, 0.0), Vec2d(88.0, 23.0), color)

        // Draw text
        FontRenderAdapter.drawString(notification.text, 10.0f, 9.0f, true, ColorHolder(), 0.6f, true)
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
