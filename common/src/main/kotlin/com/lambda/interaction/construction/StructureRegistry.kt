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
import java.nio.file.*
import kotlin.io.path.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asSequence

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object StructureRegistry : ConcurrentHashMap<Identifier, StructureTemplate?>() {
    private val levelSession = mc.levelStorage.createSession(FolderRegister.structure.path)
    private val structurePath = levelSession.getDirectory(WorldSavePath.ROOT).normalize()

    /**
     * Loads a structure from disk based on the provided [id].
     *
     * @param id The identifier of the structure to load.
     * @return The loaded [StructureTemplate], or null if the structure is not found.
     */
    fun loadStructure(id: Identifier): StructureTemplate? {
        val path = PathUtil.getResourcePath(structurePath, id.path, ".nbt")

        return if (!structurePath.isDirectory() || path.notExists()) null
        else computeIfAbsent(id) {
            path.inputStream().use { templateStream ->
                val compound = NbtIo.readCompressed(templateStream, NbtSizeTracker.ofUnlimitedBytes())
                if (compound.isValidStructureTemplate()) {
                    createStructure(compound)
                } else {
                    logError("Invalid structure template: ${path.fileName}", "File does not match template format")
                    null
                }
            }
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
            DataFixTypes.STRUCTURE.update(mc.dataFixer, nbt, version)
        )?.let { error ->
            logError("Could not create structure from file", error.message ?: "")
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
        val path = PathUtil.getResourcePath(structurePath, name, ".nbt")
        val compound = structure.writeNbt(NbtCompound())

        Files.createDirectories(path.parent) // Ensure parent directories exist
        path.outputStream().use { output ->
            NbtIo.writeCompressed(compound, output)
        }
    }

    /**
     * Streams all available structure templates from the directory.
     *
     * @return A [Sequence] of [Identifier]s of the available templates.
     */
    fun streamTemplates(): Sequence<Identifier> {
        return if (!structurePath.isDirectory()) {
            emptySequence()
        } else {
            try {
                structurePath.walk().filter { it.isRegularFile() && it.extension == "nbt" }
                    .filter { it.isValidNbtStructure() }
                    .mapNotNull { it.toIdentifier() }
            } catch (e: Exception) {
                logError("Error streaming structure templates", e)
                emptySequence()
            }
        }
    }

    /**
     * Converts a file [Path] to an [Identifier].
     *
     * @param this@pathToIdentifier The file path to convert.
     * @return The resulting [Identifier], or null if the path is invalid.
     */
    private fun Path.toIdentifier(): Identifier? {
        return try {
            val relativePath = structurePath.relativize(this).invariantSeparatorsPathString
            val namespace = "minecraft"
            val pathWithoutExtension = relativePath.removeSuffix(".nbt")
            Identifier(namespace, pathWithoutExtension)
        } catch (e: Exception) {
            this@StructureRegistry.logError("Invalid path for structure template", e.message ?: "")
            null
        }
    }

    /**
     * Walks through the [Path] hierarchy recursively and returns a [Sequence] of paths.
     */
    private fun Path.walk(): Sequence<Path> = Files.walk(this).asSequence()

    /**
     * Checks whether the NBT file at the given [this@isValidNbtStructure] is a valid Minecraft structure template.
     *
     * @param this@isValidNbtStructure The path to the NBT file.
     * @return True if the NBT file is a valid structure template, false otherwise.
     */
    private fun Path.isValidNbtStructure() =
        runCatching {
            inputStream().use { input ->
                NbtIo.readCompressed(input, NbtSizeTracker.ofUnlimitedBytes())
                    .isValidStructureTemplate()
            }
        }.getOrDefault(false)

    /**
     * Verifies that the provided NBT data represents a valid Minecraft structure template.
     *
     * @param this@isValidStructureTemplate The [NbtCompound] to validate.
     * @return True if the NBT contains valid structure template data, false otherwise.
     */
    private fun NbtCompound.isValidStructureTemplate() =
        contains("DataVersion")
                && contains("blocks")
                && contains("entities")
                && contains("palette")
                && contains("size")
}
