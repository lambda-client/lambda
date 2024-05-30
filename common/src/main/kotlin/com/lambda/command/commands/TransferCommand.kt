package com.lambda.command.commands

import com.lambda.brigadier.execute
import com.lambda.command.LambdaCommand
import com.lambda.interaction.material.ContainerManager.transfer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.MainHandContainer
import com.lambda.util.primitives.extension.CommandBuilder
import net.minecraft.item.Items

object TransferCommand : LambdaCommand(
    name = "transfer",
    usage = "transfer <item> <amount> <to>",
    description = "Transfer items to a container"
) {
    override fun CommandBuilder.create() {
        execute {
            Items.OBSIDIAN.select().transfer(MainHandContainer).solve.start(null)
        }
    }
}