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

package com.lambda.config

import com.lambda.Lambda
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

abstract class Serializer<T>(type: Class<T>) : BaseSerializer<T>(type)
abstract class Deserializer<T>(type: Class<T>) : BaseDeserializer<T>(type)

abstract class FallbackSerializer<T>(type: Class<T>) : BaseSerializer<T>(type)
abstract class FallbackDeserializer<T>(type: Class<T>) : BaseDeserializer<T>(type)

interface Stringifiable<T> { fun stringify(value: T): String }

sealed class BaseSerializer<T>(final override val type: Class<T>) : StdSerializer<T>(type), JsonModRegisterable<T> {
	override val mapper by lazy { Lambda.mapper }

	context(simpleModule: SimpleModule)
	override fun register() {
		with(simpleModule) {
			addSerializer<T>(type, this@BaseSerializer)
		}
	}
}
sealed class BaseDeserializer<T>(final override val type: Class<T>) : StdDeserializer<T>(type), JsonModRegisterable<T> {
	override val mapper by lazy { Lambda.mapper }

	context(simpleModule: SimpleModule)
	override fun register() {
		with(simpleModule) {
			addDeserializer<T>(type, this@BaseDeserializer)
		}
	}
}

interface JsonModRegisterable<T> {
	val mapper: JsonMapper
	val type: Class<T>

	context(simpleModule: SimpleModule)
	fun register()

	fun initFromJsonException(objName: String) = IllegalStateException("Attempted to initialize a $objName directly from JSON! All $objName's should be updated after standard initialization.")
}
