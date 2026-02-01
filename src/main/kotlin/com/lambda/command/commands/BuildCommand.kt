/*
 * Copyright 2025 Lambda
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
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.AutomationConfig
import com.lambda.interaction.construction.StructureRegistry
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.task.RootTask.run
import com.lambda.task.tasks.BuildTask
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
	private var lastBuildTask: BuildTask? = null

	override fun CommandBuilder.create() {
		required(literal("place")) {
			required(greedyString("structure")) { structure ->
				suggests { _, builder ->
					StructureRegistry.forEach { key, _ -> builder.suggest(key) }
					builder.buildFuture()
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
						} catch (e: InvalidPathException) {
							return@executeWithResult failure("Invalid path $pathString")
						} catch (e: NoSuchFileException) {
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
