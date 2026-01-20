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
import com.lambda.config.Configuration.Companion.configurables
import com.lambda.config.settings.CharSetting
import com.lambda.config.settings.FunctionSetting
import com.lambda.config.settings.StringSetting
import com.lambda.config.settings.collections.BlockCollectionSetting
import com.lambda.config.settings.collections.ClassCollectionSetting
import com.lambda.config.settings.collections.CollectionSetting
import com.lambda.config.settings.collections.ItemCollectionSetting
import com.lambda.config.settings.collections.MapSetting
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.BlockPosSetting
import com.lambda.config.settings.complex.BlockSetting
import com.lambda.config.settings.complex.ColorSetting
import com.lambda.config.settings.complex.KeybindSetting
import com.lambda.config.settings.complex.Vec3dSetting
import com.lambda.config.settings.numeric.DoubleSetting
import com.lambda.config.settings.numeric.FloatSetting
import com.lambda.config.settings.numeric.IntegerSetting
import com.lambda.config.settings.numeric.LongSetting
import com.lambda.event.Muteable
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
    val configuration: Configuration,
) : Jsonable, Nameable {
    val settings = mutableListOf<Setting<*, *>>()
	val settingGroups = mutableListOf<SettingGroup>()

    init {
        registerConfigurable()
    }

    private fun registerConfigurable() {
		if (configurables.any { it.name == name })
			throw IllegalStateException("Configurable with name $name already exists")
		configuration.configurables.add(this)
    }

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
    ) = Setting(name, description, BooleanSetting(defaultValue), this, visibility).register()

    inline fun <reified T : Enum<T>> setting(
        name: String,
        defaultValue: T,
        description: String = "",
        noinline
        visibility: () -> Boolean = { true },
    ) = Setting(name, description,EnumSetting(defaultValue), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Char,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, CharSetting(defaultValue), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: String,
        multiline: Boolean = false,
        flags: Int = ImGuiInputTextFlags.None,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, StringSetting(defaultValue, multiline, flags), this, visibility).register()

	@JvmName("collectionSetting1")
    fun setting(
        name: String,
        defaultValue: Collection<Block>,
        immutableCollection: Collection<Block> = Registries.BLOCK.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockCollectionSetting(immutableCollection, defaultValue.toMutableList()), this, visibility).register()

	@JvmName("collectionSetting2")
    fun setting(
        name: String,
        defaultValue: Collection<Item>,
        immutableCollection: Collection<Item> = Registries.ITEM.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, ItemCollectionSetting(immutableCollection, defaultValue.toMutableList()), this, visibility).register()

	@JvmName("collectionSetting3")
    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: Collection<T>,
        immutableList: Collection<T> = defaultValue,
        description: String = "",
        displayClassName: Boolean = false,
        serialize: Boolean = false,
        noinline visibility: () -> Boolean = { true },
    ) = Setting(
	    name,
	    description,
        if (displayClassName) ClassCollectionSetting(immutableList, defaultValue.toMutableList())
                else CollectionSetting(defaultValue.toMutableList(), immutableList, TypeToken.getParameterized(Collection::class.java, T::class.java).type, serialize),
		this,
	    visibility
	).register()

    // ToDo: Actually implement maps
    inline fun <reified K : Any, reified V : Any> setting(
        name: String,
        defaultValue: Map<K, V>,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = Setting(
	    name,
	    description,
	    MapSetting(
		    defaultValue.toMutableMap(),
		    TypeToken.getParameterized(MutableMap::class.java, K::class.java, V::class.java).type
		),
	    this,
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
    ) = Setting(name, description, DoubleSetting(defaultValue, range, step, unit), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, FloatSetting(defaultValue, range, step, unit), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, IntegerSetting(defaultValue, range, step, unit), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, LongSetting(defaultValue, range, step, unit), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Bind,
        description: String = "",
        alwaysListening: Boolean = false,
        screenCheck: Boolean = true,
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, KeybindSetting(defaultValue, this as? Muteable, alwaysListening, screenCheck), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: KeyCode,
        description: String = "",
        alwaysListening: Boolean = false,
        screenCheck: Boolean = true,
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, KeybindSetting(defaultValue, this as? Muteable, alwaysListening, screenCheck), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Color,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, ColorSetting(defaultValue), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Vec3d,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, Vec3dSetting(defaultValue), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: BlockPos.Mutable,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockPosSetting(defaultValue), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: BlockPos,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockPosSetting(defaultValue), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: Block,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockSetting(defaultValue), this, visibility).register()

    fun setting(
        name: String,
        defaultValue: () -> Unit,
        description: String = "",
        visibility: () -> Boolean = { true }
    ) = Setting(name, description, FunctionSetting(defaultValue), this, visibility).register()
}