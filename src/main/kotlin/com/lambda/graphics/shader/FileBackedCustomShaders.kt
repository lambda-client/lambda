/*
 * Copyright 2026 Lambda
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

package com.lambda.graphics.shader

import com.lambda.Lambda
import com.lambda.graphics.mc.LambdaRenderPipelines
import net.minecraft.util.Identifier
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.HexFormat
import java.util.zip.CRC32

class FileBackedCustomShaders(
    private val folder: Path,
    private val exampleName: String,
    private val exampleShader: String,
    private val pathPrefix: String,
    private val templateMarker: String,
    private val templateShader: Identifier,
    private val prepareFolderError: String,
    private val listShadersError: String
) {
    companion object {
        const val NONE = "None"
    }

    private val states = mutableMapOf<String, FileState>()
    private val options = mutableSetOf<String>()
    private var cachedOptions = arrayOf(NONE)
    private var lastFolderMtime: Long = -1L

    @Synchronized
    fun ensureFolder() {
        try {
            Files.createDirectories(folder)

            val example = folder.resolve(exampleName)
            if (!Files.exists(example)) {
                Files.writeString(example, exampleShader, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
            }

            if (cachedOptions.size == 1) refreshOptions()
        } catch (e: IOException) {
            Lambda.LOG.error(prepareFolderError, e)
        }
    }

    @Synchronized
    fun getOptions(): Array<String> {
        ensureFolder()
        refreshOptions()
        return cachedOptions.copyOf()
    }

    @Synchronized
    fun isSelected(name: String?): Boolean {
        if (name.isNullOrBlank() || name == NONE) return false

        ensureFolder()
        if (name in options) return true
        refreshOptions()
        return name in options
    }

    @Synchronized
    fun clearState() {
        states.clear()
        options.clear()
        cachedOptions = arrayOf(NONE)
        lastFolderMtime = -1L
        ensureFolder()
    }

    fun getShaderIdentifier(name: String): Identifier =
        Identifier.of(Lambda.MOD_ID, pathPrefix + encode(name) + "/" + getVersionToken(name))

    fun handles(identifier: Identifier): Boolean =
        identifier.namespace == Lambda.MOD_ID && identifier.path.startsWith(pathPrefix)

    @Throws(IOException::class)
    fun loadSource(identifier: Identifier): String {
        val encodedName = identifier.path.removePrefix(pathPrefix).substringBefore('/')
        val shaderName = decode(encodedName)
        val source = Files.readString(resolveFile(shaderName), StandardCharsets.UTF_8)
        if (source.contains("#version")) return source

        val template = LambdaRenderPipelines.getShaderSource(templateShader)
        val result = template.replace(templateMarker, source)
        if (result.contains(templateMarker)) {
            Lambda.LOG.warn("Custom shader '{}' marker not found in template; shader will have no effect. Expected marker: {}", shaderName, templateMarker)
        }
        return result
    }

    fun getEncodedName(name: String): String = encode(name)

    @Synchronized
    fun getVersionToken(name: String): String =
        try {
            states.getOrPut(name) { FileState.read(resolveFile(name)) }.token()
        } catch (_: IOException) {
            "missing"
        }

    private fun refreshOptions() {
        if (Files.isDirectory(folder)) {
            val currentMtime = Files.getLastModifiedTime(folder).toMillis()
            if (lastFolderMtime == currentMtime && options.isNotEmpty()) {
                return
            }
            lastFolderMtime = currentMtime
        }

        val updatedOptions = mutableListOf(NONE)
        options.clear()

        if (!Files.isDirectory(folder)) {
            cachedOptions = updatedOptions.toTypedArray()
            return
        }

        try {
            Files.list(folder).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .map { it.fileName.toString() }
                    .filter { it.endsWith(".frag") }
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach {
                        updatedOptions.add(it)
                        options.add(it)
                    }
            }
        } catch (e: IOException) {
            Lambda.LOG.error(listShadersError, e)
        }

        cachedOptions = updatedOptions.toTypedArray()
    }

    @Throws(IOException::class)
    private fun resolveFile(fileName: String): Path {
        val normalizedFolder = folder.toAbsolutePath().normalize()
        val path = normalizedFolder.resolve(fileName).normalize()
        if (path.parent != normalizedFolder || !Files.isRegularFile(path) || !path.fileName.toString().endsWith(".frag")) {
            throw IOException("Invalid custom shader path: $fileName")
        }
        return path
    }

    private fun encode(value: String): String = HexFormat.of().formatHex(value.toByteArray(StandardCharsets.UTF_8))

    private data class FileState(val modifiedTime: Long, val size: Long, val checksum: Long) {
        fun token(): String = "$modifiedTime-$size-${checksum.toString(16)}"

        companion object {
            @Throws(IOException::class)
            fun read(path: Path): FileState {
                val modifiedTime = Files.getLastModifiedTime(path).toMillis()
                val size = Files.size(path)
                val bytes = Files.readAllBytes(path)
                val crc = CRC32().apply { update(bytes) }
                return FileState(modifiedTime, size, crc.value)
            }
        }
    }

    @Throws(IOException::class)
    private fun decode(value: String): String =
        try {
            String(HexFormat.of().parseHex(value), StandardCharsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid custom shader identifier: $value", e)
        }
}
