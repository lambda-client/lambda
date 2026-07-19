
package com.minato.command.commands

import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.integer
import com.minato.brigadier.argument.itemStack
import com.minato.brigadier.argument.literal
import com.minato.brigadier.argument.string
import com.minato.brigadier.argument.value
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.config.automation.AutomationConfig
import com.minato.interaction.handlers.ContainerHandler
import com.minato.interaction.handlers.ContainerHandler.findContainersWithMaterial
import com.minato.interaction.handlers.ContainerHandler.findContainersWithSpace
import com.minato.interaction.material.StackSelection.Companion.selectStack
import com.minato.task.RootTask
import com.minato.task.Task
import com.minato.threading.runSafeAutomated
import com.minato.util.CommunicationUtils.info
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching

object TransferCommand : MinatoCommand(
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
