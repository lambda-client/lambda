package me.zeroeightsix.kami.process

import baritone.api.pathing.goals.GoalNear
import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import me.zeroeightsix.kami.module.modules.misc.HighwayTools
import me.zeroeightsix.kami.util.math.CoordinateConverter.asString

/**
 * @author Avanatiker
 * @since 26/08/20
 */
object HighwayToolsProcess : IBaritoneProcess {

    override fun isTemporary(): Boolean {
        return true
    }

    override fun priority(): Double {
        return 2.0
    }

    override fun onLostControl() {}

    override fun displayName0(): String {
        val processName = if (HighwayTools.pendingTasks.size > 0 && !HighwayTools.pathing) {
            HighwayTools.pendingTasks.peek().toString()
        } else if (HighwayTools.pathing) {
            "Moving to Position: (${HighwayTools.getNextWalkableBlock().asString()})"
        } else {
            "Manual mode"
        }
        return "HighwayTools: $processName"
    }

    override fun isActive(): Boolean {
        return HighwayTools.isEnabled
    }

    override fun onTick(p0: Boolean, p1: Boolean): PathingCommand? {
        return if (HighwayTools.baritoneMode.value) {
            PathingCommand(GoalNear(HighwayTools.getNextWalkableBlock(), 0), PathingCommandType.SET_GOAL_AND_PATH)
        } else PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
    }
}