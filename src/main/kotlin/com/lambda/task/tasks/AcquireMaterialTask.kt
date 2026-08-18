/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.task.tasks

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.handlers.ContainerHandler
import com.lambda.interaction.handlers.ContainerHandler.findContainerWithMaterial
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.HotbarContainer
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated

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
