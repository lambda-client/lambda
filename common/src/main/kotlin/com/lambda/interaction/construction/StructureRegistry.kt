/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.construction

import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.util.Communication.logError
import com.lambda.util.FolderRegister
import com.lambda.util.extension.readLitematicaOrException
import com.lambda.util.extension.readNbtOrException
import com.lambda.util.extension.readSchematicOrException
import com.lambda.util.extension.readSpongeOrException
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtSizeTracker
import net.minecraft.registry.Registries
import net.minecraft.structure.StructureTemplate
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/**
 * The `StructureRegistry` object is responsible for managing the loading, saving, and validating of Minecraft structure templates.
 * It extends [ConcurrentHashMap] to allow concurrent access to structure templates by their names.
 * This registry supports multiple structure formats and automatically monitors changes in the structure directory.
 */
@OptIn(ExperimentalPathApi::class)
@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object StructureRegistry : ConcurrentHashMap<String, StructureTemplate?>(), Loadable {
    private val structurePath = FolderRegister.structure
    private val pathWatcher = FileSystems.getDefault().newWatchService()
        .apply { structurePath.register(this, ENTRY_CREATE, ENTRY_DELETE) }

    /**
     * Map of file suffix to their respective read function
     */
    private val serializers = mapOf(
        "nbt" to StructureTemplate::readNbtOrException,
        "schem" to StructureTemplate::readSpongeOrException,
        "litematica" to StructureTemplate::readLitematicaOrException,

        // Not supported, who could guess that converting a format from 14 years ago would be hard? :clueless:
        "schematic" to StructureTemplate::readSchematicOrException,
    )

    /**
     * Loads a structure by relative path, will attempt a discovery sequence if the structure could not be found
     * and performs format conversions if necessary
     *
     * @param relativePath The name of the structure to load (without extension).
     * @param convert Whether to replace the file after converting it.
     * @return The loaded [StructureTemplate], or null if the structure is not found.
     */
    fun loadStructureByRelativePath(
        relativePath: Path,
        convert: Boolean = true,
    ): StructureTemplate? {
        updateFileWatcher()

        return computeIfAbsent(relativePath.pathString.lowercase()) {
            loadFileAndCreate(relativePath, convert)
        }
    }

    /**
     * Poll directory file events to load many structures at once.
     * They might not show up in the command suggestion, but they are
     * present in the map.
     */
    private fun updateFileWatcher() {
        pathWatcher.poll()?.let { key ->
            key.pollEvents()
                ?.filterIsInstance<WatchEvent<Path>>()
                ?.forEach { event ->
                    val newPath = event.context()

                    when (event.kind()) {
                        ENTRY_DELETE -> remove(newPath.pathString)
                        ENTRY_CREATE -> {
                            computeIfAbsent(newPath.pathString) {
                                loadStructureByRelativePath(newPath, convert = true)
                            }
                        }
                    }

                    // Reset the key -- this step is critical if you want to
                    // receive further watch events.  If the key is no longer valid,
                    // the directory is inaccessible so exit the loop.
                    if (!key.reset()) return@forEach
                }
        }
    }

    /**
     * Loads the structure file and creates a [StructureTemplate].
     *
     * @param convert Whether to replace the file after converting it.
     * @return The created [StructureTemplate], or null if the structure is not found or invalid.
     */
    private fun loadFileAndCreate(path: Path, convert: Boolean) =
        structurePath.resolve(path).inputStream().use { templateStream ->
            val compound = NbtIo.readCompressed(templateStream, NbtSizeTracker.ofUnlimitedBytes())
            val extension = path.extension
            val template = createStructure(compound, extension)

            if (convert && extension != "nbt") {
                template?.let { saveStructure(path.pathString, it) }
            }

            // Verify the structure integrity after it had been
            // converted to a regular structure template
            if (compound.isValidStructureTemplate()) {
                template
            } else {
                logError("Corrupted structure file: ${path.pathString}")
                null
            }
        }

    /**
     * Creates a [StructureTemplate] from the provided NBT data.
     *
     * @param nbt The [NbtCompound] containing the structure's data.
     * @return The created [StructureTemplate], or null if there was an error.
     */
    private fun createStructure(nbt: NbtCompound, suffix: String): StructureTemplate? =
        StructureTemplate().apply {
            serializers[suffix]
                ?.invoke(this, Registries.BLOCK.readOnlyWrapper, nbt)
                ?.let { error ->
                    logError("Could not create structure from file: ${error.message}")
                    return null
                }
        }

    /**
     * Saves the provided [structure] to disk under the specified [name].
     *
     * @param relativePath The relative path of the structure to save.
     * @param structure The [StructureTemplate] to save.
     */
    private fun saveStructure(relativePath: String, structure: StructureTemplate) {
        val path = structurePath.resolve(relativePath)
        val compound = structure.writeNbt(NbtCompound())

        Files.createDirectories(path.parent)
        path.outputStream().use { output ->
            NbtIo.writeCompressed(compound, output)
        }
    }

    /**
     * Verifies that the provided NBT data represents a valid Minecraft structure template.
     *
     * @receiver The [NbtCompound] to validate.
     * @return True if the NBT contains valid structure template data, false otherwise.
     */
    private fun NbtCompound.isValidStructureTemplate() =
        contains("DataVersion") && contains("blocks") && contains("palette") && contains("size")

    override fun load(): String {
        structurePath.walk()
            .filter { it.extension in serializers.keys }
            .forEach { loadStructureByRelativePath(structurePath.relativize(it)) }

        return "Loaded $size structure templates"
    }
}
