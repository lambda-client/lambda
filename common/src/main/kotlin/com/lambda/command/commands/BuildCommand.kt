package com.lambda.command.commands

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.argument.*
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.interaction.construction.StructureRegistry
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.extension.move
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

object BuildCommand : LambdaCommand(
    name = "Build",
    description = "Builds a structure",
    usage = "build <structure>"
) {
    override fun CommandBuilder.create() {
        required(literal("place")) {
            required(greedyString("structure")) { structure ->
                suggests { _, builder ->
                    StructureRegistry.forEach { key, _ -> builder.suggest(key) }

                    builder.buildFuture()
                }
                optional(boolean("pathing")) { pathing ->
                    executeWithResult {
                        val pathString = structure().value()
                        val doPathing = if (pathing != null) pathing().value() else false
                        runSafe<Unit> {
                            try {
                                StructureRegistry
                                    .loadStructureByRelativePath(Path.of(pathString))
                                    ?.let { template ->
                                        info("Building structure $pathString with dimensions ${template.size.toShortString()} created by ${template.author}")
                                        template.toStructure()
                                            .move(player.blockPos)
                                            .toBlueprint()
                                            .build(pathing = doPathing)
                                            .start(null)

                                        return@executeWithResult CommandResult.success()
                                    }
                            } catch (e: InvalidPathException) {
                                return@executeWithResult CommandResult.failure("Invalid path $pathString")
                            } catch (e: NoSuchFileException) {
                                return@executeWithResult CommandResult.failure("Structure $pathString not found")
                            } catch (e: Exception) {
                                return@executeWithResult CommandResult.failure(e.message ?: "Failed to load structure $pathString")
                            }
                        }

                        CommandResult.failure("Structure $pathString not found")
                    }
                }
            }
        }
    }
}
