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

package com.lambda

import com.lambda.Lambda.mapper
import com.lambda.config.Deserializer
import com.lambda.config.FallbackDeserializer
import com.lambda.config.FallbackSerializer
import com.lambda.config.FallbackSerializers
import com.lambda.config.Serializer
import com.lambda.core.Loader
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenOnceUnsafe
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.util.ReflectionUtils.getInstances
import com.lambda.util.WindowUtils.setLambdaWindowIcon
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.core.util.Separators
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.type.TypeFactory
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

object Lambda : ClientModInitializer {
	const val MOD_NAME = "Lambda"
	const val MOD_ID = "lambda"
	const val SYMBOL = "λ"
	const val APP_ID = "1221289599427416127"
	const val REPO_URL = "https://github.com/lambda-client/lambda"
	val VERSION: String =
		FabricLoader.getInstance()
			.getModContainer("lambda").orElseThrow()
			.metadata.version.friendlyString

	val LOG: Logger = LogManager.getLogger(SYMBOL)

	@JvmStatic
	val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

	val isDebug = System.getProperty("lambda.dev") != null

	/**
	 * A Jackson [tools.jackson.databind.json.JsonMapper].
	 *
	 * Jackson is used over Gson (unlike Minecraft) as it allows for updating existing objects
	 * rather than creating new instances when deserializing.
	 *
	 * We use the base [tools.jackson.module.kotlin.KotlinModule] with `SingletonSupport` disabled, as it overrides our serialization.
	 * We also use a simple module for our standard serializers and deserializers.
	 * Finally, we use a custom module that searches through the supertypes of the given object to find
	 * the closest related type with a registered serializer or deserializer, depending on the action.
	 */
	val mapper = jsonMapper {
		defaultPrettyPrinter(
			DefaultPrettyPrinter(
				Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER)
			).apply {
				val tabIndenter = DefaultIndenter("\t", DefaultIndenter.SYS_LF)
				indentObjectsWith(tabIndenter)
				indentArraysWith(tabIndenter)
			}
		)
		enable(SerializationFeature.INDENT_OUTPUT)
		addModules(
			object : SimpleModule() {
				override fun setupModule(context: SetupContext) {
					val fallbackSerializers = FallbackSerializers(
						getInstances<FallbackSerializer<*>>().associateBy { it.type },
						getInstances<FallbackDeserializer<*>>().associateBy { it.type }
					)
					context.addSerializers(fallbackSerializers)
					context.addDeserializers(fallbackSerializers)
					super.setupModule(context)
				}
			},
			SimpleModule().apply {
				getInstances<Serializer<*>>().forEach { it.register() }
				getInstances<Deserializer<*>>().forEach { it.register() }
			},
			kotlinModule { disable(KotlinFeature.SingletonSupport) }
		)
	}

	/**
	 * The configured [TypeFactory] produced by [mapper].
	 */
	val typeFactory: TypeFactory = mapper.typeFactory

	override fun onInitializeClient() {} // nop

	init {
		// We want the opengl context to be created
		listenOnceUnsafe<ClientEvent.Startup>({ Int.MAX_VALUE }) {
			LOG.info("$MOD_NAME $VERSION initialized in ${Loader.initialize()} ms\n")
			if (ClickGuiLayout.setLambdaWindowIcon) setLambdaWindowIcon()
			true
		}
	}
}
