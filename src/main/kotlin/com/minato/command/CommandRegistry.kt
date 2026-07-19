
package com.minato.command

import com.minato.command.CommandHandler.dispatcher
import com.minato.config.Config
import com.minato.config.categories.MinatoCategory
import com.minato.core.Loadable
import com.minato.util.ReflectionUtils.getInstances
import com.mojang.brigadier.tree.CommandNode

/**
 * The [CommandRegistry] object is responsible for managing all [MinatoCommand] instances in the system.
 */
object CommandRegistry : Config(
    "command",
    MinatoCategory
), Loadable {
    override val priority get() = -2
    val prefixSetting = setting("prefix", ';')
    val prefix by prefixSetting

    val commands = getInstances<MinatoCommand>().toMutableList()

    override fun load() = "Loaded ${commands.size} commands with ${dispatcher.root.children()} possible command paths."

    private fun CommandNode<*>.children(): Int = children.sumOf { it.children() } + 1
}
