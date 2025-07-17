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

package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.module.modules.client.Network
import com.lambda.util.FileUtils.downloadIfNotPresent
import com.lambda.util.FolderRegister.cache
import com.lambda.util.extension.resolveFile
import com.mojang.serialization.Codec
import kotlinx.coroutines.runBlocking
import net.minecraft.block.BlockState
import net.minecraft.client.resource.language.TranslationStorage
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import org.apache.logging.log4j.Logger
import java.lang.reflect.Field
import java.lang.reflect.InaccessibleObjectException
import java.util.*

object DynamicReflectionSerializer : Loadable {
    // Classes that should not be recursively serialized
    private val skipables = setOf(
        Codec::class.java,
        Logger::class.java,
        BlockPos::class.java,
        BlockState::class.java,
        ItemStack::class.java,
        Identifier::class.java,
        NbtCompound::class.java,
        Map::class.java,
        BitSet::class.java,
        Collection::class.java,
        RegistryEntry::class.java,
        RegistryKey::class.java,
        ScreenHandlerType::class.java,
        TranslationStorage::class.java,
        ChunkPos::class.java,
        Text::class.java,
        org.slf4j.Logger::class.java,
        String::class.java,
    )
    private val skipFields = setOf(
        Codec::class.java,
    )

    private const val INDENT = 2

    private val mappings = runBlocking {
        "${Network.mappings}/${Network.gameVersion}"
            .downloadIfNotPresent(cache.resolveFile(Network.gameVersion))
            .map { file ->
                val standardMappings = file.readLines()
                    .map { it.split(' ') }
                    .filter { it.size == 2 }
                    .associate { (obf, deobf) -> obf to deobf }

                buildMap {
                    putAll(standardMappings)

                    standardMappings.forEach { (obf, deobf) ->
                        put(obf.split('$').last(), deobf)
                        if ('$' !in obf) return@forEach
                        put(obf.replace('$', '.'), deobf)
                        val parts = obf.split('$')
                        if (!parts.all { it.startsWith("class_") }) return@forEach
                        (1 until parts.size).forEach { i ->
                            put("${parts.take(i).joinToString("$")}.${parts.drop(i).joinToString("$")}", deobf)
                        }
                    }
                }
            }
            .getOrElse {
                LOG.error("Unable to download deobfuscated qualifiers", it)
                emptyMap()
            }
    }


    val String.remappedName get() = mappings.getOrDefault(this, this)

    fun <T : Any> Class<T>.dynamicName(remap: Boolean) =
        if (remap) canonicalName.remappedName else simpleName
    fun Field.dynamicName(remap: Boolean) =
        if (remap) name.remappedName else name

    fun Any.dynamicString(
        maxRecursionDepth: Int = 6,
        currentDepth: Int = 0,
        indent: String = "",
        visitedObjects: MutableSet<Any> = HashSet(),
        builder: StringBuilder = StringBuilder(),
        remap: Boolean = !Lambda.isDebug,
    ): String {
        if (visitedObjects.contains(this)) {
            builder.appendLine("$indent${javaClass.dynamicName(remap)} (Circular Reference)")
            return builder.toString()
        }

        visitedObjects.add(this)
        builder.appendLine("$indent${javaClass.dynamicName(remap)}")

        val fields = javaClass.declaredFields + javaClass.superclass?.declaredFields.orEmpty()
        fields.forEach { field ->
            processField(field, indent, builder, currentDepth, maxRecursionDepth, visitedObjects, remap)
        }

        return builder.toString()
    }

    private fun Any.processField(
        field: Field,
        indent: String,
        builder: StringBuilder,
        currentDepth: Int,
        maxRecursionDepth: Int,
        visitedObjects: MutableSet<Any>,
        remap: Boolean,
    ) {
        if (skipFields.any { it.isAssignableFrom(field.type) }) return

        try {
            field.isAccessible = true
        } catch (e: InaccessibleObjectException) {
            return
        }
        val fieldValue = field.get(this)
        val fieldIndent = "$indent${" ".repeat(INDENT)}"
        builder.appendLine("$fieldIndent${field.dynamicName(remap)}: ${fieldValue.formatFieldValue(remap)}")

        if (currentDepth < maxRecursionDepth
            && fieldValue != null
            && !field.type.isPrimitive
            && !field.type.isArray
            && !field.type.isEnum
            && skipables.none { it.isAssignableFrom(field.type) }
        ) {
            fieldValue.dynamicString(
                maxRecursionDepth,
                currentDepth + 1,
                "$fieldIndent${" ".repeat(INDENT)}",
                visitedObjects,
                builder,
                remap
            )
        }
    }

    private fun Any?.formatFieldValue(remap: Boolean): String =
        when (this) {
            is String -> "\"${this}\""
            is Collection<*> -> "[${joinToString(", ") { it.formatFieldValue(remap) }}]"
            is Array<*> -> "[${joinToString(", ") { it.formatFieldValue(remap) }}]"
            is Map<*, *> -> "{${
                entries.joinToString(", ") { (k, v) ->
                    "${k.formatFieldValue(remap)}: ${v.formatFieldValue(remap)}"
                }
            }}"

            is Text -> string
            is Identifier -> "$namespace:$path"
            is NbtCompound -> asString()
            is RegistryEntry<*> -> "${value()}"
            else -> {
                if (this?.javaClass?.canonicalName?.contains("minecraft") == true)
                    "${this.javaClass.dynamicName(remap)}@${Integer.toHexString(hashCode())}"
                else this?.toString() ?: "null"
            }
        }

    override fun load() = "Loaded ${mappings.size} deobfuscated qualifier"
}
