package com.lambda.command.commands

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.argument.identifier
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.interaction.construction.StructureRegistry
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder

object BuildCommand : LambdaCommand(
    name = "Build",
    description = "Builds a structure",
    usage = "build <structure>"
) {
    override fun CommandBuilder.create() {
        required(literal("place")) {
            required(identifier("structure")) { structure ->
                suggests { _, builder ->
                    StructureRegistry.keys
                        .forEach { builder.suggest(it.path) }

                    builder.buildFuture()
                }

                executeWithResult {
                    StructureRegistry.loadStructure(structure().value())?.let { template ->
                        info("Building structure: ${template.size}")

                        return@executeWithResult CommandResult.success()
                    }

                    CommandResult.failure("Structure not found")
                }

//                execute {
//                    runSafe {
//
////                    val materials = setOf(
////                        TargetState.Block(Blocks.NETHERRACK),
////                        TargetState.Block(Blocks.AIR),
////                        TargetState.Block(Blocks.COBBLESTONE),
////                        TargetState.Block(Blocks.AIR),
////                    )
////                    val facing = player.horizontalFacing
////                    val pos = player.blockPos.add(facing.vector.multiply(2))
////
////                    BlockBox.create(pos, pos.add(facing.rotateYClockwise().vector.multiply(3)))
////                        .toStructure(TargetState.Block(Blocks.NETHERRACK))
////                        .toBlueprint {
////                            it.mapValues { (_, _) ->
////                                materials.elementAt((System.currentTimeMillis() / 5000).toInt() % materials.size)
////                            }
////                        }
////                        .build(finishOnDone = false)
////                        .start(null)
//                    }
//                }
            }
        }
    }
}
