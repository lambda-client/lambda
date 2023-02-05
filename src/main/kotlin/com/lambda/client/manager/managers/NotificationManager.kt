package com.lambda.client.manager.managers

import com.lambda.client.gui.hudgui.elements.client.Notification
import com.lambda.client.gui.hudgui.elements.client.NotificationType
import com.lambda.client.gui.hudgui.elements.client.Notifications
import com.lambda.client.manager.Manager
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object NotificationManager : Manager {
    private val pendingNotifications: Queue<Notification> = LinkedList()

    init {
        safeListener<TickEvent.ClientTickEvent> {
            while (pendingNotifications.isNotEmpty()) {
                val notification = pendingNotifications.poll()
                Notifications.addNotification(notification)
            }
        }
    }

    fun registerNotification(message: String) {
        pendingNotifications.add(Notification(message, NotificationType.INFO, 3000))
    }

    fun registerNotification(message: String, type: NotificationType) {
        pendingNotifications.add(Notification(message, type, 3000))
    }
}