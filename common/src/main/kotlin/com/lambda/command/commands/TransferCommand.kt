package com.lambda.command.commands

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.itemStack
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.interaction.material.ContainerManager
import com.lambda.interaction.material.ContainerManager.containerMatchSelection
import com.lambda.interaction.material.ContainerManager.findContainerWithSelection
import com.lambda.interaction.material.ContainerManager.transfer
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.transfer.TransferResult
import com.lambda.util.Communication.info
import com.lambda.util.primitives.extension.CommandBuilder

object TransferCommand : LambdaCommand(
    name = "transfer",
    usage = "transfer <item> <amount> <to>",
    description = "Transfer items to a container"
) {
    override fun CommandBuilder.create() {
        required(itemStack("stack", registry)) { stack ->
            required(integer("amount")) { amount ->
                required(string("from")) { from ->
                    suggests { ctx, builder ->
                        val selection = selectStack(amount(ctx).value()) {
                            isItem(stack(ctx).value().item)
                        }
                        containerMatchSelection(selection).forEach {
                            builder.suggest("\"${it.name} with ${it.available(selection)}\"")
                        }
                        builder.buildFuture()
                    }
                    required(string("to")) { to ->
                        suggests { ctx, builder ->
                            val selection = selectStack(amount(ctx).value()) {
                                isItem(stack(ctx).value().item)
                            }
                            ContainerManager.container().forEach {
                                builder.suggest("\"${it.name} with space left ${it.spaceLeft(selection)}\"")
                            }
                            builder.buildFuture()
                        }
                        executeWithResult {
                            val selection = selectStack(amount().value()) {
                                isItem(stack().value().item)
                            }
                            val fromContainer = ContainerManager.container().find {
                                it.name == from().value().split(" with ").firstOrNull()
                            } ?: return@executeWithResult CommandResult.failure("From container not found")

                            val toContainer = ContainerManager.container().find {
                                it.name == to().value().split(" with ").firstOrNull()
                            } ?: return@executeWithResult CommandResult.failure("To container not found")

                            when (val result = fromContainer.transfer(selection, toContainer)) {
                                is TransferResult.Success -> {
                                    info("Transferring $selection from ${fromContainer.name} to ${toContainer.name}")
                                    result.solve.start(null)
                                    return@executeWithResult CommandResult.success()
                                }
                                is TransferResult.MissingItems -> {
                                    return@executeWithResult CommandResult.failure("Missing items: ${result.missing}")
                                }
                                is TransferResult.NoSpace -> {
                                    return@executeWithResult CommandResult.failure("No space in ${toContainer.name}")
                                }
                            }

                            return@executeWithResult CommandResult.success()
                        }
                    }
                }
            }
        }
    }
}