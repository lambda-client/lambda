package com.lambda.config.serializer.gui

import com.google.gson.*
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.gui.impl.hudgui.LambdaHudGui
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
            addProperty("tag", it.tag.name)
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
        val tag = ModuleTag(it["tag"].asString)

        val gui = when (tag) {
            in ModuleTag.defaults -> LambdaClickGui
            in ModuleTag.hudDefaults -> LambdaHudGui
            else -> return@let null
        }

        TagWindow(tag, gui).apply {
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