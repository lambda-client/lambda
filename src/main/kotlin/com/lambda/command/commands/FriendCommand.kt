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
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.uuid
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.command.CommandRegistry.prefix
import com.lambda.command.LambdaCommand
import com.lambda.config.categories.FriendCategory
import com.lambda.interaction.handler.handlers.FriendHandler
import com.lambda.interaction.handlers.FriendImporter
import com.lambda.network.mojang.getProfile
import com.lambda.threading.runIO
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import kotlinx.coroutines.runBlocking
import net.minecraft.command.CommandSource.suggestMatching
import java.awt.Color
import java.io.File
import java.util.*

@Suppress("unused")
object FriendCommand : LambdaCommand(
    name = "friends",
    usage = "friends <add <name> | add-uuid <uuid> | remove <name> | remove-uuid <uuid> | import [path]>",
    description = "Add, remove or import friends",
    examples = listOf("friends import", "friends import <path>")
) {
    override fun CommandBuilder.create() {
        execute {
            runIO {
                info(
                    buildText {
                        if (FriendHandler.friends.isEmpty()) {
                            literal("You have no friends yet. Go make some! :3\n")
                        } else {
                            literal("Your friends (${FriendHandler.friends.size}):\n")

                            FriendHandler.friends.forEachIndexed { index, uuid ->
                                val displayName = FriendHandler.friendDisplayName(uuid)

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
                            clickEvent = ClickEvents.openFile(FriendCategory.primaryFile.path),
                        ) {
                            literal("Click to open your friends list as a file")
                        }
                    }
                )

                if (FriendHandler.friends.any { FriendHandler.gameProfile(it) == null }) {
                    runIO { FriendHandler.resolveMissing() }
                }
            }
        }

        required(literal("add")) {
            required(string("player name")) { player ->
                suggests { _, builder ->
                    val playerNames = mc.networkHandler
                        ?.playerList
                        ?.map { it.profile.name }
                        ?.toList() ?: emptyList()

                    suggestMatching(playerNames, builder)
                }

                executeWithResult {
                    val name = player().value()

                    runBlocking {
                        val profile = FriendHandler.latestGameProfile(name)
                            ?: return@runBlocking failure("Could not find the player")

                        if (FriendHandler.isFriend(profile.id))
                            return@runBlocking failure("This player is already in your friend list")

                        FriendHandler.befriend(profile)

                        info(FriendHandler.befriendedText(profile.name))
                        success()
                    }
                }
            }
        }

        required(literal("add-uuid")) {
            required(uuid("player uuid")) { player ->
                suggests { _, builder ->
                    val uuids = mc.networkHandler
                        ?.playerList
                        ?.map { it.profile.id.toString() }
                        ?.toList() ?: emptyList()

                    suggestMatching(uuids, builder)
                }

                executeWithResult {
                    val uuid = player().value()

                    if (FriendHandler.isFriend(uuid))
                        return@executeWithResult failure("This player is already in your friend list")

                    runBlocking {
                        val profile = FriendHandler.latestGameProfile(uuid)

                        if (profile != null) {
                            FriendHandler.befriend(profile)
                            info(FriendHandler.befriendedText(profile.name))
                        } else {
                            FriendHandler.befriend(uuid)
                            info(FriendHandler.befriendedText(uuid.toString()))
                        }

                        success()
                    }
                }
            }
        }

        required(literal("remove")) {
            required(string("player name")) { player ->
                suggests { _, builder ->
                    val playerNames = FriendHandler.friends.map { FriendHandler.friendDisplayName(it) }
                    suggestMatching(playerNames, builder)
                }

                executeWithResult {
                    val name = player().value()

                    runBlocking {
                        val uuid = FriendHandler.gameProfile(name)?.id
                            ?: getProfile(name).getOrNull()?.id
                            ?: runCatching { UUID.fromString(name) }.getOrNull()
                            ?: return@runBlocking failure("Could not resolve the player name")

                        if (!FriendHandler.isFriend(uuid))
                            return@runBlocking failure("This player is not in your friend list")

                        FriendHandler.unfriend(uuid)

                        info(FriendHandler.unfriendedText(name))
                        success()
                    }
                }
            }
        }

        required(literal("remove-uuid")) {
            required(uuid("player uuid")) { player ->
                suggests { _, builder ->
                    val uuids = mc.networkHandler
                        ?.playerList
                        ?.map { it.profile.id.toString() }
                        ?.toList() ?: emptyList()

                    suggestMatching(uuids, builder)
                }

                executeWithResult {
                    val uuid = player().value()

                    if (!FriendHandler.isFriend(uuid))
                        return@executeWithResult failure("This player is not in your friend list")

                    val displayName = FriendHandler.friendDisplayName(uuid)
                    FriendHandler.unfriend(uuid)

                    info(FriendHandler.unfriendedText(displayName))
                    success()
                }
            }
        }

        required(literal("import")) {
            optional(greedyString("path")) { file ->
                execute {
                    runIO {
                        val target = file?.let { File(it().value().trim().removeSurrounding("\"")) }
                            ?.toPath()
                            ?: runCatching { FriendImporter.pickFile() }.getOrElse {
                                this@FriendCommand.logError("The file picker could not be opened. Try $prefix$name import <path>.")
                                return@runIO
                            }
                            ?: run {
                                this@FriendCommand.warn("Import cancelled.")
                                return@runIO
                            }

                        FriendImporter.importFile(target)
                    }
                }
            }
        }
    }
}
