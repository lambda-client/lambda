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

package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.config.settings.CharSetting
import com.lambda.config.settings.FunctionSetting
import com.lambda.config.settings.StringSetting
import com.lambda.config.settings.collections.ListSetting
import com.lambda.config.settings.collections.MapSetting
import com.lambda.config.settings.collections.SetSetting
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.complex.BlockPosSetting
import com.lambda.config.settings.complex.BlockSetting
import com.lambda.config.settings.complex.ColorSetting
import com.lambda.config.settings.complex.KeyBindSetting
import com.lambda.config.settings.complex.Vec3dSetting
import com.lambda.config.settings.numeric.DoubleSetting
import com.lambda.config.settings.numeric.FloatSetting
import com.lambda.config.settings.numeric.IntegerSetting
import com.lambda.config.settings.numeric.LongSetting
import com.lambda.util.Communication.logError
import com.lambda.util.KeyCode
import com.lambda.util.Nameable
import imgui.flag.ImGuiInputTextFlags
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * Represents a set of [AbstractSetting]s that are associated with the [name] of the [Configurable].
 * The settings are managed by this [Configurable] and are saved and loaded as part of the [Configuration].
 *
 * This class also provides a series of helper methods ([setting]) for creating different types of settings.
 *
 * @property settings A set of [AbstractSetting]s that this configurable manages.
 */
