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

import com.lambda.Lambda
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.util.FolderRegistry
import net.minecraft.util.Identifier

object CustomShaders {
    val OUTLINE = CustomShaderSet(
        folder = FolderRegistry.outlineShaders,
        exampleName = "example-outline.frag",
        exampleShader = """
            vec4 lambda_applyCustom(vec4 baseColor, float edgeFactor, bool isOutline) {
                float pulse = 0.5 + 0.5 * sin(u_Time * 2.0 + v_TexCoord.y * 12.0);
                vec3 tint = mix(baseColor.rgb, vec3(0.35, 0.8, 1.0), pulse * 0.35);
                float glow = isOutline ? edgeFactor * 0.2 : 0.0;
                return vec4(clamp(tint + glow, 0.0, 1.0), baseColor.a);
            }
        """.trimIndent(),
        pathPrefix = "shaders/core/custom/outline/",
        templateMarker = "vec4 lambda_applyCustom(vec4 baseColor, float edgeFactor, bool isOutline) {\n    return baseColor;\n}",
        templateShader = Identifier.of(Lambda.MOD_ID, "shaders/core/outline_composite.fsh"),
        prepareFolderError = "Failed to prepare custom outline shader folder.",
        listShadersError = "Failed to list custom outline shaders.",
        pipelineFactory = { shaders, shaderName ->
            shaders.ensureFolder()
            LambdaRenderPipelines.createCustomOutlineCompositePipeline(
                shaders.getEncodedName(shaderName),
                shaders.getVersionToken(shaderName),
                shaders.getShaderIdentifier(shaderName)
            )
        },
        pipelineFailureMessage = "Failed to compile custom outline shader '{}'. Falling back to built-in outline."
    )
}
