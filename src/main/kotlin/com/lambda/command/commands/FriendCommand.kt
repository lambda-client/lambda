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

package com.lambda.command.commands

import com.lambda.Lambda.mc
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.uuid
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.configurations.FriendConfig
import com.lambda.friend.FriendManager
import com.lambda.network.mojang.getProfile
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import kotlinx.coroutines.runBlocking
import java.awt.Color

object FriendCommand : LambdaCommand(
    name = "friends",
    usage = "friends <add <name> | add-uuid <uuid> | remove <name>>",
    description = "Add or remove a friend"
) {
    override fun CommandBuilder.create() {
        execute {
            info(
                buildText {
                    if (FriendManager.friends.isEmpty()) {
                        literal("You have no friends yet. Go make some! :3\n")
                    } else {
                        literal("Your friends (${FriendManager.friends.size}):\n")

                        FriendManager.friends.forEachIndexed { index, gameProfile ->
                            literal("   ${index + 1}. ${gameProfile.name} ")
                            styled(
                                color = Color.RED,
                                clickEvent = ClickEvents.suggestCommand(";friends remove ${gameProfile.name}")
                            ) {
                                literal("x\n")
                            }
                        }
                    }

                    literal("\n")
                    styled(
                        color = Color.CYAN,
                        underlined = true,
                        clickEvent = ClickEvents.openFile(FriendConfig.primary.path),
                    ) {
                        literal("Click to open your friends list as a file")
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
                        ?.forEach { builder.suggest(it) }

                    builder.buildFuture()
                }

                executeWithResult {
                    val name = player().value()

                    if (FriendManager.isFriend(name))
                        return@executeWithResult failure("This player is already in your friend list")

                    if (mc.gameProfile.name == name)
                        return@executeWithResult failure("You can't befriend yourself")

                    runBlocking {
                        val profile = mc.networkHandler
                            ?.playerList
                            ?.map { it.profile }
                            ?.firstOrNull { it.name == name }
                            ?: getProfile(name)
                                .getOrElse { return@runBlocking failure("Could not find the player") }

                        FriendManager.befriend(profile)

                        info(FriendManager.befriendedText(profile.name))
                        success()
                    }
                }
            }
        }

        required(literal("add-uuid")) {
            required(uuid("player uuid")) { player ->
                suggests { _, builder ->
                    mc.networkHandler
                        ?.playerList
                        ?.filter { it.profile != mc.gameProfile }
                        ?.map { it.profile.id }
                        ?.forEach { builder.suggest(it.toString()) }

                    builder.buildFuture()
                }

                executeWithResult {
                    val uuid = player().value()

                    if (FriendManager.isFriend(uuid))
                        return@executeWithResult failure("This player is already in your friend list")

                    if (mc.gameProfile.id == uuid)
                        return@executeWithResult failure("You can't befriend yourself")

                    runBlocking {
                        val profile = mc.networkHandler
                            ?.playerList
                            ?.map { it.profile }
                            ?.firstOrNull { it.id == uuid }
                            ?: getProfile(uuid)
                                .getOrElse { return@runBlocking failure("Could not find the player") }

                        FriendManager.befriend(profile)

                        info(FriendManager.befriendedText(profile.name))
                        success()
                    }
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

                    FriendManager.unfriend(profile)

                    info(FriendManager.unfriendedText(name))
                    success()
                }
            }
        }
    }
}
