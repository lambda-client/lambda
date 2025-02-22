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

import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.client.Discord.partyCreate
import com.lambda.module.modules.client.Discord.partyJoin
import com.lambda.module.modules.client.Discord.partyLeave
import com.lambda.module.modules.client.Discord.rpc
import com.lambda.network.api.v1.endpoints.leaveParty
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import com.lambda.util.extension.CommandBuilder

object DiscordCommand : LambdaCommand(
    name = "discord",
    description = "Discord Rich Presence commands",
    usage = "rpc <create | leave | delete | join [id] | accept [user] | refuse [user]>"
) {
    override fun CommandBuilder.create() {
        required(literal("create")) {
            execute {
                runSafe { partyCreate() }
            }
        }

        required(literal("leave")) {
            execute {
                runSafe { partyLeave() }
            }
        }

        required(literal("delete")) {
            execute {
                runSafe { partyCreate() }
            }
        }

        required(literal("join")) {
            required(word("id")) { id ->
                execute {
                    runSafe { partyJoin(id().value()) }
                }
            }
        }

        required(literal("accept")) {
            required(word("user")) { user ->
                execute {
                    runConcurrent { rpc.activityManager.acceptJoinRequest(user().value()) }
                }
            }
        }

        required(literal("refuse")) {
            required(word("user")) { user ->
                execute {
                    runConcurrent { rpc.activityManager.refuseJoinRequest(user().value()) }
                }
            }
        }
    }
}
