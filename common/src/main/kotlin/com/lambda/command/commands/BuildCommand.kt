package com.lambda.command.commands

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.argument.identifier
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.StructureRegistry
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.extension.move

object BuildCommand : LambdaCommand(
    name = "Build",
    description = "Builds a structure",
    usage = "build <structure>"
) {
    override fun CommandBuilder.create() {
        required(literal("place")) {
            required(identifier("structure")) { structure ->
                suggests { _, builder ->
                    StructureRegistry.streamTemplates()
                        .forEach { builder.suggest(it.path) }

                    builder.buildFuture()
                }

                executeWithResult {
                    val id = structure().value()
                    runSafe<Unit> {
                        StructureRegistry.loadStructure(id)?.let { template ->
                            info("Building structure ${id.path} with size ${template.size.toShortString()} by ${template.author}")
                            template.toStructure()
                                .move(player.blockPos)
                                .toBlueprint()
                                .build()
                                .start(null)

                            return@executeWithResult CommandResult.success()
                        }
                    }

                    CommandResult.failure("Structure ${id.path} not found")
                }
            }
        }
    }
}
