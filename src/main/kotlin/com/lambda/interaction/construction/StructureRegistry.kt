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

package com.lambda.interaction.construction

import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.util.FolderRegister
import com.lambda.util.FolderRegister.structure
import com.lambda.util.extension.readLitematica
import com.lambda.util.extension.readSchematic
import com.lambda.util.extension.readSponge
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtSizeTracker
import net.minecraft.registry.Registries
import net.minecraft.structure.StructureTemplate
import java.io.FileNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.walk

/**
 * The `StructureRegistry` object is responsible for managing the loading, saving, and validating of Minecraft structure templates.
 * It extends [ConcurrentHashMap] to allow concurrent access to structure templates by their names.
 * This registry supports multiple structure formats and automatically monitors changes in the structure directory.
 */
@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object StructureRegistry : ConcurrentHashMap<String, StructureTemplate>(), Loadable {
	private val pathWatcher by lazy {
		FileSystems.getDefault().newWatchService()
			.apply { structure.register(this, ENTRY_CREATE, ENTRY_DELETE) }
	}

	/**
	 * Map of file suffix to their respective read function
	 */
	private val serializers = mapOf(
		"nbt" to StructureTemplate::readNbt,
		"schem" to StructureTemplate::readSponge,
		"litematic" to StructureTemplate::readLitematica,

		// Not supported, who could've guess that converting a format from 15 years ago would be hard? :clueless:
		"schematic" to StructureTemplate::readSchematic,
	)

	/**
	 * Loads a structure by relative path, will attempt a discovery sequence if the structure could not be found
	 * and performs format conversions if necessary
	 *
	 * @param relativePath The name of the structure to load (without extension).
	 * @param convert Whether to replace the file after converting it.
	 *
	 * @throws IllegalStateException if there was an error while parsing the data
	 * @throws FileNotFoundException when the given path doesn't exist
	 */
	fun loadStructureByRelativePath(
		relativePath: Path,
		convert: Boolean = true,
	): StructureTemplate {
		updateFileWatcher(convert)

		val structure = loadFileAndCreate(relativePath, convert)
		putIfAbsent(relativePath.pathString, structure)

		return structure
	}

	/**
	 * Poll directory file events to load many structures at once.
	 * They might not show up in the command suggestion, but they are
	 * present in the map.
	 */
	private fun updateFileWatcher(convert: Boolean) {
		pathWatcher.poll()?.let { key ->
			key.pollEvents()
				?.filterIsInstance<WatchEvent<Path>>()
				?.forEach { event ->
					val newPath = event.context()

					when (event.kind()) {
						ENTRY_DELETE -> remove(newPath.pathString)
						ENTRY_CREATE -> {
							put(
								newPath.pathString,
								loadFileAndCreate(newPath, convert)
							)
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
	 * @throws IllegalStateException if the parsed data is corrupted
	 *
	 * @return The created [StructureTemplate], or null if the structure is not found or invalid.
	 */
	private fun loadFileAndCreate(path: Path, convert: Boolean) =
		structure.resolve(path).inputStream().use { templateStream ->
			val compound = NbtIo.readCompressed(templateStream, NbtSizeTracker.ofUnlimitedBytes())
			val extension = path.extension
			val template = createStructure(compound, extension)

			if (convert && extension != "nbt") {
				saveStructure(path.nameWithoutExtension, template)
			}

			// Verify the structure integrity after it had been
			// converted to a regular structure template
			if (compound.isValidStructureTemplate()) template
			else throw IllegalStateException("Corrupted structure file ${path.pathString}")
		}

	/**
	 * Creates a [StructureTemplate] from the provided NBT data.
	 *
	 * @param nbt The [NbtCompound] containing the structure's data.
	 * @throws IllegalStateException if there was an error while parsing the data
	 */
	private fun createStructure(nbt: NbtCompound, suffix: String): StructureTemplate =
		StructureTemplate().apply {
			serializers[suffix]
				?.invoke(this, Registries.BLOCK, nbt)
		}

	/**
	 * Saves the provided [structure] to disk under the specified [name].
	 *
	 * @param relativePath The relative path of the structure to save without the extension.
	 * @param structure The [StructureTemplate] to save.
	 */
	private fun saveStructure(relativePath: String, structure: StructureTemplate) {
		val path = FolderRegister.structure.resolve("$relativePath.nbt")
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
		structure.walk()
			.filter { it.extension in serializers.keys }
			.sortedBy { it.extension.length } // Don’t walk lexicographically -Constructor
			.distinctBy { it.nameWithoutExtension } // Pick the first structure in the priority list nbt > litematica > schematica
			.forEach { struct ->
				runCatching { loadStructureByRelativePath(structure.relativize(struct)) }
					.onFailure { LOG.warn("Unable to load the structure $struct: ${it.message}") }
			}

		return "Loaded $size structure templates"
	}
}
