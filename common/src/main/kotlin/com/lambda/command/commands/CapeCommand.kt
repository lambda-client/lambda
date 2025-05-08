/*
 * Copyright 2025 Lambda
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

import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.network.CapeManager
import com.lambda.network.CapeManager.updateCape
import com.lambda.network.NetworkManager
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.extension.CommandBuilder

object CapeCommand : LambdaCommand(
    name = "cape",
    usage = "set <id>",
    description = "Sets your cape",
)  {
    override fun CommandBuilder.create() {
        required(literal("set")) {
            required(string("id")) { id ->
                suggests { _, builder ->
                    CapeManager.capeList
                        .forEach { builder.suggest(it) }

                    builder.buildFuture()
                }

                execute {
                    val cape = id().value()
                    updateCape(cape) { error ->
                        if (error != null) logError("Could not update your cape", error)
                        else info("Updated your cape to $cape")
                    }
                }
            }
        }
    }
}
