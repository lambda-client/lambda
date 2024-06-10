package com.lambda.command

import com.lambda.config.Configurable
import com.lambda.config.configurations.LambdaConfig
import com.lambda.core.Loadable
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleRegistry.modules
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

/**
 * The [CommandRegistry] object is responsible for managing all [LambdaCommand] instances in the system.
 */
object CommandRegistry : Configurable(LambdaConfig), Loadable {
    override val name = "command"

    val prefix by setting("prefix", ';')
    val commands = mutableSetOf<LambdaCommand>()

    override fun load(): String {
        Reflections(
            ConfigurationBuilder()
                .forPackage("com.lambda.command.commands")
                .setScanners(Scanners.SubTypes)
        ).getSubTypesOf(LambdaCommand::class.java).forEach { commandClass ->
            commandClass.declaredFields.find {
                it.name == "INSTANCE"
            }?.apply {
                isAccessible = true
                (get(null) as? LambdaCommand)?.let { command ->
                    commands.add(command)
                }
            }
        }

        return "Registered ${commands.size} commands"
    }
}
