
package com.minato

import com.minato.Minato.mapper
import com.minato.config.Deserializer
import com.minato.config.FallbackDeserializer
import com.minato.config.FallbackSerializer
import com.minato.config.FallbackSerializers
import com.minato.config.Serializer
import com.minato.core.Loader
import com.minato.event.events.ClientEvent
import com.minato.event.listener.UnsafeListener.Companion.listenOnceUnsafe
import com.minato.gui.components.ClickGuiLayout
import com.minato.util.ReflectionUtils.getInstances
import com.minato.util.WindowUtils.setMinatoWindowIcon
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

object Minato : ClientModInitializer {
	const val MOD_NAME = "Minato Client"
	const val MOD_ID = "minato"
	const val SYMBOL = "M"
	const val APP_ID = "1221289599427416127"
	const val REPO_URL = "https://github.com/lambda-client/lambda"
	val VERSION: String =
		FabricLoader.getInstance()
			.getModContainer("minato").orElseThrow()
			.metadata.version.friendlyString

	val LOG: Logger = LogManager.getLogger(SYMBOL)

	@JvmStatic
	val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

	val isDebug = System.getProperty("minato.dev") != null

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
			if (ClickGuiLayout.setMinatoWindowIcon) setMinatoWindowIcon()
			true
		}
	}
}
