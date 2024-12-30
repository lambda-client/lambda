/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.material.transfer

import com.lambda.context.SafeContext
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.util.Communication.info

abstract class TransferResult : Task<Unit>() {
    data class Transfer(
        val selection: StackSelection,
        val from: MaterialContainer,
        val to: MaterialContainer
    ) : TransferResult() {
        override val name = "Transfer of [$selection] from [${from.name}] to [${to.name}]"

        override fun SafeContext.onStart() {
            from.withdraw(selection).then {
                to.deposit(selection).finally {
                    success()
                }
            }.execute(this@Transfer)
        }
    }

    data object NoSpace : TransferResult() {
        override val name = "No space left in the target container"

        // ToDo: Needs inventory space resolver. compressing or disposing
        override fun SafeContext.onStart() {
            failure("No space left in the target container")
        }
    }

    data class MissingItems(val missing: Int) : TransferResult() {
        override val name = "Missing $missing items"

        // ToDo: Find other satisfying permutations
        override fun SafeContext.onStart() {
            failure("Missing $missing items")
        }
    }
}
