package com.lambda.config.serializer.gui

import com.google.gson.*
import com.lambda.gui.impl.clickgui.windows.TagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d
import java.lang.reflect.Type

object TagWindowSerializer : JsonSerializer<TagWindow>, JsonDeserializer<TagWindow> {
    override fun serialize(
        src: TagWindow?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement = src?.let {
        JsonObject().apply {
            addProperty("title", it.title)
            add("tags", JsonArray().apply {
                it.tags.forEach { tag ->
                    add(tag.name)
                }
            })
            addProperty("width", it.width)
            addProperty("height", it.height)
            addProperty("isOpen", it.isOpen)
            add("position", JsonArray().apply {
                add(it.position.x)
                add(it.position.y)
            })
        }
    } ?: JsonNull.INSTANCE

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ) = json?.asJsonObject?.let {
        TagWindow(
            tags = it["tags"].asJsonArray.map { tag ->
                ModuleTag(tag.asString)
            }.toSet(),
            title = it["title"].asString,
            width = it["width"].asDouble,
            height = it["height"].asDouble
        ).apply {
            isOpen = it["isOpen"].asBoolean
            position = Vec2d(
                it["position"].asJsonArray[0].asDouble,
                it["position"].asJsonArray[1].asDouble
            )
        }
    } ?: throw JsonParseException("Invalid window data")
}