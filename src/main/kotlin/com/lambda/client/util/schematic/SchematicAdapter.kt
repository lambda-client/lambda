package com.lambda.client.util.schematic

import com.github.lunatrius.schematica.client.world.SchematicWorld
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class SchematicAdapter(private val schematic: SchematicWorld) : Schematic {
    override fun desiredState(pos: BlockPos): IBlockState {
        return schematic.schematic.getBlockState(BlockPos(pos.x - getOrigin().x, pos.y - getOrigin().y, pos.z - getOrigin().z))
    }

    override fun widthX(): Int {
        return schematic.schematic.width
    }

    override fun heightY(): Int {
        return schematic.schematic.height
    }

    override fun lengthZ(): Int {
        return schematic.schematic.length
    }

    override fun getOrigin(): BlockPos {
        return schematic.position
    }

    override fun inSchematic(pos: BlockPos): Boolean {
        return inSchematic(pos.x, pos.y, pos.z)
    }

    override fun inSchematic(x: Int, y: Int, z: Int): Boolean {
        return x >= schematic.position.x && x < schematic.position.x + widthX() && y >= schematic.position.y && y < schematic.position.y + heightY() && z >= schematic.position.z && z < schematic.position.z + lengthZ()
    }
}