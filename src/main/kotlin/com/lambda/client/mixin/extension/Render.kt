package com.lambda.client.mixin.extension

import com.lambda.mixin.accessor.render.AccessorRenderGlobal
import com.lambda.mixin.accessor.render.AccessorRenderManager
import com.lambda.mixin.accessor.render.AccessorShaderGroup
import com.lambda.mixin.accessor.render.AccessorViewFrustum
import net.minecraft.client.renderer.RenderGlobal
import net.minecraft.client.renderer.ViewFrustum
import net.minecraft.client.renderer.chunk.RenderChunk
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.client.shader.Framebuffer
import net.minecraft.client.shader.Shader
import net.minecraft.client.shader.ShaderGroup
import net.minecraft.util.math.BlockPos

val RenderGlobal.entityOutlineShader: ShaderGroup get() = (this as AccessorRenderGlobal).entityOutlineShader

val RenderManager.renderPosX: Double get() = (this as AccessorRenderManager).renderPosX
val RenderManager.renderPosY: Double get() = (this as AccessorRenderManager).renderPosY
val RenderManager.renderPosZ: Double get() = (this as AccessorRenderManager).renderPosZ
val RenderManager.renderOutlines: Boolean get() = (this as AccessorRenderManager).renderOutlines

val ShaderGroup.listShaders: List<Shader> get() = (this as AccessorShaderGroup).listShaders
val ShaderGroup.listFrameBuffers: List<Framebuffer> get() = (this as AccessorShaderGroup).listFramebuffers

// Unused, but kept for consistency. Java equivalent used in Mixins
fun ViewFrustum.getRenderChunk(pos: BlockPos): RenderChunk = (this as AccessorViewFrustum).invokeGetRenderChunk(pos)
