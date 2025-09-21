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

package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.network.LambdaAPI
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
import java.io.File
import java.lang.reflect.InaccessibleObjectException
import java.util.*
import kotlin.jvm.optionals.getOrDefault
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

object DynamicReflectionSerializer : Loadable {
    // Classes that should not be recursively serialized
    private val skipables = setOf(
        Codec::class,
        Logger::class,
        BlockPos::class,
        BlockState::class,
        ItemStack::class,
        Identifier::class,
        NbtCompound::class,
        Map::class,
        BitSet::class,
        Collection::class,
        RegistryEntry::class,
        RegistryKey::class,
        ScreenHandlerType::class,
        TranslationStorage::class,
        ChunkPos::class,
        Text::class,
        org.slf4j.Logger::class,
        String::class,
    )
    private val skipFields = setOf(
        Codec::class,
    )

    private const val INDENT = 2

    private val simpleMappings = runBlocking {
        "${LambdaAPI.mappings}/${LambdaAPI.GAME_VERSION}"
            .downloadIfNotPresent(cache.resolveFile("${LambdaAPI.GAME_VERSION}-simple"))
            .map(::buildMappingsMap)
            .getOrElse {
                LOG.error("Unable to download simplified deobfuscated qualifiers", it)
                emptyMap()
            }
    }

    private val qualifiedMappings = runBlocking {
        "${LambdaAPI.mappings}/${LambdaAPI.GAME_VERSION}-qualified"
            .downloadIfNotPresent(cache.resolveFile(LambdaAPI.GAME_VERSION))
            .map(::buildMappingsMap)
            .getOrElse {
                LOG.error("Unable to download deobfuscated qualifiers", it)
                emptyMap()
            }
    }

    val String.simpleRemappedName get() = simpleMappings.getOrDefault(this, this)
    val String.remappedName get() = qualifiedMappings.getOrDefault(this, this)

    private fun buildMappingsMap(file: File): Map<String, String> {
        val standardMappings = file.readLines()
            .map { it.split(' ') }
            .filter { it.size == 2 }
            .associate { (obf, deobf) -> obf to deobf }

        return buildMap {
            putAll(standardMappings)
            standardMappings.forEach { (obf, deobf) ->
                val parts = obf.split('$')
                put(parts.last(), deobf)
                if ('$' !in obf) return@forEach
                put(obf.replace('$', '.'), deobf)
                if (!parts.all { it.startsWith("class_") }) return@forEach
                (1 until parts.size).forEach { i ->
                    put("${parts.take(i).joinToString("$")}.${parts.drop(i).joinToString("$")}", deobf)
                }
            }
        }
    }

    fun <T : Any> KClass<T>.dynamicName(remap: Boolean, simple: Boolean = true) =
        if (remap)
            if (simple) qualifiedName?.simpleRemappedName else qualifiedName?.remappedName
        else if (simple) simpleName else qualifiedName

    fun <T : Any> KProperty1<T, *>.dynamicName(remap: Boolean, simple: Boolean = true) =
        if (remap)
            if (simple) name.simpleRemappedName else name.remappedName
        else name

    fun Any.dynamicString(
        maxRecursionDepth: Int = 6,
        currentDepth: Int = 0,
        indent: String = "",
        visitedObjects: MutableSet<Any> = HashSet(),
        builder: StringBuilder = StringBuilder(),
        remap: Boolean = !Lambda.isDebug,
        simple: Boolean = true
    ): String {
        if (visitedObjects.contains(this)) {
            builder.appendLine("$indent${this::class.dynamicName(remap, simple)} (Circular Reference)")
            return builder.toString()
        }

        visitedObjects.add(this)
        builder.appendLine("$indent${this::class.dynamicName(remap, simple)}")

        this::class.memberProperties
            .forEach { processField(it, indent, builder, currentDepth, maxRecursionDepth, visitedObjects, remap) }

        return builder.toString()
    }

    private fun <T : Any> T.processField(
        field: KProperty1<out T, *>,
        indent: String,
        builder: StringBuilder,
        currentDepth: Int,
        maxRecursionDepth: Int,
        visitedObjects: MutableSet<Any>,
        remap: Boolean,
    ) {
        if (skipFields.any { it.isInstance(field) }) return

        try {
            field.javaField?.isAccessible = true
        } catch (_: InaccessibleObjectException) {
            return
        }

        val fieldValue = field.javaField?.get(this)
        val fieldIndent = "$indent${" ".repeat(INDENT)}"
        builder.appendLine("$fieldIndent${field.dynamicName(remap)}: ${fieldValue.formatFieldValue(remap)}")

        if (currentDepth < maxRecursionDepth
            && fieldValue != null
            && !field.returnType.jvmErasure.java.isPrimitive
            && !field.returnType.jvmErasure.java.isArray
            && !field.returnType.jvmErasure.java.isEnum
            && skipables.none { it.isInstance(field.returnType.jvmErasure) }
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
            is NbtCompound -> asString().getOrDefault("")
            is RegistryEntry<*> -> "${value()}"
            null -> "null"
            else -> {
                if (this::class.qualifiedName?.contains("minecraft") == true) "${this::class.dynamicName(remap)}@${Integer.toHexString(hashCode())}"
                else this.toString()
            }
        }

    override fun load() = "Loaded ${simpleMappings.size} deobfuscated qualifier"
}
