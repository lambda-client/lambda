package me.zeroeightsix.kami.process

import baritone.api.pathing.goals.GoalNear
import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import me.zeroeightsix.kami.module.ModuleManager
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
        val highwayTools = ModuleManager.getModuleT(HighwayTools::class.java)!!
        val processName = if (highwayTools.blockQueue.size > 0 && !highwayTools.pathing) {
            "Block: " + highwayTools.blockQueue.peek().block.localizedName + " @ Position: (" + highwayTools.blockQueue.peek().blockPos.asString() + ") Priority: " + highwayTools.blockQueue.peek().priority + " State: " + highwayTools.blockQueue.peek().taskState.toString()
        } else if (highwayTools.pathing) {
            "Moving to Position: (${highwayTools.getNextBlock().asString()})"
        } else {
            "Manual mode"
        }
        return "HighwayTools: $processName"
    }

    override fun isActive(): Boolean {
        return (ModuleManager.isModuleEnabled(HighwayTools::class.java))
    }

    override fun onTick(p0: Boolean, p1: Boolean): PathingCommand? {
        val highwayTools = ModuleManager.getModuleT(HighwayTools::class.java)!!
        return if (highwayTools.baritoneMode.value) {
            PathingCommand(GoalNear(highwayTools.getNextBlock(), 0), PathingCommandType.SET_GOAL_AND_PATH)
        } else PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
    }
}