package com.lambda.graphics.animation

import com.lambda.Lambda.mc
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.primitives.extension.partialTicks
import kotlin.math.abs
import kotlin.reflect.KProperty

class Animation(initialValue: Double, val update: (Double) -> Double) {
    private var prevValue = initialValue
    private var currValue = initialValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>) =
        lerp(prevValue, currValue, mc.partialTicks)

    operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: Double) {
        prevValue = valueIn
        currValue = valueIn
    }

    fun tick() {
        prevValue = currValue
        currValue = update(currValue)
    }

    companion object {
        fun AnimationTicker.exp(min: Double, max: Double, speed: Double, flag: () -> Boolean) =
            exp({ min }, { max }, speed, flag)

        fun AnimationTicker.exp(min: () -> Double, max: () -> Double, speed: Double, flag: () -> Boolean) =
            Animation(min()) {
                val target = if (flag()) max() else min()
                if (abs(target - it) < CLAMP) target
                else lerp(it, target, speed)
            }.apply(::register)

        fun AnimationTicker.linear(min: Double, max: Double, step: Double, flag: () -> Boolean) =
            Animation(min) {
                val target = if (flag()) max else min
                target.coerceIn(it - step, it + step)
            }.apply(::register)

        fun AnimationTicker.linear(min: () -> Double, max: () -> Double, step: Double, flag: () -> Boolean) =
            Animation(min()) {
                val target = if (flag()) max() else min()
                target.coerceIn(it - step, it + step)
            }.apply(::register)

        // Exponent animation will never reach target value
        private const val CLAMP = 0.01
    }
}

