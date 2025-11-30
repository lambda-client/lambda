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
import com.lambda.config.settings.CharSetting
import com.lambda.config.settings.FunctionSetting
import com.lambda.config.settings.StringSetting
import com.lambda.config.settings.collections.BlockCollectionSetting
import com.lambda.config.settings.collections.CollectionSettings
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
    val settings = mutableListOf<AbstractSetting<*>>()
	val settingGroups = mutableListOf<SettingGroup>()

    init {
        registerConfigurable()
    }

    private fun registerConfigurable() = configuration.configurables.add(this)

    inline fun <reified T : AbstractSetting<*>> T.register(): T {
        if (settings.any { it.name == name })
            throw IllegalStateException("Setting with name $name already exists for configurable: ${this@Configurable.name}")
        settings.add(this)
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

    fun setting(
        name: String,
        defaultValue: Boolean,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = BooleanSetting(name, defaultValue, description, visibility).register()

    inline fun <reified T : Enum<T>> setting(
        name: String,
        defaultValue: T,
        description: String = "",
        noinline
        visibility: () -> Boolean = { true },
    ) = EnumSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: Char,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = CharSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: String,
        multiline: Boolean = false,
        flags: Int = ImGuiInputTextFlags.None,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = StringSetting(name, defaultValue, multiline, flags, description, visibility).register()


    fun setting(
        name: String,
        defaultValue: Collection<Block>,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = BlockCollectionSetting(
        name,
        Registries.BLOCK.toList(),
        defaultValue.toMutableList(),
        description,
        visibility,
    ).register()

    fun setting(
        name: String,
        defaultValue: Collection<Item>,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = ItemCollectionSetting(
        name,
        Registries.ITEM.toList(),
        defaultValue.toMutableList(),
        description,
        visibility,
    ).register()

    inline fun <reified T : Any> setting(
        name: String,
        immutableList: Collection<T>,
        defaultValue: Collection<T> = immutableList,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = CollectionSettings(
        name,
        immutableList,
        defaultValue.toMutableList(),
        TypeToken.getParameterized(Collection::class.java, T::class.java).type,
        description,
        visibility,
    ).register()

    // ToDo: Actually implement maps
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

    fun setting(
        name: String,
        defaultValue: Double,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = DoubleSetting(name, defaultValue, range, step, description, unit, visibility).register()

    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = FloatSetting(name, defaultValue, range, step, description, unit, visibility).register()

    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = IntegerSetting(name, defaultValue, range, step, description, unit, visibility).register()

    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = LongSetting(name, defaultValue, range, step, description, unit, visibility).register()

    fun setting(
        name: String,
        defaultValue: Bind,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = KeybindSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: KeyCode,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = KeybindSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: Color,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = ColorSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: Vec3d,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Vec3dSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: BlockPos.Mutable,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = BlockPosSetting(name, defaultValue, description, visibility).register()

    fun setting(
        name: String,
        defaultValue: BlockPos,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = BlockPosSetting(name, defaultValue, description, visibility).register()

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
