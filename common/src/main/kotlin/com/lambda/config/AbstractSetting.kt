package com.lambda.config

import com.google.gson.JsonElement
import com.lambda.Lambda.gson
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.Nameable
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

abstract class AbstractSetting<T : Any>(
    private val defaultValue: T,
    val visibility: () -> Boolean,
    val description: String,
) : Jsonable, Nameable {
    private val listeners = mutableListOf<(from: T, to: T) -> Unit>()

    var value: T by Delegates.observable(defaultValue) { _, from, to ->
        if (from == to) return@observable
        listeners.forEach { it(from, to) }
    }

    val isVisible get() = visibility()
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

    fun listener(block: SafeContext.(from: T, to: T) -> Unit) {
        listeners.add { from, to ->
            runSafe {
                block(from, to)
            }
        }
    }

    fun unsafeListener(block: (from: T, to: T) -> Unit) {
        listeners.add(block)
    }

    fun reset() {
        value = defaultValue
    }
}