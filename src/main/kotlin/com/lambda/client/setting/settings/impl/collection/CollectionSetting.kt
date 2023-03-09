package com.lambda.client.setting.settings.impl.collection

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.client.setting.settings.ImmutableSetting

class CollectionSetting<E : Any, T : MutableCollection<E>>(
    name: String,
    override val value: T,
    visibility: () -> Boolean = { true },
    description: String = "",
    unit: String = ""
) : ImmutableSetting<T>(name, value, visibility, { _, input -> input }, description, unit), MutableCollection<E> by value {

    constructor(
        name: String,
        value: T,
        visibility: () -> Boolean = { true },
        description: String = "",
        unit: String = "",
        entryType: Class<E>, // type must be set if the default collection is empty
    ) : this(name, value, visibility, description, unit) {
        this.entryType = entryType
    }

    private var entryType: Class<E>? = null
    override val defaultValue: T = valueClass.newInstance()
    private val lockObject = Any()
    val editListeners = ArrayList<() -> Unit>()

    init {
        value.toCollection(defaultValue)
    }

    // Should be used instead of directly accessing value itself
    // TODO: Hook into [MutableSetting] and allow this to work with `this.value.add(someVal)`
    fun editValue(block: (value: CollectionSetting<E, T>) -> Unit) {
        block.invoke(this)
        editListeners.forEach { it.invoke() }
    }

    override fun resetValue() {
        synchronized(lockObject) {
            value.clear()
            value.addAll(defaultValue)
            editListeners.forEach { it.invoke() }
        }
    }

    override fun write(): JsonElement = gson.toJsonTree(value)

    override fun read(jsonElement: JsonElement?) {
        jsonElement?.asJsonArray?.let {
            val cacheArray = gson.fromJson<Array<E>>(it, TypeToken.getArray(entryType ?: value.first().javaClass).type)
            synchronized(lockObject) {
                value.clear()
                value.addAll(cacheArray)
            }
        }
    }

    override fun toString() = value.joinToString { it.toString() }

}