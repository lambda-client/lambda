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

package com.lambda.command.commands

import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.boolean
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.command.CommandRegistry.prefix
import com.lambda.command.LambdaCommand
import com.lambda.module.ModuleRegistry
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.Communication.joinToText
import com.lambda.util.Communication.warn
import com.lambda.util.StringUtils
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.ClickEvents.suggestCommand
import com.lambda.util.text.buildText
import com.lambda.util.text.clickEvent
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import java.awt.Color

object ModuleCommand : LambdaCommand(
    name = "module",
    aliases = setOf("mod"),
    usage = "module <module name> [enable]",
    description = "Enable or disable a module"
) {
    override fun CommandBuilder.create() {
        executeWithResult {
            val enabled = ModuleRegistry.modules.filter {
                it.isEnabled
            }

            if (enabled.isEmpty()) {
                info("No modules are enabled")
                return@executeWithResult success()
            }

            this@ModuleCommand.info(buildText {
                styled(Color.GRAY) {
                    literal("Enabled Modules: ")
                }
                joinToText(enabled) {
                    clickEvent(suggestCommand("$prefix${input} ${it.commandName}")) {
                        styled(if (it.isEnabled) Color.GREEN else Color.RED) {
                            literal(it.commandName)
                        }
                    }
                }
            })
            return@executeWithResult success()
        }

        required(string("module name")) { moduleName ->
            suggests { _, builder ->
                ModuleRegistry.modules.map {
                    it.commandName
                }.forEach {
                    builder.suggest(it)
                }
                builder.buildFuture()
            }
            optional(boolean("enable")) { enable ->
                executeWithResult {
                    val name = moduleName().value()
                    val module = ModuleRegistry.modules.find {
                        it.commandName.equals(name, true)
                    } ?: return@executeWithResult failure(buildText {
                        styled(Color.RED) {
                            literal("Module ")
                            styled(Color.GRAY) {
                                literal("$name ")
                            }
                            literal("not found!")
                        }
                        val similarModules = StringUtils.findSimilarStrings(
                            name,
                            ModuleRegistry.moduleCommandNames,
                            3
                        )
                        if (similarModules.isEmpty()) return@buildText

                        literal(" Did you mean ")
                        similarModules.forEachIndexed { index, s ->
                            if (index != 0) {
                                literal(", ")
                            }
                            clickEvent(suggestCommand("$prefix${input.replace(name, s)}")) {
                                styled(Color.GRAY) {
                                    literal(s)
                                }
                            }
                        }
                        literal("?")
                    })

                    runSafe {
                        if (enable == null) {
                            module.toggle()
                        } else {
                            if (enable().value() == module.isEnabled) {
                                this@ModuleCommand.warn(buildText {
                                    styled(Color.GRAY) {
                                        literal("$name already ")
                                        literal(if (module.isEnabled) "enabled" else "disabled")
                                    }
                                })
                                return@runSafe success()
                            }

                            if (enable().value()) {
                                module.enable()
                            } else {
                                module.disable()
                            }
                        }
                        this@ModuleCommand.info(buildText {
                            styled(Color.GRAY) {
                                literal("$name ")
                            }
                            styled(if (module.isEnabled) Color.GREEN else Color.RED) {
                                literal(if (module.isEnabled) "enabled" else "disabled")
                            }
                        })
                        success()
                    } ?: failure("Failed to ${if (module.isEnabled) "enable" else "disable"} module $name")
                }
            }
        }
    }
}
