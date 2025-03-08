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

package com.lambda.util.extension

import com.lambda.Lambda.mc
import com.lambda.util.VarIntIterator
import com.lambda.util.math.MathUtils.logCap
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
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

fun StructureTemplate.readNbtOrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable? = runCatching { readNbt(lookup, nbt) }.exceptionOrNull()

fun StructureTemplate.readSpongeOrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable? = when (nbt.getInt("Version") +
        nbt.getCompound("Schematic").getInt("Version"))
{
    1, 2 -> readSpongeV1OrException(lookup, nbt)
    3 -> readSpongeV3OrException(lookup, nbt)
    else -> IllegalStateException("Invalid sponge schematic version")
}

fun StructureTemplate.readSchematicOrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable = when (nbt.getString("Materials")) {
    "Alpha" -> IllegalStateException("Not implemented, you can help us by contributing to the project")
    "Classic" -> IllegalStateException("Method not implemented, you can help us by contributing to the Minecraft Wiki (https://minecraft.wiki/w/Data_values_(Classic))")
    "Pocket" -> IllegalStateException("Pocket Edition schematics are not supported")
    else -> IllegalStateException("Invalid MCEdit schematic version")
}

private fun StructureTemplate.readSpongeV1OrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable? {
    val version = nbt.getInt("DataVersion")
    val width = (nbt.getShort("Width") and 0xFFFF.toShort()).toInt()
    val height = (nbt.getShort("Height") and 0xFFFF.toShort()).toInt()
    val length = (nbt.getShort("Length") and 0xFFFF.toShort()).toInt()

    val metadata = nbt.getCompound("Metadata")

    // If the offset is too far, we simply ignore it
    // I think at some point schematica calculated
    // the offset based on the distance between the player position
    // and the schematic lower corner, so it would fuck up everything
    // when you tried to import and build it using Baritone
    // val minimumPosition = nbt.getIntArray("Offset")
    //     .takeIf { it.isNotEmpty() }
    //     ?.let { fastVectorOf(it[0], it[1], it[2]) }
    //     ?.takeIf { 274945015809L times 16 < it } ?: 0L

    val palette = nbt.getCompound("Palette")
    val newPalette = NbtList()

    palette.keys
        .sortedBy { palette.getInt(it) }
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
    VarIntIterator(nbt.getByteArray("BlockData"))
        .forEach { blockId ->
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
    nbt.putString("author", metadata.getString("Author"))

    // Fix the data for future versions
    DataFixTypes.STRUCTURE.update(mc.dataFixer, nbt, version)

    // Use the StructureTemplate NBT read utils in order to construct the template
    return readNbtOrException(lookup, nbt)
}

private fun StructureTemplate.readSpongeV3OrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable? {
    val schematic = nbt.getCompound("Schematic")
    val blocks = schematic.getCompound("Blocks")

    schematic.put("Palette", blocks.getCompound("Palette"))
    schematic.putByteArray("BlockData", blocks.getByteArray("Data"))

    nbt.clear()
    nbt.copyFrom(schematic)

    return readSpongeV1OrException(lookup, nbt)
}

fun StructureTemplate.readLitematicaOrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable? {
    val version = nbt.getInt("MinecraftDataVersion")

    val metadata = nbt.getCompound("Metadata")
    val author = metadata.getString("Author")

    val dimension = metadata.getVector("EnclosingSize")

    val newPalette = NbtList()
    val newBlocks = NbtList()

    val regions = nbt.getCompound("Regions")
    regions.keys.map { regions.getCompound(it) }
        .forEach {
            val position = it.getVector("Position")
            val size = it.getVector("Size")

            val xSizeAbs = abs(size.x)
            val ySizeAbs = abs(size.y)
            val zSizeAbs = abs(size.z)

            if (size.x < 0) position.x %= size.x + 1
            if (size.y < 0) position.y %= size.y + 1
            if (size.z < 0) position.z %= size.z + 1

            // The litematic's block state palette is the same as nbt
            newPalette.addAll(it.getList("BlockStatePalette", 10))

            val palette = it.getLongArray("BlockStates")
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
    return readNbtOrException(lookup, nbt)
}
