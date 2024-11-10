package com.lambda.util.extension

import com.lambda.Lambda.mc
import com.lambda.util.VarIntIterator
import com.lambda.util.world.*
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.datafixer.DataFixTypes
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryEntryLookup
import net.minecraft.structure.StructureTemplate
import net.minecraft.world.World
import kotlin.experimental.and

fun World.getBlockState(x: Int, y: Int, z: Int): BlockState {
    if (isOutOfHeightLimit(y)) return Blocks.VOID_AIR.defaultState

    val chunk = getChunk(x shr 4, z shr 4)
    val sectionIndex = getSectionIndex(y)
    if (sectionIndex < 0) return Blocks.VOID_AIR.defaultState

    val section = chunk.getSection(sectionIndex)
    return section.getBlockState(x and 0xF, y and 0xF, z and 0xF)
}

fun World.getFluidState(x: Int, y: Int, z: Int): FluidState {
    if (isOutOfHeightLimit(y)) return Fluids.EMPTY.defaultState

    val chunk = getChunk(x shr 4, z shr 4)
    val sectionIndex = getSectionIndex(y)
    if (sectionIndex < 0) return Fluids.EMPTY.defaultState

    val section = chunk.getSection(sectionIndex)
    return section.getFluidState(x and 0xF, y and 0xF, z and 0xF)
}

fun World.getBlockState(vec: FastVector): BlockState = getBlockState(vec.x, vec.y, vec.z)
fun World.getFluidState(vec: FastVector): FluidState = getFluidState(vec.x, vec.y, vec.z)

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
): Throwable? = when (nbt.getInt("Version")) {
    1, 2, 3 -> readSpongeV1OrException(lookup, nbt)
    else -> IllegalStateException("Invalid sponge schematic version")
}

fun StructureTemplate.readSchematicOrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable? = when (nbt.getString("Materials")) {
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
    // I think schematica at some point calculated
    // the offset based on the current player position from the
    // schematic, so it would fuck up everything when you tried
    // to import it back and build it using Baritone
    // val minimumPosition = nbt.getIntArray("Offset")
    //     .takeIf { it.isNotEmpty() }
    //     ?.let { fastVectorOf(it[0], it[1], it[2]) }
    //     ?.takeIf { 274945015809L times 16 < it } ?: 0L

    val paletteMax = nbt.getInt("PaletteMax")
    val palette = nbt.getCompound("Palette")

    if (palette.size != paletteMax)
        return IllegalStateException("Block palette size does not match the provided size (corrupted?)")

    val newPalette = NbtList()
    val newBlocks = NbtList()

    palette.keys.forEach { key ->
        val resource = key.substringBefore('[')
        val paletteEntry = NbtCompound()
        val blockState = NbtCompound()

        // Why ?
        // I know it's supposed to be SNBT, but it cannot be parsed back
        key
            .substringAfter('[')
            .substringBefore(']')
            .takeIf { it != resource }
            ?.split(',')
            ?.associate { it.substringBefore('=') to it.substringAfter('=') }
            ?.forEach { (key, value) -> blockState.putString(key, value) }

        paletteEntry.putString("Name", resource)
        paletteEntry.put("Properties", blockState)

        newPalette.add(paletteEntry)
    }

    var blockIndex = 0
    VarIntIterator(nbt.getByteArray("BlockData"))
        .forEach { blockId ->
            val compound = NbtCompound()
            val blockpos = positionFromIndex(width, length, blockIndex++)

            compound.putIntList("pos", blockpos.x, blockpos.y, blockpos.z)
            compound.putInt("state", blockId.toInt())

            newBlocks.add(compound)
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
    // Third revision
    // - 3D Biome support
    // - Rename Palette to BlockPalette
    // - Wordsmithing varint and palette usages
    nbt.put("Palette", nbt.getCompound("BlockPalette"))

    return readSpongeV1OrException(lookup, nbt)
}

fun StructureTemplate.readLitematicaOrException(
    lookup: RegistryEntryLookup<Block>,
    nbt: NbtCompound,
): Throwable? {
    return IllegalStateException("Litematica parsing is not implemented")
}
