package com.lambda.config.serializer.gui

import com.google.gson.*
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.clickgui.windows.tag.CustomTagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d
import java.lang.reflect.Type

object CustomTagWindowSerializer : JsonSerializer<CustomTagWindow>, JsonDeserializer<CustomTagWindow> {
    override fun serialize(
        src: CustomTagWindow?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement = src?.let {
        JsonObject().apply {
            addProperty("title", it.title)
            add("tags", JsonArray().apply {
                it.tags.forEach {
                    add(it.name)
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
    )  = json?.asJsonObject?.let {
        CustomTagWindow(
            it["title"].asString,
            it["tags"].asJsonArray.map { tag ->
                ModuleTag(tag.asString)
            }.toSet(),
            LambdaClickGui
        ).apply {
            width = it["width"].asDouble
            height = it["height"].asDouble
            isOpen = it["isOpen"].asBoolean
            position = Vec2d(
                it["position"].asJsonArray[0].asDouble,
                it["position"].asJsonArray[1].asDouble
            )
        }
    } ?: throw JsonParseException("Invalid window data")
}