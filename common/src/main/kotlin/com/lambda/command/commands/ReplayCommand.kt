package com.lambda.command.commands

import com.lambda.command.CommandManager.register
import com.lambda.command.LambdaCommand

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
        }
    }
}