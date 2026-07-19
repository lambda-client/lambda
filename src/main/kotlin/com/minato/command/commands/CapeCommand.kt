
package com.minato.command.commands

import com.minato.brigadier.argument.literal
import com.minato.brigadier.argument.string
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.network.CapeHandler.availableCapes
import com.minato.network.CapeHandler.updateCape
import com.minato.util.CommunicationUtils.info
import com.minato.util.CommunicationUtils.logError
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching

@Suppress("unused")
object CapeCommand : MinatoCommand(
    name = "cape",
    usage = "set <id>",
    description = "Sets your cape",
) {
    override fun CommandBuilder.create() {
        required(literal("set")) {
            required(string("id")) { id ->
                suggests { _, builder ->
                    suggestMatching(availableCapes, builder)
                }

                execute {
                    val cape = id().value()
                    updateCape(cape) { error ->
                        if (error != null) logError("Could not update your cape", error)
                        else info("Updated your cape to $cape")
                    }
                }
            }
        }
    }
}
