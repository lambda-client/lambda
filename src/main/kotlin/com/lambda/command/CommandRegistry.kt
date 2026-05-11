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

package com.lambda.command

import com.lambda.command.CommandManager.dispatcher
import com.lambda.config.Configurable
import com.lambda.config.configurations.LambdaConfig
import com.lambda.core.Loadable
import com.lambda.util.reflections.getInstances
import com.mojang.brigadier.tree.CommandNode

/**
 * The [CommandRegistry] object is responsible for managing all [LambdaCommand] instances in the system.
 */
object CommandRegistry : Configurable(LambdaConfig), Loadable {
    override val priority get() = -2
    override val name = "command"
    val prefix by setting("prefix", ';')

    val commands = getInstances<LambdaCommand>().toMutableList()

    override fun load() = "Loaded ${commands.size} commands with ${dispatcher.root.children()} possible command paths."

    private fun CommandNode<*>.children(): Int = children.sumOf { it.children() } + 1
}
