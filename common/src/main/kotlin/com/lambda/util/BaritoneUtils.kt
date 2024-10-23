/*
 * Copyright 2024 Lambda
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

package com.lambda.util

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.Settings
import baritone.api.pathing.goals.Goal

object BaritoneUtils {
    private val baritone = BaritoneAPI.getProvider()
    val settings: Settings = BaritoneAPI.getSettings()

    @JvmStatic
    val primary: IBaritone = baritone.primaryBaritone

    /**
     * Whether Baritone is currently pathing
     */
    val isPathing: Boolean
        get() = primary.pathingBehavior.isPathing

    /**
     * Whether Baritone is active (pathing, calculating goal, etc.)
     */
    val isActive: Boolean
        get() = primary.customGoalProcess.isActive || primary.pathingBehavior.isPathing || primary.pathingControlManager.mostRecentInControl()
            .orElse(null)?.isActive == true

    /**
     * Sets the current Baritone goal and starts pathing
     */
    fun setGoalAndPath(goal: Goal) = primary.customGoalProcess.setGoalAndPath(goal)

    /**
     * Force cancel Baritone
     */
    fun cancel() = primary.pathingBehavior.cancelEverything()
}
