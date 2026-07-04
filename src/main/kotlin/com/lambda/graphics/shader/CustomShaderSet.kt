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

package com.lambda.graphics.shader

import com.lambda.graphics.mc.LambdaRenderPipelines
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.ShaderType
import net.minecraft.util.Identifier
import java.nio.file.Path

class CustomShaderSet(
    folder: Path,
    exampleName: String,
    exampleShader: String,
    pathPrefix: String,
    templateMarker: String,
    templateShader: Identifier,
    prepareFolderError: String,
    listShadersError: String,
    pipelineFactory: (CustomShaderSet, String) -> RenderPipeline,
    pipelineFailureMessage: String
) {
    companion object {
        const val NONE = FileBackedCustomShaders.NONE
    }

    private val shaders = FileBackedCustomShaders(
        folder,
        exampleName,
        exampleShader,
        pathPrefix,
        templateMarker,
        templateShader,
        prepareFolderError,
        listShadersError
    )

    private val cache = CustomShaderPipelineCache(
        pipelineFactory = { shaderName -> pipelineFactory(this, shaderName) },
        source = { identifier, type ->
            if (type == ShaderType.FRAGMENT && shaders.handles(identifier)) shaders.loadSource(identifier)
            else LambdaRenderPipelines.getShaderSource(identifier, type)
        },
        failureMessage = pipelineFailureMessage
    )

    @Synchronized
    fun ensureFolder() {
        shaders.ensureFolder()
    }

    @Synchronized
    fun getOptions(): Array<String> = shaders.getOptions()

    @Synchronized
    fun isSelected(name: String?): Boolean = shaders.isSelected(name)

    fun clear() {
        cache.clear()
        shaders.clearState()
    }

    fun getOrDefault(shaderName: String, fallback: RenderPipeline): RenderPipeline =
        cache.getOrDefault(shaderName, fallback)

    fun getShaderIdentifier(name: String): Identifier = shaders.getShaderIdentifier(name)

    fun getEncodedName(name: String): String = shaders.getEncodedName(name)

    @Synchronized
    fun getVersionToken(name: String): String = shaders.getVersionToken(name)
}
