
package com.minato.task.tasks

import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.interaction.handlers.ContainerHandler
import com.minato.interaction.handlers.ContainerHandler.findContainerWithMaterial
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.container.containers.HotbarContainer
import com.minato.task.Task
import com.minato.threading.runSafeAutomated

class AcquireMaterialTask @Ta5kBuilder constructor(
    val selection: StackSelection,
    automated: Automated
) : Task<StackSelection>(), Automated by automated {
    override val name: String
        get() = "Acquiring $selection"

    override fun SafeContext.onStart() {
        runSafeAutomated {
            selection.findContainerWithMaterial()
                ?.transferByTask(selection, HotbarContainer)
                ?.finally {
                    success(selection)
                }?.execute(this@AcquireMaterialTask)
                ?: failure(ContainerHandler.NoContainerFound(selection)) // ToDo: Create crafting path
        }
    }

    companion object {
        @Ta5kBuilder
        fun Automated.acquire(selection: () -> StackSelection) =
            AcquireMaterialTask(selection(), this)
    }
}
