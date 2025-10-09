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

package com.lambda.command.commands

import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.itemStack
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.context.AutomationConfig
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.ContainerManager.containerWithSpace
import com.lambda.interaction.material.transfer.TransferResult
import com.lambda.task.RootTask.run
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder

object TransferCommand : LambdaCommand(
    name = "transfer",
    usage = "transfer <move | cancel | undo> <item> <amount> <to>",
    description = "Transfer items from anywhere to anywhere",
) {
    private var lastContainerTransfer: TransferResult.ContainerTransfer? = null

    override fun CommandBuilder.create() {
        required(itemStack("stack", registry)) { stack ->
            required(integer("amount", 1)) { amount ->
                required(string("from")) { from ->
                    suggests { ctx, builder ->
                        val count = amount(ctx).value()
                        val selection = selectStack(count) {
                            isItem(stack(ctx).value().item)
                        }
                        with(AutomationConfig) {
                            selection.containerWithMaterial().forEachIndexed { i, container ->
                                builder.suggest("\"${i + 1}. ${container.name}\"", container.description(selection))
                            }
                        }
                        builder.buildFuture()
                    }
                    required(string("to")) { to ->
                        suggests { ctx, builder ->
                            val selection = selectStack(amount(ctx).value()) {
                                isItem(stack(ctx).value().item)
                            }
                            with(AutomationConfig) {
                                containerWithSpace(selection).forEachIndexed { i, container ->
                                    builder.suggest("\"${i + 1}. ${container.name}\"", container.description(selection))
                                }
                            }
                            builder.buildFuture()
                        }
                        executeWithResult {
                            val selection = selectStack(amount().value()) {
                                isItem(stack().value().item)
                            }
                            val fromContainer = ContainerManager.container().find {
                                it.name == from().value().split(".").last().trim()
                            } ?: return@executeWithResult failure("From container not found")

                            val toContainer = ContainerManager.container().find {
                                it.name == to().value().split(".").last().trim()
                            } ?: return@executeWithResult failure("To container not found")

                            with(AutomationConfig) {
                                when (val transaction = fromContainer.transfer(selection, toContainer)) {
                                    is TransferResult.ContainerTransfer -> {
                                        info("${transaction.name} started.")
                                        lastContainerTransfer = transaction
                                        transaction.finally {
                                            info("${transaction.name} completed.")
                                        }.run()
                                        return@executeWithResult success()
                                    }

                                    is TransferResult.MissingItems -> {
                                        return@executeWithResult failure("Missing items: ${transaction.missing}")
                                    }

                                    is TransferResult.NoSpace -> {
                                        return@executeWithResult failure("No space in ${toContainer.name}")
                                    }
                                }
                            }

                            return@executeWithResult success()
                        }
                    }
                }
            }
        }

        required(literal("cancel")) {
            executeWithResult {
                lastContainerTransfer?.cancel() ?: run {
                    return@executeWithResult failure("No transfer to cancel")
                }
                this@TransferCommand.info("$lastContainerTransfer cancelled")
                lastContainerTransfer = null
                success()
            }
        }
    }
}
