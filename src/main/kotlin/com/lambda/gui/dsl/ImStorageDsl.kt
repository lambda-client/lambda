/*
 * Copyright 2026 Lambda
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

package com.lambda.gui.dsl

import com.lambda.imgui.ImGui.getID
import com.lambda.imgui.ImGui.getStateStorage
import com.lambda.imgui.ImGuiStorage
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object ImStorageDsl {
    /**
     * Returns a hash of a string for the [ImGuiStorage]
     */
    val String.imHash: Int get() = getID(this)

    /**
     * Current persistent per-window storage (store e.g. tree node open/close state)
     */
    val storage: ImGuiStorage get() = getStateStorage()

    inline fun <reified T : Any> get(hash: Int, default: T) =
        when (T::class) {
            Boolean::class -> storage.getBool(hash, default as Boolean)
            Int::class -> storage.getInt(hash, default as Int)
            Float::class -> storage.getFloat(hash, default as Float)
            else -> throw IllegalStateException("Unknown type '${T::class}'")
        } as T

    inline fun <reified T : Any> set(hash: Int, value: T) =
        when (T::class) {
            Boolean::class -> storage.setBool(hash, value as Boolean)
            Int::class -> storage.setInt(hash, value as Int)
            Float::class -> storage.setFloat(hash, value as Float)
            else -> throw IllegalStateException("Unknown type '${T::class}'")
        }

    inline fun <reified T : Any> imProperty(str: String, default: T) = object : ReadWriteProperty<Any?, T> {
        // There is something I don't quite understand about kotlin typing 'smart' inference.
        // If you don't provide a value of type T, the compiler panics and uses a type that isn't of type T (??)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = get(str.imHash, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(str.imHash, value)
    }
}
