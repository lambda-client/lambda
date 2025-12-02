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
import com.lambda.Lambda.LOG
import com.lambda.config.settings.CharSettingCore
import com.lambda.config.settings.FunctionSettingCore
import com.lambda.config.settings.StringSettingCore
import com.lambda.config.settings.collections.BlockCollectionSettingCore
import com.lambda.config.settings.collections.ClassCollectionSettingCore
import com.lambda.config.settings.collections.CollectionSettingCore
import com.lambda.config.settings.collections.ItemCollectionSettingCore
import com.lambda.config.settings.collections.MapSettingCore
import com.lambda.config.settings.comparable.BooleanSettingCore
import com.lambda.config.settings.comparable.EnumSettingCore
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.BlockPosSettingCore
import com.lambda.config.settings.complex.BlockSettingCore
import com.lambda.config.settings.complex.ColorSettingCore
import com.lambda.config.settings.complex.KeybindSettingCore
import com.lambda.config.settings.complex.Vec3DSettingCore
import com.lambda.config.settings.numeric.DoubleSettingCore
import com.lambda.config.settings.numeric.FloatSettingCore
import com.lambda.config.settings.numeric.IntegerSettingCore
import com.lambda.config.settings.numeric.LongSettingCore
import com.lambda.util.Communication.logError
import com.lambda.util.KeyCode
import com.lambda.util.Nameable
import imgui.flag.ImGuiInputTextFlags
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * Represents a set of [SettingCore]s that are associated with the [name] of the [Configurable].
 * The settings are managed by this [Configurable] and are saved and loaded as part of the [Configuration].
 *
 * This class also provides a series of helper methods ([setting]) for creating different types of settings.
 *
 * @property settings A set of [SettingCore]s that this configurable manages.
 */
abstract class Configurable(
    private val configuration: Configuration,
) : Jsonable, Nameable {
    val settings = mutableListOf<Setting<*, *>>()
	val settingGroups = mutableListOf<SettingGroup>()

    init {
        registerConfigurable()
    }

    private fun registerConfigurable() = configuration.configurables.add(this)

    fun <T : SettingCore<R>, R : Any> Setting<T, R>.register() = apply {
        if (settings.any { it.name == name })
            throw IllegalStateException("Setting with name $name already exists for configurable: ${this@Configurable.name}")
	    settings.add(this)
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

    fun setting(
        name: String,
        defaultValue: Boolean,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BooleanSettingCore(defaultValue), visibility).register()

    inline fun <reified T : Enum<T>> setting(
        name: String,
        defaultValue: T,
        description: String = "",
        noinline
        visibility: () -> Boolean = { true },
    ) = Setting(name, description,EnumSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: Char,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, CharSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: String,
        multiline: Boolean = false,
        flags: Int = ImGuiInputTextFlags.None,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, StringSettingCore(defaultValue, multiline, flags), visibility).register()

	@JvmName("collectionSetting1")
    fun setting(
        name: String,
        defaultValue: Collection<Block>,
        immutableCollection: Collection<Block> = Registries.BLOCK.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockCollectionSettingCore(immutableCollection, defaultValue.toMutableList()), visibility).register()

	@JvmName("collectionSetting2")
    fun setting(
        name: String,
        defaultValue: Collection<Item>,
        immutableCollection: Collection<Item> = Registries.ITEM.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, ItemCollectionSettingCore(immutableCollection, defaultValue.toMutableList()), visibility).register()

	@JvmName("collectionSetting3")
    inline fun <reified T : Comparable<T>> setting(
        name: String,
        defaultValue: Collection<T>,
        immutableList: Collection<T> = defaultValue,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = Setting(
	    name,
	    description,
	    CollectionSettingCore(
		    defaultValue.toMutableList(),
		    immutableList,
		    TypeToken.getParameterized(Collection::class.java, T::class.java).type
		),
	    visibility
	).register()

	@JvmName("collectionSetting4")
    inline fun <reified T : Any> setting(
	    name: String,
	    defaultValue: Collection<T>,
	    immutableList: Collection<T> = defaultValue,
	    description: String = "",
	    noinline visibility: () -> Boolean = { true },
    ) = Setting(name, description, ClassCollectionSettingCore(immutableList, defaultValue.toMutableList()), visibility).register()

    // ToDo: Actually implement maps
    inline fun <reified K : Any, reified V : Any> setting(
        name: String,
        defaultValue: Map<K, V>,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = Setting(
	    name,
	    description,
	    MapSettingCore(
		    defaultValue.toMutableMap(),
		    TypeToken.getParameterized(MutableMap::class.java, K::class.java, V::class.java).type
		),
		visibility
	).register()

    fun setting(
        name: String,
        defaultValue: Double,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, DoubleSettingCore(defaultValue, range, step, unit), visibility).register()

    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, FloatSettingCore(defaultValue, range, step, unit), visibility).register()

    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, IntegerSettingCore(defaultValue, range, step, unit), visibility).register()

    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, LongSettingCore(defaultValue, range, step, unit), visibility).register()

    fun setting(
        name: String,
        defaultValue: Bind,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, KeybindSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: KeyCode,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, KeybindSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: Color,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, ColorSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: Vec3d,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, Vec3DSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: BlockPos.Mutable,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockPosSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: BlockPos,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockPosSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: Block,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockSettingCore(defaultValue), visibility).register()

    fun setting(
        name: String,
        defaultValue: () -> Unit,
        description: String = "",
        visibility: () -> Boolean = { true }
    ) = Setting(name, description, FunctionSettingCore(defaultValue), visibility).register()
}