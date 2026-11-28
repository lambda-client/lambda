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

package com.lambda.module.modules.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.lambda.gui.core.observe
import com.lambda.gui.hud.HudText
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.module.HudModule
import com.lambda.module.ModuleTag

@Suppress("unused")
object Baritone : HudModule(
    name = "Baritone",
    description = "Look inside of Baritones head",
    tag = ModuleTag.HUD,
) {
	@Composable
	override fun Content() {
		val status by observe { status() }
		HudText(status)
	}

	private fun status(): String {
		if (!BaritoneHandler.baritoneAvailable) return "Baritone is not loaded"
		return when (val goal = BaritoneHandler.primary?.customGoalProcess?.goal) {
			null -> "No Baritone Process Running"
			is BuildGoal -> "Lambda Simulation: ${goal.sim}"
			else -> "Baritone: $goal"
		}
    }
}
