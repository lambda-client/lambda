package com.lambda.command

import com.lambda.command.CommandManager.dispatcher
import com.lambda.util.Nameable
import com.lambda.util.primitives.extension.CommandBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource

abstract class LambdaCommand(
    final override val name: String,
    val aliases: Set<String> = emptySet(),
    val usage: String = "",
    val description: String = "",
) : Nameable {

    init {
        (listOf(name) + aliases).forEach {
            val argument = LiteralArgumentBuilder.literal<CommandSource>(it)
            argument.create()
            dispatcher.register(argument)
        }
    }

    abstract fun CommandBuilder.create()
}