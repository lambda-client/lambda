
package com.minato.util.extension

import com.minato.Minato.mc
import com.minato.util.math.MathUtils.logCap
import com.minato.util.varIterator
import com.minato.util.world.FastVector
import com.minato.util.world.fastVectorOf
import com.minato.util.world.x
import com.minato.util.world.y
import com.minato.util.world.z
import net.minecraft.block.Block
import net.minecraft.datafixer.DataFixTypes
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryEntryLookup
import net.minecraft.structure.StructureTemplate
import kotlin.experimental.and
import kotlin.math.abs

private fun positionFromIndex(width: Int, length: Int, index: Int): FastVector {
    val y = index / (width * length)
    val remainder = index - (y * width * length)
    val z = remainder / width
    val x = remainder - z * width

    return fastVectorOf(x, y, z)
}

fun StructureTemplate.readSponge(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
) {
    when (nbt.getInt("Version", 0) +
            nbt.getCompoundOrEmpty("Schematic").getInt("Version", 0)) {
        1, 2 -> readSpongeV1(lookup, nbt)
        3 -> readSpongeV3(lookup, nbt)
        else -> throw IllegalStateException("Invalid sponge schematic version")
    }
}

fun StructureTemplate.readSchematic(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
) {
    throw when (nbt.getString("Materials", "")) {
        "Alpha" -> IllegalStateException("Not implemented, you can help us by contributing to the project")
        "Classic" -> IllegalStateException("Method not implemented, you can help us by contributing to the Minecraft Wiki (https://minecraft.wiki/w/Data_values_(Classic))")
        "Pocket" -> IllegalStateException("Pocket Edition schematics are not supported")
        else -> IllegalStateException("Invalid MCEdit schematic version")
    }
}

fun StructureTemplate.readSpongeV1(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
) {
    val version = nbt.getInt("DataVersion")
        .orElseThrow { IllegalStateException("Expected structure game data version but got nothing") }

    val width = nbt.getShort("Width")
        .map { (it and 0xFFFF.toShort()).toInt() }
        .orElseThrow { IllegalStateException("Expected structure width but got nothing") }

    val height = nbt.getShort("Height")
        .map { (it and 0xFFFF.toShort()).toInt() }
        .orElseThrow { IllegalStateException("Expected structure height but got nothing") }

    val length = nbt.getShort("Length")
        .map { (it and 0xFFFF.toShort()).toInt() }
        .orElseThrow { IllegalStateException("Expected structure length but got nothing") }

    val metadata = nbt.getCompoundOrEmpty("Metadata")
    val author = metadata.getString("Author", "unknown")

    // If the offset is too far, we simply ignore it
    // I think at some point schematica calculated
    // the offset based on the distance between the player position
    // and the schematic lower corner, so it would mess up everything
    // when you tried to import and build it
    // val minimumPosition = nbt.getIntArray("Offset")
    //     .takeIf { it.isNotEmpty() }
    //     ?.let { fastVectorOf(it[0], it[1], it[2]) }
    //     ?.takeIf { 274945015809L times 16 < it } ?: 0L

    val palette = nbt.getCompoundOrEmpty("Palette")
    val newPalette = NbtList()

    palette.keys
        .sortedBy { palette.getInt(it, 0) }
        .forEach { key ->
            val resource = key.substringBefore('[')
            val blockState = NbtCompound()

            // Why ?
            // I know it's supposed to be SNBT, but it cannot be parsed back
            key.substringAfter('[')
                .substringBefore(']')
                .takeIf { it != resource }
                ?.split(',')
                ?.associate { it.substringBefore('=') to it.substringAfter('=') }
                ?.forEach { (key, value) -> blockState.putString(key, value) }

            // Populate the list using the correct indices
            newPalette.add(NbtCompound().apply {
                putString("Name", resource)
                put("Properties", blockState)
            })
        }

    val newBlocks = NbtList()
    var blockIndex = 0
    nbt.getByteArray("BlockData")
        .orElseThrow { IllegalStateException("Expected block data but got nothing") }
        .varIterator { blockId ->
            val blockpos = positionFromIndex(width, length, blockIndex++)

            newBlocks.add(NbtCompound().apply {
                putIntList("pos", blockpos.x, blockpos.y, blockpos.z)
                putInt("state", blockId)
            })
        }

    // Construct a structure compatible nbt compound
    nbt.putIntList("size", width, height, length)
    nbt.put("palette", newPalette)
    nbt.put("blocks", newBlocks)
    nbt.putString("author", author)

    // Fix the data for future versions
    DataFixTypes.STRUCTURE.update(mc.dataFixer, nbt, version)

    // Use the StructureTemplate NBT read utils in order to construct the template
    return readNbt(lookup, nbt)
}

