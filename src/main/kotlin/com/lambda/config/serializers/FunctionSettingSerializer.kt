/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("unused")

package com.lambda.config.serializers

import com.lambda.config.Deserializer
import com.lambda.config.Serializer
import com.lambda.config.settings.FunctionSetting
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object FunctionSettingSerializer : Serializer<FunctionSetting<*>>(FunctionSetting::class.java) {
	override fun serialize(functionSetting: FunctionSetting<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeNull()
	}
}

object FunctionSettingDeserializer : Deserializer<FunctionSetting<*>>(FunctionSetting::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FunctionSetting<*> {
		throw initFromJsonException("FunctionSetting")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, functionSetting: FunctionSetting<*>): FunctionSetting<*> = functionSetting
}