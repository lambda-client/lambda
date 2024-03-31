package com.lambda.graphics.animation

import com.lambda.Lambda.mc
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.primitives.extension.partialTicks
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
            Animation(min) {
                val target = if (flag()) max else min
                lerp(it, target, speed)
            }.apply(::register)

        fun AnimationTicker.linear(min: Double, max: Double, step: Double, flag: () -> Boolean) =
            Animation(min) {
                val target = if (flag()) max else min
                target.coerceIn(it - step, it + step)
            }.apply(::register)
    }
}

