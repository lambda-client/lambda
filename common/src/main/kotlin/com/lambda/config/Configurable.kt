package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lambda.Lambda.LOG
import com.lambda.config.settings.StringSetting
import com.lambda.config.settings.collections.ListSetting
import com.lambda.config.settings.collections.MapSetting
import com.lambda.config.settings.collections.SetSetting
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.complex.BlockPosSetting
import com.lambda.config.settings.complex.BlockSetting
import com.lambda.config.settings.complex.KeyBindSetting
import com.lambda.config.settings.numeric.*
import com.lambda.util.KeyCode
import com.lambda.util.Nameable
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos
import com.lambda.Lambda

/**
 * Represents a set of [AbstractSetting]s that are associated with the [name] of the [Configurable].
 * The settings are managed by this [Configurable] and are saved and loaded as part of the [Configuration].
 *
 * This class also provides a series of helper methods ([setting]) for creating different types of settings.
 *
 * @property settings A set of [AbstractSetting]s that this configurable manages.
 */
abstract class Configurable(configuration: Configuration) : Jsonable, Nameable {
    val settings = mutableSetOf<AbstractSetting<*>>()

    init {
        configuration.configurables.add(this) // ToDo: Find non-leaking solution
    }

    override fun toJson() =
        JsonObject().apply {
            settings.forEach { setting ->
                add(setting.name, setting.toJson())
            }
        }

    override fun loadFromJson(serialized: JsonElement) {
        serialized.asJsonObject.entrySet().forEach { (name, value) ->
            settings.find {
                it.name == name
            }?.loadFromJson(value)
                ?: LOG.warn("No saved setting found for $name with $value in ${this::class.simpleName}")
        }
    }

    /**
     * Creates a [BooleanSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Boolean] value of the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * ```kotlin
     * private val foo by setting("Foo", true)
     * ```
     *
     * @return The created [BooleanSetting].
     */
    fun setting(
        name: String,
        defaultValue: Boolean,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = BooleanSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates an [EnumSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Enum] value of the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * ```kotlin
     * enum class Foo { A, B, C }
     * private val foo by setting("Foo", Foo.A)
     * ```
     *
     *
     * @return The created [EnumSetting].
     */
    inline fun <reified T : Enum<T>> setting(
        name: String,
        defaultValue: T,
        noinline visibility: () -> Boolean = { true },
        description: String = "",
    ) = EnumSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [StringSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [String] value of the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * ```kotlin
     * private val foo by setting("Foo", "bar")
     * ```
     *
     * @return The created [StringSetting].
     */
    fun setting(
        name: String,
        defaultValue: String,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = StringSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Constructs a [ListSetting] instance with the specified parameters and appends it to the [settings] collection.
     *
     * The type parameter [T] must either be a primitive type or a type with a registered type adapter in [Lambda.gson].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [List] value of type [T] for the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * ```kotlin
     * // the parameter type is inferred from the defaultValue
     * private val foo by setting("Foo", listOf("bar", "baz"))
     * ```
     *
     * @return The created [ListSetting].
     */
    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: List<T>,
        noinline visibility: () -> Boolean = { true },
        description: String = "",
    ) = ListSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Constructs a [MapSetting] instance with the specified parameters and appends it to the [settings] collection.
     *
     * The type parameter [K] and [V] must either be a primitive type or a type with a registered type adapter in [Lambda.gson].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Map] value of type [K] and [V] for the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * ```kotlin
     * // the parameter types are inferred from the defaultValue
     * private val foo by setting("Foo", mapOf("bar" to 1, "baz" to 2))
     * ```
     *
     * @return The created [MapSetting].
     */
    inline fun <reified K : Any, V : Any> setting(
        name: String,
        defaultValue: Map<K, V>,
        noinline visibility: () -> Boolean = { true },
        description: String = "",
    ) = MapSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Constructs a [SetSetting] instance with the specified parameters and appends it to the [settings] collection.
     *
     * The type parameter [T] must either be a primitive type or a type with a registered type adapter in [Lambda.gson].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Set] value of type [T] for the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * ```kotlin
     * // the parameter type is inferred from the defaultValue
     * private val foo by setting("Foo", setOf("bar", "baz"))
     * ```
     *
     * @return The created [SetSetting].
     */
    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: Set<T>,
        noinline visibility: () -> Boolean = { true },
        description: String = "",
    ) = SetSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [ByteSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Byte] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [ByteSetting].
     */
    fun setting(
        name: String,
        defaultValue: Byte,
        range: ClosedRange<Byte>,
        step: Byte = 1,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = ByteSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [DoubleSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Double] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [DoubleSetting].
     */
    fun setting(
        name: String,
        defaultValue: Double,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = DoubleSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [FloatSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Float] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [FloatSetting].
     */
    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = FloatSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates an [IntegerSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Int] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [IntegerSetting].
     */
    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = IntegerSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [LongSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Long] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [LongSetting].
     */
    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = LongSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [ShortSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Short] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [ShortSetting].
     */
    fun setting(
        name: String,
        defaultValue: Short,
        range: ClosedRange<Short>,
        step: Short = 1,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = ShortSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [KeyBindSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [KeyCode] value of the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [KeyBindSetting].
     */
    fun setting(
        name: String,
        defaultValue: KeyCode,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = KeyBindSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [BlockPosSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [BlockPos] value of the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [BlockPosSetting].
     */
    fun setting(
        name: String,
        defaultValue: BlockPos,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = BlockPosSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    /**
     * Creates a [BlockSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Block] value of the setting.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     *
     * @return The created [BlockSetting].
     */
    fun setting(
        name: String,
        defaultValue: Block,
        visibility: () -> Boolean = { true },
        description: String = "",
    ) = BlockSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }
}