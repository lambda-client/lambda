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

package com.lambda.graphics.animation

import com.lambda.Lambda.mc
import com.lambda.util.extension.partialTicks
import com.lambda.util.math.lerp
import kotlin.math.abs
import kotlin.reflect.KProperty

class Animation(initialValue: Double, val update: (Double) -> Double) {
    private var prevValue = initialValue
    private var currValue = initialValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: Double) = setValue(valueIn)

    fun value(): Double = lerp(mc.partialTicks, prevValue, currValue)

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

