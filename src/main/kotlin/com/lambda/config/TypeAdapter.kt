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

package com.lambda.config

import com.lambda.Lambda
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

interface Stringifiable<T> { fun stringify(value: T): String }

abstract class TypeAdapter<T> {
	val mapper by lazy { Lambda.mapper }
	abstract val type: Class<T>
	abstract val serializer: StdSerializer<T>
	abstract val deserializer: StdDeserializer<T>

	context(simpleModule: SimpleModule)
	fun register() {
		with(simpleModule) {
			addSerializer<T>(type, serializer)
			addDeserializer<T>(type, deserializer)
		}
	}

	fun initFromJsonException(objName: String) = IllegalStateException("Attempted to initialize a $objName directly from JSON! All $objName's should be updated after standard initialization.")

	protected typealias Serializer<T> = StdSerializer<T>
	protected typealias Deserializer<T> = StdDeserializer<T>
}