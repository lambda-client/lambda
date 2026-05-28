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
import com.lambda.util.FileUtils.listRecursive
import com.lambda.util.FolderRegistry
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching
import kotlin.io.path.exists

@Suppress("unused")
object ReplayCommand : LambdaCommand(
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
