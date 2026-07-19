
package com.minato.command.commands

import com.minato.brigadier.argument.literal
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.module.modules.world.StashMover
import com.minato.threading.runSafe
import com.minato.util.extension.CommandBuilder

object StashMoverCommand : MinatoCommand(
	name = "stashmover",
	usage = "stashmover <command>",
	description = "Set configurations for the StashMover module"
) {
	override fun CommandBuilder.create() {
		required(literal("index_selected_containers")) {
			execute { runSafe { StashMover.indexSelectedContainers() } }
		}
		required(literal("remove_selected_containers")) {
			execute { runSafe { StashMover.removeSelectedContainers() } }
		}
		required(literal("set_item_throw")) {
			execute { runSafe { StashMover.setItemThrow() } }
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
		required(literal("pause-unpause")) {
			execute { runSafe { StashMover.pauseUnpause() } }
		}
	}
}