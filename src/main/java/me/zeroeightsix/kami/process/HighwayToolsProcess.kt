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
        val ht = HighwayTools
        val processName = if (ht.pendingTasks.size > 0 && !ht.pathing) {
            ht.pendingTasks.peek().toString()
        } else if (ht.pathing) {
            "Moving to Position: (${ht.getNextWalkableBlock().asString()})"
        } else {
            "Manual mode"
        }
        return "HighwayTools: $processName"
    }

    override fun isActive(): Boolean {
        return HighwayTools.isEnabled
    }

    override fun onTick(p0: Boolean, p1: Boolean): PathingCommand? {
        val ht = HighwayTools
        return if (ht.baritoneMode.value) {
            PathingCommand(GoalNear(ht.getNextWalkableBlock(), 0), PathingCommandType.SET_GOAL_AND_PATH)
        } else PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
    }
}