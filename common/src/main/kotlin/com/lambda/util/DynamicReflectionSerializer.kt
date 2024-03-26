package com.lambda.util

import com.mojang.serialization.Codec
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

object DynamicReflectionSerializer {
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

    // ToDo: To make this work in production, every field could be remapped.
    fun Any.dynamicString(
        maxRecursionDepth: Int = 6,
        currentDepth: Int = 0,
        indent: String = "",
        visitedObjects: MutableSet<Any> = HashSet(),
        builder: StringBuilder = StringBuilder(),
    ): String {
        if (visitedObjects.contains(this)) {
            builder.appendLine("$indent${javaClass.simpleName} (Circular Reference)")
            return builder.toString()
        }

        visitedObjects.add(this)
        builder.appendLine("$indent${javaClass.simpleName}")

        val fields = javaClass.declaredFields + javaClass.superclass?.declaredFields.orEmpty()
        fields.forEach { field ->
            processField(field, indent, builder, currentDepth, maxRecursionDepth, visitedObjects)
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
    ) {
        if (skipFields.any { it.isAssignableFrom(field.type) }) return

        try {
            field.isAccessible = true
        } catch (e: InaccessibleObjectException) {
            return
        }
        val fieldValue = field.get(this)
        val fieldIndent = indent + " ".repeat(INDENT)
        builder.appendLine("$fieldIndent${field.name}: ${formatFieldValue(fieldValue)}")

        if (currentDepth < maxRecursionDepth
            && fieldValue != null
            && !field.type.isPrimitive
            && !field.type.isArray &&
            !field.type.isEnum &&
            skipables.none { it.isAssignableFrom(field.type) }
        ) {
            fieldValue.dynamicString(
                maxRecursionDepth,
                currentDepth + 1,
                fieldIndent + " ".repeat(INDENT),
                visitedObjects,
                builder,
            )
        }
    }

    private fun formatFieldValue(value: Any?): String {
        return when (value) {
            is String -> "\"$value\""
            is Collection<*> -> "[${value.joinToString(", ") { formatFieldValue(it) }}]"
            is Array<*> -> "[${value.joinToString(", ") { formatFieldValue(it) }}]"
            is Map<*, *> -> "{${
                value.entries.joinToString(", ") { (k, v) ->
                    "${formatFieldValue(k)}: ${formatFieldValue(v)}"
                }
            }}"
            is Text -> value.string
            is Identifier -> "${value.namespace}:${value.path}"
            is NbtCompound -> value.asString()
            is RegistryEntry<*> -> "${value.value()}"
            else -> value?.toString() ?: "null"
        }
    }
}