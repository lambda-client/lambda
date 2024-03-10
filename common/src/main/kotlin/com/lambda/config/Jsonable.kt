package com.lambda.config

import com.google.gson.JsonElement


interface Jsonable {
    fun toJson(): JsonElement
    fun loadFromJson(serialized: JsonElement)
}