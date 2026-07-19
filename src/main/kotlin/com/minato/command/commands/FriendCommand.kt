
package com.minato.command.commands

import com.minato.Minato.mc
import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.literal
import com.minato.brigadier.argument.string
import com.minato.brigadier.argument.uuid
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.config.categories.FriendCategory
import com.minato.interaction.handlers.FriendHandler
import com.minato.network.mojang.getProfile
import com.minato.threading.runIO
import com.minato.util.CommunicationUtils.info
import com.minato.util.extension.CommandBuilder
import com.minato.util.text.ClickEvents
import com.minato.util.text.buildText
import com.minato.util.text.literal
import com.minato.util.text.styled
import kotlinx.coroutines.runBlocking
import net.minecraft.command.CommandSource.suggestMatching
import java.awt.Color
import java.util.*

@Suppress("unused")
object FriendCommand : MinatoCommand(
    name = "friends",
    usage = "friends <add <name> | add-uuid <uuid> | remove <name> | remove-uuid <uuid>>",
    description = "Add or remove a friend"
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
                                val profile = FriendHandler.latestGameProfile(uuid)
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
                            clickEvent = ClickEvents.openFile(FriendCategory.primaryFile.path),
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
    }
}
