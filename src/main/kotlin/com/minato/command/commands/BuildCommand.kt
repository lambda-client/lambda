
package com.minato.command.commands

import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.greedyString
import com.minato.brigadier.argument.literal
import com.minato.brigadier.argument.value
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.config.automation.AutomationConfig
import com.minato.interaction.construction.StructureRegistry
import com.minato.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.minato.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.minato.task.RootTask.run
import com.minato.task.tasks.BuildTask
import com.minato.task.tasks.BuildTask.Companion.build
import com.minato.threading.runSafe
import com.minato.util.CommunicationUtils.info
import com.minato.util.extension.CommandBuilder
import com.minato.util.extension.move
import net.minecraft.command.CommandSource.suggestMatching
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

object BuildCommand : MinatoCommand(
    name = "Build",
    description = "Builds a structure",
    usage = "build <structure>"
) {
    private var lastBuildTask: BuildTask? = null

    override fun CommandBuilder.create() {
        required(literal("place")) {
            required(greedyString("structure")) { structure ->
                suggests { _, builder ->
                    suggestMatching(StructureRegistry.keys, builder)
                }
                executeWithResult {
                    val pathString = structure().value()
                    runSafe<Unit> {
                        try {
                            StructureRegistry
                                .loadStructureByRelativePath(Path.of(pathString))
                                .let { template ->
                                    info("Building structure $pathString with dimensions ${template.size.toShortString()} created by ${template.author}")
                                    lastBuildTask = with(AutomationConfig.Companion.DEFAULT) {
                                        template.toStructure()
                                            .move(player.blockPos)
                                            .toBlueprint()
                                            .build()
                                            .run()
                                    }

                                    return@executeWithResult success()
                                }
                        } catch (_: InvalidPathException) {
                            return@executeWithResult failure("Invalid path $pathString")
                        } catch (_: NoSuchFileException) {
                            return@executeWithResult failure("Structure $pathString not found")
                        } catch (e: Exception) {
                            return@executeWithResult failure(
                                e.message ?: "Failed to load structure $pathString"
                            )
                        }
                    }

                    failure("Structure $pathString not found")
                }
            }
        }

        required(literal("cancel")) {
            executeWithResult {
                lastBuildTask?.cancel() ?: run {
                    return@executeWithResult failure("No build task to cancel")
                }
                this@BuildCommand.info("$lastBuildTask cancelled")
                lastBuildTask = null
                success()
            }
        }
    }
}
