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

import com.lambda.Lambda.mc
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.configurations.FriendConfig
import com.lambda.friend.FriendManager
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import java.awt.Color

object FriendCommand : LambdaCommand(
    name = "friend",
    usage = "friend <add | remove> <name>",
    description = "Add or remove a friend"
) {
    override fun CommandBuilder.create() {
        execute {
            this@FriendCommand.info(
                buildText {
                    styled(
                        color = Color.CYAN,
                        underlined = true,
                        clickEvent = ClickEvents.openFile(FriendConfig.primary.absolutePath),
                    ) {
                        literal("Click to open your friend list")
                    }
                }
            )
        }

        required(literal("add")) {
            required(string("player name")) { player ->
                suggests { _, builder ->
                    mc.networkHandler
                        ?.playerList
                        ?.filter { it.profile != mc.gameProfile }
                        ?.map { it.profile.name }
                        ?.forEach { builder.suggest(it)  }

                    builder.buildFuture()
                }

                executeWithResult {
                    val name = player().value()
                    if (FriendManager.contains(name))
                        return@executeWithResult failure("This player is already in your friend list")

                    val id = mc.networkHandler
                        ?.playerList
                        ?.firstOrNull {
                            it.profile.name == name &&
                            it.profile != mc.gameProfile
                        } ?: return@executeWithResult failure("Could not find the player on the server")

                    FriendManager.add(id.profile)

                    this@FriendCommand.info(buildText {
                        color(Color.GREEN) {
                            literal("Added ")
                            color(Color.CYAN) {
                                literal(name)
                                color(Color.WHITE) { literal(" to your friend list") }
                            }
                        }
                    })

                    return@executeWithResult success()
                }
            }
        }

        required(literal("remove")) {
            required(string("player name")) { player ->
                suggests { _, builder ->
                    FriendManager.friends.map { it.name }
                        .forEach { builder.suggest(it) }

                    builder.buildFuture()
                }

                executeWithResult {
                    val name = player().value()
                    val profile = FriendManager.gameProfile(name)
                        ?: return@executeWithResult failure("This player is not in your friend list")

                    FriendManager.remove(profile)

                    this@FriendCommand.info(buildText {
                        color(Color.RED) {
                            literal("Removed ")
                            color(Color.CYAN) {
                                literal(profile.name)
                                color(Color.WHITE) { literal(" from your friend list") }
                            }
                        }
                    })

                    return@executeWithResult success()
                }
            }
        }
    }
}
