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

package com.lambda.network

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.config.Configurable
import com.lambda.config.configurations.SecretsConfig
import com.lambda.core.Loadable
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.network.api.v1.endpoints.getCape
import com.lambda.network.api.v1.endpoints.getCapes
import com.lambda.network.api.v1.endpoints.setCape
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runIO
import com.lambda.util.FileUtils.createIfNotExists
import com.lambda.util.FileUtils.downloadCompare
import com.lambda.util.FileUtils.downloadIfNotPresent
import com.lambda.util.FileUtils.ifNotExists
import com.lambda.util.FileUtils.isOlderThan
import com.lambda.util.FolderRegister.capes
import com.lambda.util.StringUtils.asIdentifier
import com.lambda.util.extension.resolveFile
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

object CapeHandler : Configurable(SecretsConfig), Loadable {
    override val name: String = "capes"

    var currentCape by setting("cape", "")
        .onValueChangeUnsafe { _, to -> updateCape(to) }

    val cache = ConcurrentHashMap<UUID, String>()
    private val fetchQueue = mutableListOf<UUID>()

    val availableCapes = runBlocking {
        capes.resolveFile("capes.txt")
            .isOlderThan(24.hours) {
                it.downloadIfNotPresent("${LambdaAPI.capes}.txt")
                    .onFailure { err -> LOG.error("Could not download the cape list: $err") }
            }
            .ifNotExists {
                it.downloadCompare("${LambdaAPI.capes}.txt", -1)
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

