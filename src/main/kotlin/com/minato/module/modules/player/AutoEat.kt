
package com.minato.module.modules.player

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.blocks.EatConfig.Companion.reasonEating
import com.minato.config.withEdits
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask.run
import com.minato.task.tasks.EatTask
import com.minato.task.tasks.EatTask.Companion.eat
import com.minato.threading.runSafeAutomated

@Suppress("unused")
object AutoEat : Module(
    name = "AutoEat",
    description = "Eats food when you are hungry",
    tag = ModuleTag.PLAYER,
) {
    private var eatTask: EatTask? = null

    init {
		setDefaultAutomationConfig()
            .withEdits {
			    hideAllExcept(::eatConfig)
		    }

        listen<TickEvent.Pre> {
            val reason = runSafeAutomated { reasonEating() }
            if (eatTask != null || !reason.shouldEat()) return@listen

            val task = eat()
            task.finally { eatTask = null }
            task.run()
            eatTask = task
        }

        onDisable {
            eatTask?.cancel()
            eatTask = null
        }
    }
}
