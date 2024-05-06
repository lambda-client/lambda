package com.lambda.command.commands

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.get
import com.lambda.brigadier.required
import com.lambda.command.CommandManager.register
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.player.Replay
import com.lambda.util.FolderRegister
import com.lambda.util.FolderRegister.listRecursive

object ReplayCommand : LambdaCommand {
    override val name = "replay"

    init {
        register(name, "rep") {
            // 1. Save current recording / checkpoint to disc with name
            // 2. Load replay from disc with name
            // 3. Play replay
            // 4. Stop replay
            // 5. Pause replay
            // 6. Resume replay
            // 7. Set replay speed
            required(string("replay name")) { replayName ->
                suggests { _, builder ->
                    val dir = FolderRegister.replay
                    dir.listRecursive().forEach {
                        builder.suggest(it.relativeTo(dir).path)
                    }
                    builder.buildFuture()
                }

                executeWithResult {
                    val replayFile = FolderRegister.replay.resolve(this[replayName].value())

                    if (!replayFile.exists()) {
                        return@executeWithResult CommandResult.failure("Replay file does not exist")
                    }

                    Replay.loadRecording(replayFile)
                    CommandResult.success()
                }
            }
        }
    }
}