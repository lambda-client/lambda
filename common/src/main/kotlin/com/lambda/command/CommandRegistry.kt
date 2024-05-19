package com.lambda.command

import com.lambda.config.Configurable
import com.lambda.config.configurations.LambdaConfig
import com.lambda.core.Loadable
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

object CommandRegistry : Configurable(LambdaConfig), Loadable {
    override val name = "command"

    val prefix by setting("prefix", ';')
    val commands = mutableSetOf<LambdaCommand>()

    private val paths = mutableSetOf("com.lambda.command.commands")

    fun injectPath(path: String) = paths.add(path)

    override fun load(): String {
        Reflections(
            ConfigurationBuilder()
                .forPackages(*paths.toTypedArray())
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
