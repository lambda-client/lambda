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

package com.lambda.command.commands

import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.Configuration
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder

object ConfigCommand : LambdaCommand(
    name = "config",
    aliases = setOf("cfg"),
    usage = "config <save | load>",
    description = "Save or load the configuration files"
) {
    override fun CommandBuilder.create() {
        required(literal("save")) {
            executeWithResult {
                Configuration.configurations.forEach { config ->
                    config.trySave(true)
                }
                this@ConfigCommand.info("Saved ${Configuration.configurations.size} configuration files.")
                return@executeWithResult success()
            }
        }
        required(literal("load")) {
            executeWithResult {
                Configuration.configurations.forEach { config ->
                    config.tryLoad()
                }
                this@ConfigCommand.info("Loaded ${Configuration.configurations.size} configuration files.")
                return@executeWithResult success()
            }
        }
    }
}
