
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.Serializer
import com.minato.config.settings.FunctionSetting
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object FunctionSettingSerializer : Serializer<FunctionSetting<*, *>>(FunctionSetting::class.java) {
	override fun serialize(functionSetting: FunctionSetting<*, *>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeNull()
	}
}

object FunctionSettingDeserializer : Deserializer<FunctionSetting<*, *>>(FunctionSetting::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FunctionSetting<*, *> {
		throw initFromJsonException("FunctionSetting")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, functionSetting: FunctionSetting<*, *>): FunctionSetting<*, *> = functionSetting
}