package com.lambda.interaction.construction

import com.lambda.Lambda.mc
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
import net.minecraft.util.WorldSavePath
import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.inputStream
import kotlin.io.path.*

/**
 * The `StructureRegistry` object is responsible for managing the loading, saving, and validating of Minecraft structure templates.
 * It extends [ConcurrentHashMap] to allow concurrent access to structure templates by their names.
 * This registry supports multiple structure formats and automatically monitors changes in the structure directory.
 */
@OptIn(ExperimentalPathApi::class)
@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object StructureRegistry
    : ConcurrentHashMap<String, StructureTemplate?>(), Loadable {
    private val levelSession = mc.levelStorage.createSession(FolderRegister.structure.path)
    private val structurePath = levelSession.getDirectory(WorldSavePath.ROOT).normalize()
    private val pathWatcher = FileSystems.getDefault().newWatchService()
        .apply { structurePath.register(this, ENTRY_CREATE, ENTRY_DELETE) }

    /**
     * Map of file suffix to their respective read function
     */
    val serializers = mapOf(
        "nbt" to StructureTemplate::readNbtOrException,
        "schem" to StructureTemplate::readSpongeOrException,
        "litematica" to StructureTemplate::readLitematicaOrException,

        // Not supported, who could guess that converting a format from 14 years ago would be hard ? :clueless:
        "schematic" to StructureTemplate::readSchematicOrException,
    )

    /**
     * Searches for a structure file by name, trying all supported extensions.
     *
     * @param name The name of the structure file without the extension.
     * @return The corresponding [File] if found, or null otherwise.
     */
    fun findStructureByName(name: String): File? =
        serializers
            .keys
            .firstNotNullOfOrNull { extension ->
                structurePath
                    .resolve("$name.$extension")
                    .toFile()
                    .takeIf { it.isFile && it.exists() }
            }

    /**
     * Loads a structure by name, will attempt a discovery sequence if the structure could not be found
     * and performs format conversions if necessary
     *
     * @param name The name of the structure to load (without extension).
     * @param convert Whether to replace the file after converting it.
     * @return The loaded [StructureTemplate], or null if the structure is not found.
     */
    fun loadStructureByName(
        name: String,
        convert: Boolean = true,
    ): StructureTemplate? {
        if (!structurePath.isDirectory()) {
            logError(
                "Invalid structure template: $name",
                "The structure folder is not a folder"
            )
        }

        // Poll directory file events to load many structures at once
        // They might not show up in the command suggestion, but they are
        // present in the map
        pathWatcher.poll()
            ?.let { key ->
                key.pollEvents()?.forEach { event ->
                    @Suppress("UNCHECKED_CAST")
                    event as WatchEvent<Path>

                    val kind = event.kind()
                    val path = event.context()
                    val nameNoExt = path.nameWithoutExtension

                    when (kind) {
                        ENTRY_DELETE -> remove(nameNoExt)
                        ENTRY_CREATE -> if (!contains(nameNoExt) && nameNoExt != name) loadStructureByName(path.nameWithoutExtension, convert = true)
                    }

                    // Reset the key -- this step is critical if you want to
                    // receive further watch events.  If the key is no longer valid,
                    // the directory is inaccessible so exit the loop.
                    if (!key.reset()) return@forEach
                }
            }

        return computeIfAbsent(name.lowercase()) {
            findStructureByName(name)
                ?.let { it to it.extension }
                ?.let { (file, extension) ->
                    file.inputStream().use { templateStream ->
                        val compound = NbtIo.readCompressed(templateStream, NbtSizeTracker.ofUnlimitedBytes())
                        val template = createStructure(compound, extension)

                        // Only delete structure files that aren't NBT
                        if (convert && extension != "nbt") {
                            template
                                ?.let { saveStructure(name, it) }
                                ?.let { structurePath.resolve("$name.$extension").deleteIfExists() }
                        }

                        // Verify the structure integrity after it had been
                        // converted to a regular structure template
                        if (compound.isValidStructureTemplate()) template
                        else {
                            logError(
                                "Invalid structure template: $it",
                                "File does not match template format, it might have been corrupted",
                            )
                            null
                        }
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
    private fun createStructure(nbt: NbtCompound, suffix: String): StructureTemplate? {
        val template = StructureTemplate()

        serializers[suffix]
            ?.invoke(
                template,
                Registries.BLOCK.readOnlyWrapper,
                nbt,
            )
            ?.let { error ->
                logError("Could not create structure from file", error.message ?: "")
                return null
            }

        return template
    }

    /**
     * Saves the provided [structure] to disk under the specified [name].
     *
     * @param name The name of the structure file (without the extension).
     * @param structure The [StructureTemplate] to save.
     */
    fun saveStructure(name: String, structure: StructureTemplate) {
        val path = structurePath.resolve("$name.nbt")
        val compound = structure.writeNbt(NbtCompound())

        Files.createDirectories(path.parent) // Ensure parent directories exist
        path.outputStream().use { output ->
            NbtIo.writeCompressed(compound, output)
        }
    }

    /**
     * Verifies that the provided NBT data represents a valid Minecraft structure template.
     *
     * @param this@isValidStructureTemplate The [NbtCompound] to validate.
     * @return True if the NBT contains valid structure template data, false otherwise.
     */
    private fun NbtCompound.isValidStructureTemplate() =
        contains("DataVersion") && contains("blocks") && contains("palette") && contains("size")

    override fun load(): String {
        structurePath.walk().forEach { path ->
            if (path.extension in serializers.keys)
                loadStructureByName(path.nameWithoutExtension)
        }

        return "Loaded $size structure templates"
    }
}
