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

package com.lambda.util

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import kotlin.reflect.KProperty

class OneSetPerTick<T>(
    private var value: T,
    private val throwOnLimitBreach: Boolean = false,
    private val resetAfterTick: Boolean = false
) {
    val defaultValue = value

    var setThisTick = false
        private set

    var destroyed = false
        private set

    init {
        instances.add(this)
    }

    @Suppress("Unused")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (destroyed) throw IllegalStateException("Value accessed after being destroyed")
        return value ?: throw UninitializedPropertyAccessException()
    }

    @Suppress("Unused")
    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) = set(newValue)

    fun set(newValue: T) {
        if (destroyed) throw IllegalStateException("Value set after being destroyed")
        if (setThisTick && newValue != value) {
            if (throwOnLimitBreach) throw IllegalStateException("Value already written this tick")
            return
        }
        setThisTick = true
        value = newValue
    }

    private fun reset() {
        value = defaultValue
    }

    fun destroy() {
        destroyed = true
        instances.remove(this)
    }

    companion object {
        private val instances = linkedSetOf<OneSetPerTick<*>>()

        init {
            listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
                instances.forEach {
                    it.setThisTick = false
                    if (it.resetAfterTick) it.reset()
                }
            }
        }
    }
}