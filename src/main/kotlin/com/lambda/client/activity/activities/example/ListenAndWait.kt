package com.lambda.client.activity.activities.example

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.Exception

class ListenAndWait(
    private val message: String,
    override val timeout: Long
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            MessageSendHelper.sendChatMessage(message)
        }
    }

    override fun SafeClientEvent.onFailure(exception: Exception) =
        if (exception is TimeoutActivity.Companion.TimeoutException) {
            success()
            false
        } else {
            MessageSendHelper.sendChatMessage("Failure!")
            true
        }

    override fun addExtraInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder) {
        textComponent.add("Age", primaryColor)
        textComponent.add(age.toString(), secondaryColor)
    }
}