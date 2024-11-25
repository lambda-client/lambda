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

package com.lambda.config

import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.Nameable
import java.lang.reflect.Type
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Represents a setting with a [defaultValue], [visibility] condition, and [description].
 * This setting is serializable ([Jsonable]) and has a [name].
 *
 * When the [value] is modified, all registered [listeners] are notified.
 * The [visibility] of the setting can be checked with the [isVisible] property.
 * The setting can be [reset] to its [defaultValue].
 *
 * Simple Usage:
 * ```kotlin
 * // this uses the delegate (by) association to access the setting value in the code directly.
 * val mode by setting("Mode", Modes.FREEZE, { page == Page.CUSTOM }, "The mode of the module.")
 *
 * init {
 *     listener<TickEvent.Pre> {
 *         LOG.info("Mode: $mode") // direct access of the value
 *     }
 * }
 * ```
 *
 * Advanced usage with listeners:
 * ```kotlin
 * // notice how this does not use the delegate (by) association, to access the setting object to register listeners.
 * val mode = setting("Mode", Modes.FREEZE, { page == Page.CUSTOM }, "The mode of the module.")
 *
 * init {
 *     mode.listener { from, to ->
 *        // Do something when the mode changes in a safe context
 *     }
 *     mode.unsafeListener { from, to ->
 *        // Do something when the mode changes in an unsafe context
 *     }
 *
 *     listener<TickEvent.Pre> {
 *         LOG.info("Mode: ${mode.value}") // indirect access of the value
 *     }
 * }
 * ```
 *
 * @property defaultValue The default value of the setting.
 * @property description A description of the setting.
 * @property type The type reflection of the setting.
 * @property visibility A function that determines whether the setting is visible.
 */
abstract class AbstractSetting<T : Any>(
    private val defaultValue: T,
    protected val type: Type,
    val description: String,
    val visibility: () -> Boolean,
) : Jsonable, Nameable {
    private val listeners = mutableListOf<ValueListener<T>>()

    var value: T by Delegates.observable(defaultValue) { _, from, to ->
        listeners.forEach {
            if (it.requiresValueChange && from == to) return@forEach
            it.execute(from, to)
        }
    }

    private val isVisible get() = visibility()
    val isModified get() = value != defaultValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: T) {
        value = valueIn
    }

    override fun toJson(): JsonElement =
        gson.toJsonTree(value, type)

    override fun loadFromJson(serialized: JsonElement) {
        value = gson.fromJson(serialized, type)
    }

    fun onValueChange(block: SafeContext.(from: T, to: T) -> Unit) {
        listeners.add(ValueListener(true) { from, to ->
            runSafe {
                block(from, to)
            }
        })
    }

    fun onValueChangeUnsafe(block: (from: T, to: T) -> Unit) {
        listeners.add(ValueListener(true, block))
    }

    fun onValueSet(block: (from: T, to: T) -> Unit) {
        listeners.add(ValueListener(false, block))
    }

    private fun reset() {
        value = defaultValue
    }

    class ValueListener<T>(val requiresValueChange: Boolean, val execute: (from: T, to: T) -> Unit)

    override fun toString() = "Setting $name: $value of type ${type.typeName}"
}