fun StructureTemplate.readSpongeV3(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
) {
    val schematic = nbt.getCompoundOrEmpty("Schematic")
    val blocks = schematic.getCompoundOrEmpty("Blocks")

    val palette = blocks.getCompound("Palette")
        .orElseThrow { IllegalStateException("Expected block palette but got nothing") }

    val blockData = blocks.getByteArray("Data")
        .orElseThrow { IllegalStateException("Expected block data but got nothing") }

    nbt.clear()

    nbt.put("Palette", palette)
    nbt.putByteArray("BlockData", blockData)
    nbt.copyFrom(schematic)

    return readSpongeV1(lookup, nbt)
}

fun StructureTemplate.readLitematica(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
) {
    when (val version = nbt.getInt("Version", 1)) {
        1, 2, 3, 4 -> readLitematicaV4(lookup, nbt)
        else -> throw IllegalStateException("Unsupported litematica version $version")
    }
}

private fun StructureTemplate.readLitematicaV4(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
) {
    val version = nbt.getInt("MinecraftDataVersion")
        .orElseThrow { IllegalStateException("Expected structure game data version but got nothing") }

    val metadata = nbt.getCompoundOrEmpty("Metadata")
    val author = metadata.getString("Author", "unknown")

    val dimension = metadata.getVector("EnclosingSize")
        .orElseThrow { IllegalStateException("Expected structure dimensions but got nothing") }

    val newPalette = NbtList()
    val newBlocks = NbtList()

    val regions = nbt.getCompoundOrEmpty("Regions")
    regions.keys.map { regions.getCompoundOrEmpty(it) }
        .forEach {
            val position = it.getVector("Position")
                .orElseThrow { IllegalStateException("Expected region position but got nothing") }

            val size = it.getVector("Size")
                .orElseThrow { IllegalStateException("Expected region size but got nothing") }

            val xSizeAbs = abs(size.x)
            val ySizeAbs = abs(size.y)
            val zSizeAbs = abs(size.z)

            if (size.x < 0) position.x %= size.x + 1
            if (size.y < 0) position.y %= size.y + 1
            if (size.z < 0) position.z %= size.z + 1

            // The litematic's block state palette is the same as nbt
            newPalette.addAll(it.getListOrEmpty("BlockStatePalette"))

            val palette = it.getLongArray("BlockStates")
                .orElseThrow { IllegalStateException("Expected block palette but got nothing") }

            val bits = palette.size.logCap(2)
            val maxEntryValue = (1 shl bits) - 1L

            for (x in 0 until xSizeAbs) {
                for (y in 0 until ySizeAbs) {
                    for (z in 0 until zSizeAbs) {
                        val index = (y * xSizeAbs * zSizeAbs) + z * xSizeAbs + x

                        val startOffset = index * bits
                        val startArrIndex = startOffset / 64
                        val endArrIndex = ((index + 1) * bits - 1) / 64
                        val startBitOffset = startOffset % 64


                        val stateId = if (startArrIndex == endArrIndex) {
                            palette[startArrIndex] ushr startBitOffset and maxEntryValue
                        } else {
                            (palette[startArrIndex] ushr startBitOffset or palette[endArrIndex] shl (64 - startBitOffset)) and maxEntryValue
                        }

                        newBlocks.add(NbtCompound().apply {
                            putIntList("pos", x, y, z)
                            putInt("state", stateId.toInt())
                        })
                    }
                }
            }
        }

    // Construct a structure compatible nbt compound
    nbt.putInt("DataVersion", version)
    nbt.putIntList("size", dimension.x, dimension.y, dimension.z)
    nbt.put("palette", newPalette)
    nbt.put("blocks", newBlocks)
    nbt.putString("author", author)

    // Fix the data for future versions
    DataFixTypes.STRUCTURE.update(mc.dataFixer, nbt, version)

    // Use the StructureTemplate NBT read utils in order to construct the template
    return readNbt(lookup, nbt)
}
