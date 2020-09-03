package me.zeroeightsix.kami.process

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.pathing.goals.GoalNear
import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import me.zeroeightsix.kami.module.ModuleManager
import me.zeroeightsix.kami.module.modules.misc.HighwayTools

/**
 * @author Avanatiker
 * @since 26/08/20
 */
class HighwayToolsProcess : IBaritoneProcess {

    private lateinit var baritone: IBaritone

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
             highwayTools.blockQueue.peek().getBlock().toString() + " @ " + highwayTools.blockQueue.peek().getBlockPos().toString() + " State: " + highwayTools.blockQueue.peek().getTaskState().toString()
        } else {
            "Moving to ${highwayTools.getNextBlock()}"
        }
        return "HighwayTools: $processName"
    }

    override fun isActive(): Boolean {
        return (ModuleManager.isModuleEnabled(HighwayTools::class.java))
    }

    override fun onTick(p0: Boolean, p1: Boolean): PathingCommand? {
        baritone = BaritoneAPI.getProvider().primaryBaritone
        val highwayTools = ModuleManager.getModuleT(HighwayTools::class.java)!!
        return if (highwayTools.pathing && highwayTools.baritoneMode.value) {
            PathingCommand(GoalNear(highwayTools.getNextBlock(), 0), PathingCommandType.SET_GOAL_AND_PATH)
        } else PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
    }
}