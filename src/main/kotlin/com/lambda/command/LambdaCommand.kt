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

package com.lambda.command

import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.CommandHandler.dispatcher
import com.lambda.core.Loadable
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.Nameable
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.HoverEvents
import com.lambda.util.text.buildText
import com.lambda.util.text.clickEvent
import com.lambda.util.text.highlighted
import com.lambda.util.text.hoverEvent
import com.lambda.util.text.literal
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.server.command.CommandManager

abstract class LambdaCommand(
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
                this@LambdaCommand.info(buildText {
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
