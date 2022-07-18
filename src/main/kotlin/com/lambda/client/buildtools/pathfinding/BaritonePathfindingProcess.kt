package com.lambda.client.buildtools.pathfinding

import baritone.api.process.IBaritoneProcess
import com.lambda.client.module.modules.client.BuildTools

object BaritonePathfindingProcess : IBaritoneProcess {
    override fun isTemporary() = true

    override fun priority() = 2.0

    override fun onLostControl() {
        Navigator.movementStrategy.onLostControl()
    }

    override fun displayName0() = Navigator.processInfo()

    override fun isActive() = BuildTools.isActive()

    override fun onTick(p0: Boolean, p1: Boolean) = Navigator.currentPathingCommand
}