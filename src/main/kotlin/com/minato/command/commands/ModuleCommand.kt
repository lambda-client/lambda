
package com.minato.command.commands

import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.boolean
import com.minato.brigadier.argument.string
import com.minato.brigadier.argument.value
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.optional
import com.minato.brigadier.required
import com.minato.command.CommandRegistry.prefix
import com.minato.command.MinatoCommand
import com.minato.module.ModuleRegistry
import com.minato.threading.runSafe
import com.minato.util.CommunicationUtils.info
import com.minato.util.CommunicationUtils.joinToText
import com.minato.util.CommunicationUtils.warn
import com.minato.util.StringUtils.findSimilarStrings
import com.minato.util.extension.CommandBuilder
import com.minato.util.text.ClickEvents.suggestCommand
import com.minato.util.text.buildText
import com.minato.util.text.clickEvent
import com.minato.util.text.literal
import com.minato.util.text.styled
import net.minecraft.command.CommandSource.suggestMatching
import java.awt.Color

object ModuleCommand : MinatoCommand(
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
                    clickEvent(suggestCommand("$prefix${input} ${it.name}")) {
                        styled(if (it.isEnabled) Color.GREEN else Color.RED) {
                            literal(it.name)
                        }
                    }
                }
            })
            return@executeWithResult success()
        }

        required(string("module name")) { moduleName ->
            suggests { _, builder ->
                suggestMatching(ModuleRegistry.moduleNameMap.keys, builder)
            }
            optional(boolean("enable")) { enable ->
                executeWithResult {
                    val name = moduleName().value()
                    val module = ModuleRegistry.modules.find {
                        it.name.equals(name, true)
                    } ?: return@executeWithResult failure(buildText {
                        styled(Color.RED) {
                            literal("Module ")
                            styled(Color.GRAY) {
                                literal("$name ")
                            }
                            literal("not found!")
                        }
                        val similarModules = name.findSimilarStrings(
                            ModuleRegistry.moduleNameMap.keys,
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

                            if (enable().value()) module.enable()
                            else module.disable()
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
