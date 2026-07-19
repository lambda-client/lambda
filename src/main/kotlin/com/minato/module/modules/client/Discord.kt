
package com.minato.module.modules.client

import com.minato.Minato
import com.minato.Minato.LOG
import com.minato.context.SafeContext
import com.minato.event.EventFlow
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listenOnce
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.network.NetworkHandler.updateToken
import com.minato.network.api.v1.endpoints.linkDiscord
import com.minato.threading.runConcurrent
import com.minato.util.CommunicationUtils.warn
import com.minato.util.Nameable
import com.minato.util.extension.dimensionName
import com.minato.util.extension.fullHealth
import com.minato.util.extension.worldName
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.packet.inbound.impl.AuthenticatePacket
import dev.cbyrne.kdiscordipc.data.activity.largeImage
import dev.cbyrne.kdiscordipc.data.activity.smallImage
import dev.cbyrne.kdiscordipc.data.activity.timestamps
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
object Discord : Module(
	name = "Discord",
	description = "Discord Rich Presence configuration",
	tag = ModuleTag.CLIENT,
	enabledByDefault = true,
) {
	private val delay by setting("Update Delay", 5000L, 5000L..30000L, 100L, unit = "ms")
	private val showTime by setting("Show Time", true, description = "Show how long you have been playing for.")
	private val line1Left by setting("Line 1 Left", LineInfo.World)
	private val line1Right by setting("Line 1 Right", LineInfo.Username)
	private val line2Left by setting("Line 2 Left", LineInfo.Version)
	private val line2Right by setting("Line 2 Right", LineInfo.Fps)

	val rpc by lazy {
		KDiscordIPC(Minato.APP_ID, scope = EventFlow.minatoScope)
	}
	var connecting = AtomicBoolean(false)

	private val startup = System.currentTimeMillis()

	var discordAuth: AuthenticatePacket.Data? = null
		private set

	init {
		listenOnce<TickEvent.Pre> {
			runConcurrent {
				if (start()) handleLoop()
			}

			return@listenOnce true
		}

		onEnable {
			runConcurrent {
				if (start()) handleLoop()
			}
		}
		onDisable { stop() }
	}

	private suspend fun start(): Boolean {
		if (connecting.getAndSet(true)) return false

		rpc.connect()

		val auth = rpc.applicationManager.authenticate()

		linkDiscord(discordToken = auth.accessToken)
			.onSuccess {
				updateToken(it)
				discordAuth = auth
			}
			.onFailure {
				LOG.error(it)
				warn("Failed to link your discord account")
			}
		return true
	}

	private fun stop() {
		if (rpc.connected) rpc.disconnect()
		connecting.set(false)
	}

	private suspend fun SafeContext.handleLoop() {
		while (rpc.connected) {
			update()
			delay(delay)
		}
	}

	private suspend fun SafeContext.update() {
		rpc.activityManager.setActivity {
			details = "${line1Left.value(this@update)} | ${line1Right.value(this@update)}".take(128)
			state = "${line2Left.value(this@update)} | ${line2Right.value(this@update)}".take(128)

			largeImage("minato", Minato.VERSION)
			smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)
			//button("Download", "https://github.com/minato-client/lambda")

			if (showTime) timestamps(startup)
		}
	}

	private enum class LineInfo(val value: SafeContext.() -> String) : Nameable {
		Version({ Minato.VERSION }),
		World({ worldName }),
		Username({ mc.session.username }),
		Health({ "${player.fullHealth} HP" }),
		Hunger({ "${player.hungerManager.foodLevel} Hunger" }),
		Dimension({ world.dimensionName }),
		Fps({ "${mc.currentFps} FPS" });
	}
}
