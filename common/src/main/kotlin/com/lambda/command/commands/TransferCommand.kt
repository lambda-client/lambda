package com.lambda.command.commands

import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.*
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.interaction.material.ContainerManager
import com.lambda.interaction.material.ContainerManager.containerMatchSelection
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.transfer.TransferResult
import com.lambda.util.Communication.info
import com.lambda.util.primitives.extension.CommandBuilder

object TransferCommand : LambdaCommand(
    name = "transfer",
    usage = "transfer <move|cancel|undo> <item> <amount> <to>",
    description = "Transfer items from anywhere to anywhere",
) {
    private var lastTransfer: TransferResult.Success? = null

    override fun CommandBuilder.create() {
        required(itemStack("stack", registry)) { stack ->
            required(integer("amount")) { amount ->
                required(string("from")) { from ->
                    suggests { ctx, builder ->
                        val count = amount(ctx).value()
                        val selection = selectStack(count) {
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
                                val space = it.spaceLeft(selection)
                                if (space > 0) builder.suggest("\"${it.name} with $space space left\"")
                            }
                            builder.buildFuture()
                        }
                        executeWithResult {
                            val selection = selectStack(amount().value()) {
                                isItem(stack().value().item)
                            }
                            val fromContainer = ContainerManager.container().find {
                                it.name == from().value().split(" with ").firstOrNull()
                            } ?: return@executeWithResult failure("From container not found")

                            val toContainer = ContainerManager.container().find {
                                it.name == to().value().split(" with ").firstOrNull()
                            } ?: return@executeWithResult failure("To container not found")

                            when (val result = fromContainer.transfer(selection, toContainer)) {
                                is TransferResult.Success -> {
                                    info("Transferring $selection from ${fromContainer.name} to ${toContainer.name}")
                                    lastTransfer = result
                                    result.solve.start(null)
                                    return@executeWithResult success()
                                }
                                is TransferResult.MissingItems -> {
                                    return@executeWithResult failure("Missing items: ${result.missing}")
                                }
                                is TransferResult.NoSpace -> {
                                    return@executeWithResult failure("No space in ${toContainer.name}")
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
                lastTransfer?.solve?.cancel() ?: run {
                    return@executeWithResult failure("No transfer to cancel")
                }
                lastTransfer = null
                success()
            }
        }

        required(literal("undo")) {
            executeWithResult {
                lastTransfer?.undo ?: run {
                    return@executeWithResult failure("No transfer to undo")
                }
                success()
            }
        }
    }
}