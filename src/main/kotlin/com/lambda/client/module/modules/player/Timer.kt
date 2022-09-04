package com.lambda.client.module.modules.player

import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.util.MovementUtils
import net.minecraftforge.fml.common.gameevent.TickEvent

object Timer : Module(
    name = "Timer",
    description = "Changes your client tick speed",
    category = Category.PLAYER,
    modulePriority = 500
) {
    private val onlyWhenInputting by setting("Only When Inputting", false)
    private val slow by setting("Slow Mode", false)
    private val tickNormal by setting("Tick N", 2.0f, 1f..10f, 0.1f, { !slow })
    private val tickSlow by setting("Tick S", 8f, 1f..10f, 0.1f, { slow })

    init {
        onDisable {
            resetTimer()
        }

        listener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@listener

            if (ClickGUI.isEnabled) {
                resetTimer()
                return@listener
            }

            val multiplier = if (!slow) tickNormal else tickSlow / 10.0f
            if (onlyWhenInputting) {
                if (MovementUtils.isInputting) modifyTimer(50.0f / multiplier)
            } else modifyTimer(50.0f / multiplier)
        }
    }
}