package com.lambda.config.settings

import com.lambda.config.AbstractSetting
import kotlin.math.round
import kotlin.reflect.KProperty

/**
 * Represents a [NumericSetting] with a specific [range] and [step].
 *
 * The [value] of the setting is coerced into the specified [range] and rounded to the nearest [step].
 * The [visibility] and [description] of the setting are inherited from [AbstractSetting].
 *
 * @property range The range within which the setting's [value] must fall.
 * @property step The [step] to which the setting's [value] is rounded.
 * @property visibility A function that determines whether the setting [isVisible].
 * @property description A [description] of the setting.
 */
abstract class NumericSetting<T>(
    value: T,
    open val range: ClosedRange<T>,
    open val step: T,
    visibility: () -> Boolean,
    description: String,
) : AbstractSetting<T>(
    value,
    visibility,
    description
) where T : Number, T : Comparable<T> {
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: T) {
        value = valueIn.coerceIn(range).roundToStep(step)
    }

    private fun <T : Number> T.roundToStep(step: T): T {
        val doubleValue = this.toDouble()
        val doubleStep = step.toDouble()
        val result = round(doubleValue / doubleStep) * doubleStep
        return when (this) {
            is Byte -> result.toInt().toByte()
            is Short -> result.toInt().toShort()
            is Int -> result.toInt()
            is Long -> result.toLong()
            is Float -> result.toFloat()
            is Double -> result
            else -> throw IllegalArgumentException("Unsupported number type")
        } as T
    }
}