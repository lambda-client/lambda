package com.lambda.command.commands

import com.google.gson.JsonSyntaxException
import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.player.Replay
import com.lambda.util.FolderRegister
import com.lambda.util.FolderRegister.listRecursive
import com.lambda.util.primitives.extension.CommandBuilder

object ReplayCommand : LambdaCommand(
    name = "replay",
    usage = "replay <play|load|save|prune>",
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
                    val dir = FolderRegister.replay
                    dir.listRecursive { it.isFile }.forEach {
                        builder.suggest(it.relativeTo(dir).path)
                    }
                    builder.buildFuture()
                }

                executeWithResult {
                    val replayFile = FolderRegister.replay.resolve(replayName().value())

                    if (!replayFile.exists()) {
                        return@executeWithResult CommandResult.failure("Replay file does not exist")
                    }

                    try {
                        Replay.loadRecording(replayFile)
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
