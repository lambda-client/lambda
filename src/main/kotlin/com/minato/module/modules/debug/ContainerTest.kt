
package com.minato.module.modules.debug

import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.material.StackSelection.Companion.select
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask.run
import com.minato.task.tasks.AcquireMaterialTask.Companion.acquire
import net.minecraft.item.Items

@Suppress("unused")
object ContainerTest : Module(
    name = "ContainerTest",
    description = "Test container",
    tag = ModuleTag.DEBUG,
) {
    init {
        listen<TickEvent.Pre> {
            // Task info can be inspected here when debugging
        }

        onEnable {
            acquire {
                Items.OBSIDIAN.select()
            }.run()
        }
    }
}
