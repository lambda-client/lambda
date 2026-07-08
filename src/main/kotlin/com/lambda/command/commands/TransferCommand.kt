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
import com.lambda.config.automation.AutomationConfig
import com.lambda.interaction.handlers.ContainerHandler
import com.lambda.interaction.handlers.ContainerHandler.findContainersWithMaterial
import com.lambda.interaction.handlers.ContainerHandler.findContainersWithSpace
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.selectStack
import com.lambda.task.RootTask
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching

object TransferCommand : LambdaCommand(
    name = "transfer",
    usage = "transfer <move | cancel | undo> <item> <amount> <to>",
    description = "Transfer items from anywhere to anywhere",
) {
    private var lastContainerTransfer: Task<*>? = null

    override fun CommandBuilder.create() {
        required(itemStack("stack", registry)) { stack ->
            required(integer("amount", 1)) { amount ->
                required(string("from")) { from ->
                    suggests { ctx, builder ->
                        val selection = selectStack(amount(ctx).value()) {
                            isItem(stack(ctx).value().item)
                        }
                        AutomationConfig.DEFAULT.runSafeAutomated {
                            val containers = selection.findContainersWithMaterial()
                            val indexedContainers = containers.withIndex()

                            suggestMatching(
                                indexedContainers,
                                builder,
                                { (index, container) ->
                                    "\"${index + 1}. ${container.name}\""
                                },
                                { (_, container) ->
                                    container.description(selection)
                                }
                            )
                        } ?: builder.buildFuture()
                    }
                    required(string("to")) { to ->
                        suggests { ctx, builder ->
                            val selection = selectStack(amount(ctx).value()) {
                                isItem(stack(ctx).value().item)
                            }
                            AutomationConfig.DEFAULT.runSafeAutomated {
                                val containers = selection.findContainersWithSpace()
                                val indexedContainers = containers.withIndex()

                                suggestMatching(
                                    indexedContainers,
                                    builder,
                                    { (index, container) ->
                                        "\"${index + 1}. ${container.name}\""
                                    },
                                    { (_, container) ->
                                        container.description(selection)
                                    }
                                )
                            } ?: builder.buildFuture()
                        }
                        executeWithResult {
                            val selection = selectStack(amount().value()) {
                                isItem(stack().value().item)
                            }
                            AutomationConfig.DEFAULT.runSafeAutomated {
                                val fromContainer = ContainerHandler.containers().find {
                                    it.name == from().value().split(".").last().trim()
                                } ?: return@executeWithResult failure("From container not found")

                                val toContainer = ContainerHandler.containers().find {
                                    it.name == to().value().split(".").last().trim()
                                } ?: return@executeWithResult failure("To container not found")

	                            fromContainer.transferByTask(selection, toContainer).execute(RootTask)
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
