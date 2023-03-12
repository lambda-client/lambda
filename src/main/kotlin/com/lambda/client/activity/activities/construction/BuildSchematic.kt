package com.lambda.client.activity.activities.construction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.Direction
import com.lambda.client.util.schematic.LambdaSchematicaHelper
import com.lambda.client.util.schematic.Schematic
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class BuildSchematic(
    private val schematic: Schematic,
    private val inLayers: Boolean = true,
    private val direction: Direction = Direction.NORTH,
    private val offsetMove: BlockPos = BlockPos.ORIGIN,
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (!LambdaSchematicaHelper.isSchematicaPresent) {
            failedWith(SchematicNotPresentException())
            return
        }

        val structure = mutableMapOf<BlockPos, IBlockState>()

        for (y in schematic.getOrigin().y..schematic.getOrigin().y + schematic.heightY()) {
            val layerStructure = mutableMapOf<BlockPos, IBlockState>()

            for (x in schematic.getOrigin().x..schematic.getOrigin().x + schematic.widthX()) {
                for (z in schematic.getOrigin().z..schematic.getOrigin().z + schematic.lengthZ()) {
                    val blockPos = BlockPos(x, y, z)
                    if (!schematic.inSchematic(blockPos)) continue
                    layerStructure[blockPos] = schematic.desiredState(blockPos)
                }
            }

            structure.putAll(layerStructure)

            if (!inLayers) continue

            addSubActivities(
                BuildStructure(
                    layerStructure,
                    direction,
                    offsetMove
                )
            )
        }

        MessageSendHelper.sendChatMessage("$activityName Building ${structure.size} blocks")

        if (inLayers) return

        addSubActivities(
            BuildStructure(
                structure,
                direction,
                offsetMove
            )
        )
    }

    private class SchematicNotPresentException : Exception("Schematica is not present")
}