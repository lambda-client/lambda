package com.lambda.graphics.animation

import com.lambda.Lambda.mc
import com.lambda.util.math.lerp
import com.lambda.util.extension.partialTicks
import kotlin.math.abs
import kotlin.reflect.KProperty

class Animation(initialValue: Double, val update: (Double) -> Double) {
    private var prevValue = initialValue
    private var currValue = initialValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>) =
        lerp(mc.partialTicks, prevValue, currValue)

    operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: Double) = setValue(valueIn)

    fun setValue(valueIn: Double) {
        prevValue = valueIn
        currValue = valueIn
    }

    fun tick() {
        prevValue = currValue
        currValue = update(currValue)
    }

    companion object {
        fun AnimationTicker.exp(min: () -> Double, max: () -> Double, speed: Double, flag: () -> Boolean) =
            exp(min, max, { speed }, flag)

        fun AnimationTicker.exp(min: Double, max: Double, speed: () -> Double, flag: () -> Boolean) =
            exp({ min }, { max }, speed, flag)

        fun AnimationTicker.exp(min: Double, max: Double, speed: Double, flag: () -> Boolean) =
            exp({ min }, { max }, { speed }, flag)

        fun AnimationTicker.exp(target: () -> Double, speed: Double) =
            exp(target, target, { speed }, { true })

        fun AnimationTicker.exp(min: () -> Double, max: () -> Double, speed: () -> Double, flag: () -> Boolean) =
            Animation(min()) {
                val min = min()
                val max = max()
                val target = if (flag()) max else min

                if (abs(target - it) < CLAMP * abs(max - min)) target
                else lerp(speed(), it, target)
            }.apply(::register)

        // Exponent animation never reaches target value
        private const val CLAMP = 0.01
    }
}

