package com.lambda.interaction.construction

import com.lambda.Lambda.mc
import com.lambda.util.Communication.logError
import com.lambda.util.FolderRegister
import com.lambda.util.extension.readNbtOrException
import net.minecraft.datafixer.DataFixTypes
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtSizeTracker
import net.minecraft.registry.Registries
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.Identifier
import net.minecraft.util.PathUtil
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object StructureRegistry : ConcurrentHashMap<Identifier, StructureTemplate?>() {
    private val levelSession = mc.levelStorage.createSession(FolderRegister.structure.path)
    private val generatedPath = levelSession.getDirectory(WorldSavePath.ROOT).normalize()

    /**
     * Loads a structure from disk based on the provided [id].
     *
     * @param id The identifier of the structure to load.
     * @return The loaded [StructureTemplate], or null if the structure is not found.
     */
    fun loadStructure(id: Identifier): StructureTemplate? {
        val path = PathUtil.getResourcePath(generatedPath, id.path, ".nbt")

        return if (!Files.isDirectory(generatedPath, LinkOption.NOFOLLOW_LINKS) || path.notExists()) null
        else computeIfAbsent(id) {
            val compound = path.inputStream(StandardOpenOption.READ)
                .use { template ->
                    NbtIo.readCompressed(
                        template,
                        NbtSizeTracker.ofUnlimitedBytes()
                    ).also { template.close() }
                }

            createStructure(nbt = compound)
        }
    }

    /**
     * Creates a [StructureTemplate] from the provided NBT data.
     *
     * @param nbt The [NbtCompound] containing the structure's data.
     * @return The created [StructureTemplate], or null if there was an error.
     */
    private fun createStructure(nbt: NbtCompound): StructureTemplate? {
        val template = StructureTemplate()
        val version = NbtHelper.getDataVersion(nbt, 500)

        template.readNbtOrException(
            Registries.BLOCK.readOnlyWrapper,
            DataFixTypes.STRUCTURE.update(mc.dataFixer, nbt, version),
        )?.let { err ->
            this@StructureRegistry.logError("Could not create structure from file", err.message ?: "")
            return null
        }

        return template
    }

    /**
     * Saves the provided [structure] to disk under the specified [name].
     *
     * @param name The name of the structure file (without the ".nbt" extension).
     * @param structure The [StructureTemplate] to save.
     */
    fun saveStructure(name: String, structure: StructureTemplate) {
        val path = PathUtil.getResourcePath(generatedPath, name, ".nbt")
        val compound = structure.writeNbt(NbtCompound())

        NbtIo.writeCompressed(compound, path)
    }
}
