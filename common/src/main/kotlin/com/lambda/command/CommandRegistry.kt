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

import com.lambda.config.Configurable
import com.lambda.config.configurations.LambdaConfig
import com.lambda.core.Loadable
import com.lambda.util.reflections.getInstances

/**
 * The [CommandRegistry] object is responsible for managing all [LambdaCommand] instances in the system.
 */
object CommandRegistry : Configurable(LambdaConfig), Loadable {
    override val name = "command"
    val prefix by setting("prefix", ';')

    val commands = getInstances<LambdaCommand> { forPackages("com.lambda.command.commands") }.toMutableList()

    override fun load(): String {
        return "Registered ${commands.size} commands"
    }
}
