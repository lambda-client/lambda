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

package com.lambda.command

import com.lambda.command.CommandManager.dispatcher
import com.lambda.util.Nameable
import com.lambda.util.extension.CommandBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.server.command.CommandManager

abstract class LambdaCommand(
    final override val name: String,
    val aliases: Set<String> = emptySet(),
    val usage: String = "",
    val description: String = "",
) : Nameable {
    val registry: CommandRegistryAccess by lazy {
        CommandManager.createRegistryAccess(BuiltinRegistries.createWrapperLookup())
    }

    // ToDo: Include usage and description in the help command
    init {
        (listOf(name) + aliases).forEach {
            val argument = LiteralArgumentBuilder.literal<CommandSource>(it.lowercase())
            argument.create()
            dispatcher.register(argument)
        }
    }

    abstract fun CommandBuilder.create()
}
