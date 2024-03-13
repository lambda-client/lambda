package com.lambda.command.commands

import com.lambda.brigadier.*
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.boolean
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.command.CommandManager.prefix
import com.lambda.command.CommandManager.register
import com.lambda.command.LambdaCommand
import com.lambda.module.ModuleRegistry
import com.lambda.threading.runSafe
import com.lambda.util.StringUtils
import com.lambda.util.text.*
import com.lambda.util.text.ClickEvents.suggestCommand

object ModuleCommand : LambdaCommand() {
    override val name = "module"

    init {
        register(name, "mod") {
            required(string("module name")) { moduleName ->
                suggests { _, builder ->
                    ModuleRegistry.modules.map {
                        it.name
                    }.forEach {
                        builder.suggest(it)
                    }
                    builder.buildFuture()
                }
                optional(boolean("enable")) { enable ->
                    executeWithResult {
                        val name = this[moduleName].value()
                        val module = ModuleRegistry.modules.find {
                            it.name.equals(name, true)
                        } ?: return@executeWithResult failure(buildText {
                            styled(Color.RED) {
                                literal("Module ")
                                styled(Color.GREY) {
                                    literal("$name ")
                                }
                                literal("not found!")
                            }
                            val similarModules = StringUtils.findSimilarStrings(
                                name,
                                ModuleRegistry.moduleNames,
                                3
                            )
                            println(similarModules)
                            if (similarModules.isEmpty()) return@buildText

                            literal(" Did you mean ")
                            similarModules.forEachIndexed { index, s ->
                                if (index != 0) {
                                    literal(", ")
                                }
                                clickEvent(suggestCommand("$prefix${input.replace(name, s)}")) {
                                    styled(Color.GREY) {
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
                                if (enable().value()) {
                                    module.enable()
                                } else {
                                    module.disable()
                                }
                            }
                            sendSuccess(buildText {
                                literal("Module ")
                                styled(Color.GREY) {
                                    literal("$name ")
                                }
                                styled(if (module.isEnabled) Color.GREEN else Color.RED) {
                                    literal(if (module.isEnabled) "enabled" else "disabled")
                                }
                            })
                            success()
                        } ?: failure(buildText {
                            styled(Color.RED) {
                                literal("Failed to ${if (module.isEnabled) "enable" else "disable"} module $name")
                            }
                        })
                    }
                }
            }
        }
    }
}