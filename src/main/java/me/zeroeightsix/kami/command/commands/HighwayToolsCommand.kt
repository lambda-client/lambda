package me.zeroeightsix.kami.command.commands

import me.zeroeightsix.kami.command.ClientCommand
import me.zeroeightsix.kami.module.modules.misc.HighwayTools
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.block.Block

object HighwayToolsCommand : ClientCommand(
    name = "highwaytools",
    alias = arrayOf("ht"),
    description = "Customize settings of HighwayTools."
) {

    init {
        literal("add", "new", "+") {
            block("block") { blockArg ->
                execute("Add a block to ignore list") {
                    val added = HighwayTools.ignoreBlocks.add(blockArg.value)
                    if (added) {
                        HighwayTools.printSettings()
                        MessageSendHelper.sendChatMessage("Added &7${blockArg.value.localizedName}&r to ignore list.")
                    } else {
                        MessageSendHelper.sendChatMessage("&7${blockArg.value.localizedName}&r is already ignored.")
                    }
                }
            }
        }

        literal("del", "rem", "-") {
            block("block") { blockArg ->
                execute("Remove a block from ignore list") {
                    val removed = HighwayTools.ignoreBlocks.remove(blockArg.value)
                    if (removed) {
                        HighwayTools.printSettings()
                        MessageSendHelper.sendChatMessage("Removed &7${blockArg.value.localizedName}&r from ignore list.")
                    } else {
                        MessageSendHelper.sendChatMessage("&7${blockArg.value.localizedName}&r is not yet ignored.")
                    }
                }
            }
        }

        literal("material", "mat") {
            block("block") { blockArg ->
                execute("Set a block as main material") {
                    HighwayTools.material = blockArg.value
                    MessageSendHelper.sendChatMessage("Set your building material to &7${blockArg.value.localizedName}&r.")
                }
            }
        }

        literal("filler", "fil") {
            block("block") { blockArg ->
                execute("Set a block as filler material") {
                    HighwayTools.fillerMat = blockArg.value
                    MessageSendHelper.sendChatMessage("Set your filling material to &7${blockArg.value.localizedName}&r.")
                }
            }
        }

        execute("Print the settings") {
            HighwayTools.printSettings()
        }
    }
}