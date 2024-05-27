package com.lambda.module.modules.debug

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.tasks.AcquireMaterial.Companion.acquire
import com.lambda.util.Communication.info
import net.minecraft.item.Items

object ContainerTest : Module(
    name = "ContainerTest",
    description = "Test container",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    init {
        listener<TickEvent.Pre> {
//            info(task.info)
        }

        onEnable {
            acquire {
                Items.OBSIDIAN.select()
            }.start(null)
        }
    }
}