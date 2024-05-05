package com.lambda.config.serializer.gui

import com.google.gson.*
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.module.ModuleRegistry
import com.lambda.util.math.Vec2d
import java.lang.reflect.Type

object CustomModuleWindowSerializer : JsonSerializer<CustomModuleWindow>, JsonDeserializer<CustomModuleWindow> {
    override fun serialize(
        src: CustomModuleWindow?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement = src?.let {
        JsonObject().apply {
            addProperty("title", it.title)
            add("modules", JsonArray().apply {
                it.modules.forEach {
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
        CustomModuleWindow(
            it["title"].asString,
            it["modules"].asJsonArray.mapNotNull { name ->
                ModuleRegistry.modules.firstOrNull { module ->
                    module.name == name.asString
                }
            } as MutableList,
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