abstract class Configurable(
    private val configuration: Configuration,
) : Jsonable, Nameable {
    val settings = mutableSetOf<AbstractSetting<*>>()

    init {
        registerConfigurable()
    }

    private fun registerConfigurable() = configuration.configurables.add(this)

    inline fun <reified T : AbstractSetting<*>> T.register(): T {
        check(settings.add(this)) { "Setting with name $name already exists for configurable: ${this@Configurable.name}" }
        return this
    }

    override fun toJson() =
        JsonObject().apply {
            settings.forEach { setting ->
                try {
                    add(setting.name, setting.toJson())
                } catch (e: Exception) {
                    logError("Failed to serialize $setting in ${this::class.simpleName}", e)
                }
            }
        }

    override fun loadFromJson(serialized: JsonElement) {
        serialized.asJsonObject.entrySet().forEach { (name, value) ->
            settings.find { it.name == name }?.loadFromJson(value)
                ?: LOG.warn("No saved setting found for $name with $value in ${this::class.simpleName}")
        }
    }

    /**
     * Creates a [BooleanSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Boolean] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * ```kotlin
     * private val foo by setting("Foo", true)
     * ```
     *
     * @return The created [BooleanSetting].
     */
    fun setting(
        name        : String,
        defaultValue: Boolean,
        description : String        = "",
        visibility  : () -> Boolean = { true },
    ) = BooleanSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates an [EnumSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Enum] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
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
        name        : String,
        defaultValue: T,
        description : String        = "",
        noinline
        visibility  : () -> Boolean = { true },
    ) = EnumSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates a [CharSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Char] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * @return The created [CharSetting].
     */
    fun setting(
        name        : String,
        defaultValue: Char,
        description : String        = "",
        visibility  : () -> Boolean = { true },
    ) = CharSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates a [StringSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [String] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * ```kotlin
     * private val foo by setting("Foo", "bar")
     * ```
     *
     * @return The created [StringSetting].
     */
    fun setting(
        name        : String,
        defaultValue: String,
        multiline   : Boolean       = false,
        flags       : Int           = ImGuiInputTextFlags.None,
        description : String        = "",
        visibility  : () -> Boolean = { true },
    ) = StringSetting(name, defaultValue, multiline, flags, description, visibility).register()

    /**
     * Constructs a [ListSetting] instance with the specified parameters and appends it to the [settings] collection.
     *
     * The type parameter [T] must either be a primitive type or a type with a registered type adapter in [Lambda.gson].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [List] value of type [T] for the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * ```kotlin
     * // the parameter type is inferred from the defaultValue
     * private val foo by setting("Foo", arrayListOf("bar", "baz"))
     * ```
     *
     * @return The created [ListSetting].
     */
    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: List<T>,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = ListSetting(
        name,
        defaultValue.toMutableList(),
        TypeToken.getParameterized(MutableList::class.java, T::class.java).type,
        description,
        visibility,
    ).register()

    /**
     * Constructs a [MapSetting] instance with the specified parameters and appends it to the [settings] collection.
     *
     * The type parameter [K] and [V] must either be a primitive type or a type with a registered type adapter in [Lambda.gson].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Map] value of type [K] and [V] for the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * ```kotlin
     * // the parameter types are inferred from the defaultValue
     * private val foo by setting("Foo", mapOf("bar" to 1, "baz" to 2))
     * ```
     *
     * @return The created [MapSetting].
     */
    inline fun <reified K : Any, reified V : Any> setting(
        name: String,
        defaultValue: Map<K, V>,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = MapSetting(
        name,
        defaultValue.toMutableMap(),
        TypeToken.getParameterized(MutableMap::class.java, K::class.java, V::class.java).type,
        description,
        visibility
    ).register()

    /**
     * Constructs a [SetSetting] instance with the specified parameters and appends it to the [settings] collection.
     *
     * The type parameter [T] must either be a primitive type or a type with a registered type adapter in [Lambda.gson].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Set] value of type [T] for the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
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
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = SetSetting(
        name,
        defaultValue.toMutableSet(),
        TypeToken.getParameterized(MutableSet::class.java, T::class.java).type,
        description,
        visibility,
    ).register()

    /**
     * Creates a [DoubleSetting] with the provided parameters and adds it to the [settings].
     *
     * The value of the setting is coerced into the specified [range] and rounded to the nearest [step].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Double] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param unit The unit of the setting. E.g. "°C", "m/s", "ms", "ticks", etc.
     *
     * @return The created [DoubleSetting].
     */
    fun setting(
        name: String,
        defaultValue: Double,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = DoubleSetting(name, defaultValue, range, step, description, visibility, unit).register()

    /**
     * Creates a [FloatSetting] with the provided parameters and adds it to the [settings].
     *
     * The value of the setting is coerced into the specified [range] and rounded to the nearest [step].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Float] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param unit The unit of the setting. E.g. "°C", "m/s", "ms", "ticks", etc.
     *
     * @return The created [FloatSetting].
     */
    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = FloatSetting(name, defaultValue, range, step, description, visibility, unit).register()

    /**
     * Creates an [IntegerSetting] with the provided parameters and adds it to the [settings].
     *
     * The value of the setting is coerced into the specified [range] and rounded to the nearest [step].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Int] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param unit The unit of the setting. E.g. "°C", "m/s", "ms", "ticks", etc.
     *
     * @return The created [IntegerSetting].
     */
    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = IntegerSetting(name, defaultValue, range, step, description, visibility, unit).register()

    /**
     * Creates a [LongSetting] with the provided parameters and adds it to the [settings].
     *
     * The value of the setting is coerced into the specified [range] and rounded to the nearest [step].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Long] value of the setting.
     * @param range The range within which the setting's value must fall.
     * @param step The step to which the setting's value is rounded.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     * @param unit The unit of the setting. E.g. "°C", "m/s", "ms", "ticks", etc.
     *
     * @return The created [LongSetting].
     */
    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = LongSetting(name, defaultValue, range, step, description, visibility, unit).register()

    /**
     * Creates a [KeyBindSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [KeyCode] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * @return The created [KeyBindSetting].
     */
    fun setting(
        name: String,
        defaultValue: KeyCode,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = KeyBindSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates a [ColorSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Color] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * @return The created [ColorSetting].
     */
    fun setting(
        name: String,
        defaultValue: Color,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = ColorSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates a [Vec3dSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Vec3d] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * @return The created [Vec3dSetting].
     */
    fun setting(
        name: String,
        defaultValue: Vec3d,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Vec3dSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates a [BlockPosSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [BlockPos.Mutable] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * @return The created [BlockPosSetting].
     */
    fun setting(
        name: String,
        defaultValue: BlockPos.Mutable,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = BlockPosSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates a [BlockPosSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [BlockPos] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * @return The created [BlockPosSetting].
     */
    fun setting(
        name: String,
        defaultValue: BlockPos,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = BlockPosSetting(name, defaultValue, description, visibility).register()

    /**
     * Creates a [BlockSetting] with the provided parameters and adds it to the [settings].
     *
     * @param name The unique identifier for the setting.
     * @param defaultValue The default [Block] value of the setting.
     * @param description A brief explanation of the setting's purpose and behavior.
     * @param visibility A lambda expression that determines the visibility status of the setting.
     *
     * @return The created [BlockSetting].
     */
    fun setting(
        name: String,
        defaultValue: Block,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = BlockSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: () -> Unit,
        description: String = "",
        visibility: () -> Boolean = { true }
    ) = FunctionSetting(name, defaultValue, description, visibility).register()
}
