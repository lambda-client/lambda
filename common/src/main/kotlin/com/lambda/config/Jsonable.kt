package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.Gson


/**
 * Interface for objects that can be serialized to and deserialized from JSON ([Gson]).
 */
interface Jsonable {
    /** Serializes the object to a [JsonElement] */
    fun toJson(): JsonElement

    /** Loads the object's state from a [JsonElement] */
    fun loadFromJson(serialized: JsonElement)
}