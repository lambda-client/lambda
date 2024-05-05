package com.lambda.config

import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.Nameable
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
 * @property visibility A function that determines whether the setting is visible.
 */
abstract class AbstractSetting<T : Any>(
    private val defaultValue: T,
    val description: String,
    val visibility: () -> Boolean,
) : Jsonable, Nameable {
    private val listeners = mutableListOf<(from: T, to: T) -> Unit>()

    var value: T by Delegates.observable(defaultValue) { _, from, to ->
        if (from == to) return@observable
        listeners.forEach { it(from, to) }
    }

    private val isVisible get() = visibility()
    val isModified get() = value != defaultValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: T) {
        value = valueIn
    }

    override fun toJson(): JsonElement =
        gson.toJsonTree(value)

    override fun loadFromJson(serialized: JsonElement) {
        value = gson.fromJson(serialized, value::class.java)
    }

    fun onValueChange(block: SafeContext.(from: T, to: T) -> Unit) {
        listeners.add { from, to ->
            runSafe {
                block(from, to)
            }
        }
    }

    fun onValueChangeUnsafe(block: (from: T, to: T) -> Unit) {
        listeners.add(block)
    }

    private fun reset() {
        value = defaultValue
    }
}