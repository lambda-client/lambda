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

import com.lambda.Lambda.LOG
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.Identifier
import java.io.IOException

class CustomShaderPipelineCache(
    private val pipelineFactory: (String) -> RenderPipeline,
    private val source: (Identifier, ShaderType) -> String,
    private val failureMessage: String
) {
    private val pipelines = mutableMapOf<String, RenderPipeline>()
    private val failed = mutableSetOf<String>()

    fun clear() {
        pipelines.clear()
        failed.clear()
    }

    fun getOrDefault(shaderName: String, fallback: RenderPipeline): RenderPipeline {
        if (shaderName in failed) return fallback

        return try {
            pipelines.getOrPut(shaderName) { create(shaderName) }
        } catch (e: RuntimeException) {
            failed.add(shaderName)
            LOG.error(failureMessage, shaderName, e)
            fallback
        }
    }

    private fun create(shaderName: String): RenderPipeline {
        val pipeline = pipelineFactory(shaderName)
        val compiled: CompiledRenderPipeline = RenderSystem.getDevice().precompilePipeline(pipeline) { identifier, type ->
            try {
                source(identifier, type)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        check(compiled.isValid) { "Custom shader pipeline failed to compile: $shaderName" }
        return pipeline
    }
}
