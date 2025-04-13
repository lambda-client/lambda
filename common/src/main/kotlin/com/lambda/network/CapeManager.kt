/*
 * Copyright 2025 Lambda
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

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.texture.TextureUtils
import com.lambda.network.api.v1.endpoints.getCape
import com.lambda.network.api.v1.endpoints.setCape
import com.lambda.network.api.v1.models.Cape
import com.lambda.sound.SoundManager.toIdentifier
import com.lambda.threading.runIO
import com.lambda.util.FolderRegister.capes
import com.lambda.util.extension.get
import com.lambda.util.extension.resolveFile
import net.minecraft.client.texture.NativeImage.read
import net.minecraft.client.texture.NativeImageBackedTexture
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object CapeManager : ConcurrentHashMap<UUID, String>(), Loadable {
    /**
     * We want to cache images to reduce cloudflare requests and save money
     */
    private val images = capes.walk()
        .filter { it.extension == "png" }
        .associate { it.nameWithoutExtension to NativeImageBackedTexture(read(it.inputStream())) }
        .onEach { (key, value) -> mc.textureManager.registerTexture(key.toIdentifier(), value) }

    /**
     * Sets the current player's cape
     *
     * @param block Lambda called once the coroutine completes, it contains the throwable if any
     */
    fun updateCape(cape: String, block: (Throwable?) -> Unit = {}) = runIO {
        setCape(cape).getOrThrow()
    }.invokeOnCompletion { block(it) }

    /**
     * Fetches the cape of the given player id
     *
     * @param block Lambda called once the coroutine completes, it contains the throwable if any
     */
    fun SafeContext.fetchCape(uuid: UUID, block: (Throwable?) -> Unit = {}) = runIO {
        val cape = getCape(uuid).getOrThrow()

        mc.textureManager.get(cape.identifier) ?: download(cape)
        put(uuid, cape.id)
    }.invokeOnCompletion { block(it) }

    private fun SafeContext.download(cape: Cape, block: (Throwable?) -> Unit = {}) = runIO {
        val destination = capes.resolveFile("${cape.id}.png")
        val output = ByteArrayOutputStream()

        LambdaHttp.download(cape.url, output)

        val bytes = output.toByteArray()
        destination.writeBytes(bytes)

        val image = TextureUtils.readImage(bytes)
        val native = NativeImageBackedTexture(image)
        val id = cape.identifier

        mc.textureManager.registerTexture(id, native)
    }.invokeOnCompletion { block(it) }

    override fun load() = "Loaded ${images.size} cached capes"

    init {
        listen<WorldEvent.Player.Join>(alwaysListen = true) {
            fetchCape(it.uuid)
        }
    }
}
