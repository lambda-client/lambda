package com.lambda.client.module.modules.chat

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.MovementUtils.isMoving
import com.lambda.client.util.text.MessageDetection
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.MessageSendHelper.sendServerMessage
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

object LoginMessage : Module(
    name = "LoginMessage",
    description = "Sends a given message(s) to public chat on login",
    category = Category.CHAT,
    showOnArray = false,
    modulePriority = 150
) {
    private val sendAfterMoving by setting("Send After Moving", false, description = "Wait until you have moved after logging in")

    private val file = File(FolderUtils.lambdaFolder + "loginmsg.txt")
    private val loginMessages = CopyOnWriteArrayList<String>()
    private var sent = false
    private var moved = false

    init {
        onEnable {
            if (file.exists()) {
                defaultScope.launch(Dispatchers.IO) {
                    try {
                        file.forEachLine {
                            if (it.isNotBlank()) loginMessages.add(it.trim())
                        }
                        MessageSendHelper.sendChatMessage("$chatName Loaded ${loginMessages.size} login messages!")
                    } catch (e: Exception) {
                        MessageSendHelper.sendErrorMessage("$chatName Failed loading login messages, $e")
                        disable()
                    }
                }
            } else {
                file.createNewFile()
                MessageSendHelper.sendErrorMessage("$chatName Login Messages file not found!" +
                    ", please add them in the &7loginmsg.txt&f under the &7.minecraft/lambda&f directory.")
                disable()
            }
        }

        onDisable {
            loginMessages.clear()
        }

        listener<ConnectionEvent.Disconnect> {
            sent = false
            moved = false
        }

        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.END) return@safeListener

            if (!sent && (!sendAfterMoving || moved)) {
                for (message in loginMessages) {
                    if (MessageDetection.Command.LAMBDA detect message) {
                        MessageSendHelper.sendLambdaCommand(message)
                    } else {
                        sendServerMessage(message)
                    }
                }

                sent = true
            }

            if (!moved) moved = player.isMoving
        }
    }
}
