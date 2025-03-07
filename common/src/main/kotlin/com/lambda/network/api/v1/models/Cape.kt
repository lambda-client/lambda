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

package com.lambda.network.api.v1.models

import com.github.kittinunf.fuel.Fuel
import com.google.gson.annotations.SerializedName
import com.lambda.graphics.texture.TextureUtils
import com.lambda.sound.SoundManager.toIdentifier
import com.lambda.threading.runSafe
import com.lambda.util.Communication.logError
import com.lambda.util.FolderRegister.capes
import com.lambda.util.extension.resolveFile
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import org.lwjgl.BufferUtils
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream

class Cape(
    @SerializedName("url")
    val url: String,

    @SerializedName("type")
    val cape: String,
) {
    fun fetch() = runSafe {
        Fuel.download(url)
            .fileDestination { _, _ -> capes.resolveFile("$cape.png") }
            .response { result ->
                result.fold(
                    success = {
                        val image = TextureUtils.readImage(it)
                        val native = NativeImageBackedTexture(image)
                        val id = cape.toIdentifier()

                        mc.textureManager.registerTexture(id, native)
                    },
                    failure = { logError("Error while downloading capes", it) }
                )
            }
    }
}
