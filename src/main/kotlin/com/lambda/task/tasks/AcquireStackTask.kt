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
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.select
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.threading.runSafeAutomated
import net.minecraft.screen.slot.Slot

@Ta5kBuilder
context(automated: Automated)
fun acquireStack(selection: StackSelection) =
    AcquireStackTask(selection, automated)

@Ta5kBuilder
context(automated: Automated)
fun acquireStack(selection: () -> StackSelection) =
    AcquireStackTask(selection(), automated)

class AcquireStackTask @Ta5kBuilder internal constructor(
    val selection: StackSelection,
    automated: Automated
) : Task<Slot>(), Automated by automated {
    override val name: String
        get() = "Acquiring $selection"

    override fun SafeContext.onStart() {
        runSafeAutomated {
            selection.transfer(toSelection = HotbarContainer.select())
                .onSuccess { slot -> success(slot) }
                .start()
        }
    }
}

class NoContainerFound(selection: StackSelection) : Exception("No container found matching $selection")
