package com.lambda.command

import com.lambda.config.Configurable
import com.lambda.config.configurations.LambdaConfig
import com.lambda.core.Loadable
import com.lambda.util.reflections.getInstances
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper.forPackage
import org.reflections.util.ConfigurationBuilder

/**
 * The [CommandRegistry] object is responsible for managing all [LambdaCommand] instances in the system.
 */
object CommandRegistry : Configurable(LambdaConfig), Loadable {
    override val name = "command"

    val prefix by setting("prefix", ';')
    val commands = getInstances<LambdaCommand> { forPackages("com.lambda.command.commands") }

    override fun load(): String {
        return "Registered ${commands.size} commands"
    }
}
