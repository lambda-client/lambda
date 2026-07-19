
package com.minato.command.commands

import com.google.gson.JsonSyntaxException
import com.minato.brigadier.CommandResult
import com.minato.brigadier.argument.greedyString
import com.minato.brigadier.argument.integer
import com.minato.brigadier.argument.literal
import com.minato.brigadier.argument.value
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.module.modules.player.Replay
import com.minato.util.FileUtils.listRecursive
import com.minato.util.FolderRegistry
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching
import kotlin.io.path.exists

@Suppress("unused")
object ReplayCommand : MinatoCommand(
    name = "replay",
    usage = "replay <play | load | save | prune>",
    description = "Play, load, save, or prune a replay"
) {
    override fun CommandBuilder.create() {
        required(literal("play")) {
            required(integer("index")) { index ->
                executeWithResult {
                    Replay.playRecording(index().value())
                }
            }
        }

        required(literal("load")) {
            required(greedyString("replay filepath")) { replayName ->
                suggests { _, builder ->
                    val dir = FolderRegistry.replay.toFile()
                    val paths = dir
                        .listRecursive { it.isFile }
                        .map { it.relativeTo(dir).path }
                        .toList()
                    suggestMatching(paths, builder)
                }

                executeWithResult {
                    val replayFile = FolderRegistry.replay.resolve(replayName().value())

                    if (!replayFile.exists()) {
                        return@executeWithResult CommandResult.failure("Replay file does not exist")
                    }

                    try {
                        Replay.loadRecording(replayFile.toFile())
                    } catch (e: JsonSyntaxException) {
                        return@executeWithResult CommandResult.failure("Failed to load replay file: ${e.message}")
                    }

                    CommandResult.success()
                }
            }
        }
        required(literal("save")) {
            required(integer("id")) { id ->
                required(greedyString("replay name")) { replayName ->
                    executeWithResult {
                        Replay.saveRecording(id().value(), replayName().value())
                    }
                }
            }
        }
        required(literal("prune")) {
            required(integer("id")) { id ->
                executeWithResult {
                    Replay.pruneRecording(id().value())
                }
            }
        }
    }
}
