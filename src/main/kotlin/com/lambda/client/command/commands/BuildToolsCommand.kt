package com.lambda.client.command.commands

import com.lambda.client.buildtools.BuildToolsManager
import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage

object BuildToolsCommand : ClientCommand(
    name = "buildtools",
    alias = arrayOf("bt"),
    description = "Customize settings of BuildTools."
) {
    init {
        literal("filler", "fil") {
            block("block") { blockArg ->
                execute("Sets a block as default filler material") {
                    BuildTools.defaultFillerMat = blockArg.value
                    sendChatMessage("Set your default filling material to &7${blockArg.value.localizedName}&r.")
                }
            }
        }

        literal("add", "new", "+") {
            block("block") { blockArg ->
                execute("Adds a block to ignore list") {
                    val added = BuildTools.ignoreBlocks.add(blockArg.value.registryName.toString())
                    if (added) {
                        BuildToolsManager.printSettingsAndInfo()
                        sendChatMessage("Added &7${blockArg.value.localizedName}&r to ignore list.")
                    } else {
                        sendChatMessage("&7${blockArg.value.localizedName}&r is already ignored.")
                    }
                }
            }
        }

        literal("remove", "rem", "-", "del") {
            block("block") { blockArg ->
                execute("Removes a block from ignore list") {
                    val removed = BuildTools.ignoreBlocks.remove(blockArg.value.registryName.toString())
                    if (removed) {
                        BuildToolsManager.printSettingsAndInfo()
                        sendChatMessage("Removed &7${blockArg.value.localizedName}&r from ignore list.")
                    } else {
                        sendChatMessage("&7${blockArg.value.localizedName}&r is not yet ignored.")
                    }
                }
            }
        }

        execute("Dumps ignored blocks and info about the current structure ") {
            BuildToolsManager.printSettingsAndInfo()
        }
    }
}