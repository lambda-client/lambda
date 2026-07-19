
package com.minato.gui.dsl

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
