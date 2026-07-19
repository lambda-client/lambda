
package com.minato.network

import com.minato.Minato.LOG
import com.minato.Minato.mc
import com.minato.config.Config
import com.minato.config.categories.SecretsCategory
import com.minato.config.entries.Setting.Companion.onValueChangeUnsafe
import com.minato.core.Loadable
import com.minato.event.events.WorldEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.network.api.v1.endpoints.getCape
import com.minato.network.api.v1.endpoints.getCapes
import com.minato.network.api.v1.endpoints.setCape
import com.minato.threading.runGameScheduled
import com.minato.threading.runIO
import com.minato.util.FileUtils.createIfNotExists
import com.minato.util.FileUtils.downloadCompare
import com.minato.util.FileUtils.downloadIfNotPresent
import com.minato.util.FileUtils.ifNotExists
import com.minato.util.FileUtils.isOlderThan
import com.minato.util.FolderRegistry.capes
import com.minato.util.StringUtils.asIdentifier
import com.minato.util.extension.resolveFile
import kotlinx.coroutines.runBlocking
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImage.read
import net.minecraft.client.texture.NativeImageBackedTexture
import org.lwjgl.BufferUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
object CapeHandler : Config(
    "capes",
    SecretsCategory
), Loadable {
    var currentCape by setting("cape", "")
        .onValueChangeUnsafe { _, to -> updateCape(to) }

    val cache = ConcurrentHashMap<UUID, String>()
    private val fetchQueue = mutableListOf<UUID>()

    val availableCapes = runBlocking {
        capes.resolveFile("capes.txt")
            .isOlderThan(24.hours) {
                it.downloadIfNotPresent("${MinatoAPI.capes}.txt")
                    .onFailure { err -> LOG.error("Could not download the cape list: $err") }
            }
            .ifNotExists {
                it.downloadCompare("${MinatoAPI.capes}.txt", -1)
                    .onFailure { err -> LOG.error("Could not download the cape list: $err") }
            }
            .createIfNotExists()
            .readText()
            .split(Regex("\\s+"))
    }

    fun updateCape(cape: String, block: (Throwable?) -> Unit = {}) = runIO {
        setCape(cape).getOrThrow()
        fetchCape(mc.gameProfile.id)
    }.invokeOnCompletion { block(it) }

    fun fetchCape(uuid: UUID, block: (Throwable?) -> Unit = {}) = runIO {
        val cape = getCape(uuid).getOrNull() ?: return@runIO

        val bytes = capes.resolveFile("${cape.id}.png")
            .downloadIfNotPresent(cape.url).getOrNull()
            ?.readBytes() ?: return@runIO

        val buffer = BufferUtils
            .createByteBuffer(bytes.size)
            .put(bytes)
            .flip()

        val image = read(NativeImage.Format.RGBA, buffer)

        runGameScheduled { mc.textureManager.registerTexture(cape.id.asIdentifier, NativeImageBackedTexture({ cape.id }, image)) }

        cache[uuid] = cape.id
    }.invokeOnCompletion { block(it) }

    override fun load() = "Loaded ${availableCapes.size} capes"

    init {
        fixedRateTimer(
            daemon = true,
            name = "Cape-fetcher",
            period = 15.seconds.inWholeMilliseconds,
        ) {
            if (fetchQueue.isEmpty()) return@fixedRateTimer

            runBlocking {
                getCapes(fetchQueue)
                    .onSuccess { it.forEach { cape -> cache[cape.uuid] = cape.id } }

                fetchQueue.clear()
            }
        }

        listen<WorldEvent.Player.Join>(alwaysListen = true) { fetchQueue.add(it.uuid) }
        listen<WorldEvent.Player.Leave>(alwaysListen = true) { fetchQueue.remove(it.uuid) }
    }
}

