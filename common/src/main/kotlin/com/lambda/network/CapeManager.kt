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

import com.github.kittinunf.fuel.core.FuelError
import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.network.api.v1.endpoints.getCape
import com.lambda.sound.SoundManager.toIdentifier
import com.lambda.util.FolderRegister.capes
import net.minecraft.client.texture.NativeImage.read
import net.minecraft.client.texture.NativeImageBackedTexture
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
     * Fetches the cape of the given player id
     *
     * @throws FuelError if something wrong happens
     */
    fun SafeContext.fetch(uuid: UUID) = getOrPut(uuid) {
        getCape(uuid)
            .fold(
                success = {
                    if (!images.contains(it.cape)) it.fetch()
                    put(uuid, it.cape)
                },
                failure = { throw it },
            )
    }

    override fun load() = "Loaded ${images.size} cached capes"

    init {
        listen<WorldEvent.Player.Join>(alwaysListen = true) {
            fetch(it.uuid)
        }
    }
}
