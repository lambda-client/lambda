package me.zeroeightsix.kami.process

import baritone.api.pathing.goals.GoalNear
import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import me.zeroeightsix.kami.module.modules.misc.AutoObsidian

object AutoObsidianProcess : IBaritoneProcess {

    override fun isTemporary(): Boolean {
        return true
    }

    override fun priority(): Double {
        return 2.0
    }

    override fun onLostControl() {}

    override fun displayName0(): String {
        return "AutoObsidian: " + AutoObsidian.state.displayName
    }

    override fun isActive(): Boolean {
        return AutoObsidian.isActive()
    }

    override fun onTick(p0: Boolean, p1: Boolean): PathingCommand {
        return if (AutoObsidian.pathing && AutoObsidian.goal != null) {
            PathingCommand(GoalNear(AutoObsidian.goal, 0), PathingCommandType.SET_GOAL_AND_PATH)
        } else {
            PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }
    }
}