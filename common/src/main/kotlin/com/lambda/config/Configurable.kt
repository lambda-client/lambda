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
import com.lambda.config.settings.numeric.*
import com.lambda.util.Nameable

/**
 * Holds a set of [AbstractSetting]s that are associated with the [name] of the [Configurable].
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
            }?.loadFromJson(value) ?: LOG.warn("No saved setting found for $name with $value in ${this::class.simpleName}")
        }
    }

    fun setting(
        name: String,
        defaultValue: Boolean,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = BooleanSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    inline fun <reified T : Enum<T>> setting(
        name: String,
        defaultValue: T,
        noinline visibility: () -> Boolean = { true },
        description: String = ""
    ) = EnumSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    fun setting(
        name: String,
        defaultValue: String,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = StringSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: List<T>,
        noinline visibility: () -> Boolean = { true },
        description: String = ""
    ) = ListSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    inline fun <reified K : Any, V : Any> setting(
        name: String,
        defaultValue: Map<K, V>,
        noinline visibility: () -> Boolean = { true },
        description: String = ""
    ) = MapSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: Set<T>,
        noinline visibility: () -> Boolean = { true },
        description: String = ""
    ) = SetSetting(name, defaultValue, visibility, description).also {
        settings.add(it)
    }

    fun setting(
        name: String,
        defaultValue: Byte,
        range: ClosedRange<Byte>,
        step: Byte = 1,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = ByteSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    fun setting(
        name: String,
        defaultValue: Double,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = DoubleSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = FloatSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = IntegerSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = LongSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }

    fun setting(
        name: String,
        defaultValue: Short,
        range: ClosedRange<Short>,
        step: Short = 1,
        visibility: () -> Boolean = { true },
        description: String = ""
    ) = ShortSetting(name, defaultValue, range, step, visibility, description).also {
        settings.add(it)
    }
}