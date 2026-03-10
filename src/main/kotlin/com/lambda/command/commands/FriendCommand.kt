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
import com.lambda.threading.runIO
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.util.UUID

object FriendCommand : LambdaCommand(
    name = "friends",
    usage = "friends <add <name> | add-uuid <uuid> | remove <name> | remove-uuid <uuid>>",
    description = "Add or remove a friend"
) {
    override fun CommandBuilder.create() {
        execute {
            runIO {
                info(
                    buildText {
                        if (FriendManager.friends.isEmpty()) {
                            literal("You have no friends yet. Go make some! :3\n")
                        } else {
                            literal("Your friends (${FriendManager.friends.size}):\n")

                            FriendManager.friends.forEachIndexed { index, uuid ->
                                val profile = FriendManager.latestGameProfile(uuid)
                                val displayName = profile?.name ?: uuid.toString()

                                literal("   ${index + 1}. $displayName ")
                                styled(
                                    color = Color.RED,
                                    clickEvent = ClickEvents.suggestCommand(";friends remove $displayName")
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

                    runBlocking {
                        val profile = FriendManager.latestGameProfile(name)
                            ?: return@runBlocking failure("Could not find the player")

                        if (mc.gameProfile.id == profile.id)
                            return@runBlocking failure("You can't befriend yourself")

                        if (FriendManager.isFriend(profile.id))
                            return@runBlocking failure("This player is already in your friend list")

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

                    if (mc.gameProfile.id == uuid)
                        return@executeWithResult failure("You can't befriend yourself")

                    if (FriendManager.isFriend(uuid))
                        return@executeWithResult failure("This player is already in your friend list")

                    runBlocking {
                        val profile = FriendManager.latestGameProfile(uuid)

                        if (profile != null) {
                            FriendManager.befriend(profile)
                            info(FriendManager.befriendedText(profile.name))
                        } else {
                            FriendManager.befriend(uuid)
                            info(FriendManager.befriendedText(uuid.toString()))
                        }

                        success()
                    }
                }
            }
        }

        required(literal("remove")) {
            required(string("player name")) { player ->
                suggests { _, builder ->
                    FriendManager.friends
                        .map { FriendManager.friendDisplayName(it) }
                        .forEach { builder.suggest(it) }

                    builder.buildFuture()
                }

                executeWithResult {
                    val name = player().value()

                    runBlocking {
                        val uuid = FriendManager.gameProfile(name)?.id
                            ?: getProfile(name).getOrNull()?.id
                            ?: runCatching { UUID.fromString(name) }.getOrNull()
                            ?: return@runBlocking failure("Could not resolve the player name")

                        if (!FriendManager.isFriend(uuid))
                            return@runBlocking failure("This player is not in your friend list")

                        FriendManager.unfriend(uuid)

                        info(FriendManager.unfriendedText(name))
                        success()
                    }
                }
            }
        }

        required(literal("remove-uuid")) {
            required(uuid("player uuid")) { player ->
                executeWithResult {
                    val uuid = player().value()

                    if (!FriendManager.isFriend(uuid))
                        return@executeWithResult failure("This player is not in your friend list")

                    val displayName = FriendManager.friendDisplayName(uuid)
                    FriendManager.unfriend(uuid)

                    info(FriendManager.unfriendedText(displayName))
                    success()
                }
            }
        }
    }
}
