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
import com.lambda.config.FallbackTypeAdapter
import com.lambda.config.FallbackTypeAdapters
import com.lambda.config.TypeAdapter
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
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.type.TypeFactory
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.kotlinModule

object Lambda : ClientModInitializer {
    const val ModName = "Lambda"
    const val ModId = "lambda"
    const val Symbol = "λ"
    const val AppId = "1221289599427416127"
    const val RepoUrl = "https://github.com/lambda-client/lambda"
    val Version: String =
        FabricLoader.getInstance()
            .getModContainer("lambda").orElseThrow()
            .metadata.version.friendlyString

    val Log: Logger = LogManager.getLogger(Symbol)

    @JvmStatic
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

    val isDebug = System.getProperty("lambda.dev") != null

    /**
     * A Jackson [tools.jackson.databind.json.JsonMapper].
     *
     * We use Jackson over Gson (unlike Minecraft) as it allows for updating existing objects
     * rather than creating new instances when deserializing.
     */
    val mapper = jsonMapper {
        enable(SerializationFeature.INDENT_OUTPUT)
        addModules(
	        kotlinModule { disable(KotlinFeature.SingletonSupport) },
            SimpleModule().apply {
                getInstances<TypeAdapter<*>>().forEach { registerable ->
                    registerable.register()
                }
            },
            object : SimpleModule() {
                override fun setupModule(context: SetupContext) {
                    val fallbackSerializers = FallbackTypeAdapters(getInstances<FallbackTypeAdapter<*>>().associateBy { it.type })
                    context.addSerializers(fallbackSerializers)
                    context.addDeserializers(fallbackSerializers)
                    super.setupModule(context)
                }
            }
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
            Log.info("$ModName $Version initialized in ${Loader.initialize()} ms\n")
            if (ClickGuiLayout.setLambdaWindowIcon) setLambdaWindowIcon()
            true
        }
    }
}
