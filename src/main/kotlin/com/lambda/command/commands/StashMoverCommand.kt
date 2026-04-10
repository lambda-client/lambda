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

package com.lambda.command.commands

import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.world.StashMover
import com.lambda.threading.runSafe
import com.lambda.util.extension.CommandBuilder

object StashMoverCommand : LambdaCommand(
	name = "stashmover",
	description = "Set configurations for the StashMover module"
) {
	override fun CommandBuilder.create() {
		required(literal("index_selected_containers")) {
			execute { runSafe { StashMover.indexSelectedContainers() } }
		}
		required(literal("remove_selected_containers")) {
			execute { runSafe { StashMover.removeSelectedContainers() } }
		}
		required(literal("set_pearl_button_pos")) {
			execute { runSafe { StashMover.setPearlButtonPos() } }
		}
		required(literal("set_pearl_throw")) {
			execute { runSafe { StashMover.setPearlThrow() } }
		}
		required(literal("set_pearlbot_button")) {
			execute { runSafe { StashMover.setPearlBotButton() } }
		}
		required(literal("start-stop")) {
			execute { runSafe { StashMover.startStop() } }
		}
	}
}