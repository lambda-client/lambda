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

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.requests.CancellableRequest
import com.lambda.Lambda
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
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.FileUtils.downloadIfNotPresent
import com.lambda.util.FolderRegister.capes
import com.lambda.util.extension.get
import com.lambda.util.extension.resolveFile
import net.minecraft.client.texture.NativeImage.read
import net.minecraft.client.texture.NativeImageBackedTexture
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk

@OptIn(ExperimentalPathApi::class)
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
     */
    fun SafeContext.updateCape(cape: String): CancellableRequest =
        setCape(cape,
            success = { fetchCape(player.uuid); info("Successfully update your cape to $cape") },
            failure = { logError("Could not update the player cape", it) }
        )

    /**
     * Fetches the cape of the given player id
     */
    fun SafeContext.fetchCape(uuid: UUID): CancellableRequest =
        getCape(uuid,
            success = { mc.textureManager.get(it.identifier) ?: download(it); put(uuid, it.id) },
            failure = { logError("Could not fetch the cape of the player", it) }
        )

    private fun SafeContext.download(cape: Cape): File =
        capes.resolveFile("${cape.id}.png")
            .downloadIfNotPresent(cape.url,
                success = {
                    val image = TextureUtils.readImage(it)
                    val native = NativeImageBackedTexture(image)
                    val id = cape.identifier

                    Lambda.mc.textureManager.registerTexture(id, native)
                },
                failure = { logError("Could not download the cape", it) },
            )

    override fun load() = "Loaded ${images.size} cached capes"

    init {
        listen<WorldEvent.Player.Join>(alwaysListen = true) {
            fetchCape(it.uuid)
        }
    }
}
