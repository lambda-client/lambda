package com.lambda.config.settings

import com.lambda.config.AbstractSetting
import kotlin.math.round
import kotlin.reflect.KProperty

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