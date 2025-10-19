/*
 * Copyright 2025 Lambda
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

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.task.Task

abstract class TransferResult : Task<Unit>() {
    data class ContainerTransfer(
        val selection: StackSelection,
        val from: MaterialContainer,
        val to: MaterialContainer,
        val automated: Automated
    ) : TransferResult(), Automated by automated {
        override val name = "Container Transfer of [$selection] from [${from.name}] to [${to.name}]"

        override fun SafeContext.onStart() {
            val withdrawal = from.withdraw(selection)
            val deposit = to.deposit(selection)

            val task = when {
                withdrawal != null && deposit != null -> {
                    withdrawal.then {
                        deposit.finally { success() }
                    }
                }
                withdrawal != null -> {
                    withdrawal.finally { success() }
                }
                deposit != null -> {
                    deposit.finally { success() }
                }
                else -> null
            }

            task?.execute(this@ContainerTransfer)
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
