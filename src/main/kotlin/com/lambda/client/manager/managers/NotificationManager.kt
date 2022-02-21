package com.lambda.client.manager.managers

import com.lambda.client.LambdaMod
import com.lambda.client.manager.Manager
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import java.util.*

object NotificationManager : Manager {
    private val pendingNotifications: Queue<String> = LinkedList()

    init {
        safeListener<EntityJoinWorldEvent> {
            MessageSendHelper.sendChatMessage("Hi ${it.entity.name}")
            while (pendingNotifications.isNotEmpty()) {
                LambdaMod.LOG.info("TETS")
                MessageSendHelper.sendErrorMessage(pendingNotifications.poll())
            }
        }
    }

    fun registerNotification(message: String) {
        pendingNotifications.add(message)
    }
}