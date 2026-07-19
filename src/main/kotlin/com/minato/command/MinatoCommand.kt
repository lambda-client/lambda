
package com.minato.command

import com.minato.brigadier.argument.literal
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.command.CommandHandler.dispatcher
import com.minato.core.Loadable
import com.minato.util.CommunicationUtils.info
import com.minato.util.Nameable
import com.minato.util.extension.CommandBuilder
import com.minato.util.text.ClickEvents
import com.minato.util.text.HoverEvents
import com.minato.util.text.buildText
import com.minato.util.text.clickEvent
import com.minato.util.text.highlighted
import com.minato.util.text.hoverEvent
import com.minato.util.text.literal
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.server.command.CommandManager

abstract class MinatoCommand(
    final override val name: String,
    val aliases: Set<String> = emptySet(),
    val usage: String = "",
    val description: String = "",
    val examples: List<String> = listOf()
) : Nameable, Loadable {
    override val priority get() = -1

    val registry: CommandRegistryAccess by lazy {
        CommandManager.createRegistryAccess(BuiltinRegistries.createWrapperLookup())
    }

    override fun load(): String {
        (aliases + name).forEach { alias ->
            LiteralArgumentBuilder.literal<CommandSource>(alias.lowercase()).apply {
                create()
                help()
                dispatcher.register(this)
            }
        }
        return ""
    }

    private fun CommandBuilder.help() {
        required(literal("help")) {
            execute {
                this@MinatoCommand.info(buildText {
                    literal("Help\n")
                    highlighted("Usage:\n")
                    literal("${CommandRegistry.prefix}$usage\n")
                    highlighted("Description:\n")
                    literal(description)
                    if (examples.isNotEmpty()) {
                        literal("\n")
                        highlighted("Examples:\n")
                        examples.forEachIndexed { i, example ->
                            val full = "${CommandRegistry.prefix}$example"
                            hoverEvent(HoverEvents.showText(buildText { literal("Click to try this example!") })) {
                                clickEvent(ClickEvents.suggestCommand(full)) {
                                    literal(full)
                                    if (i != examples.lastIndex) { literal("\n") }
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    abstract fun CommandBuilder.create()
}
