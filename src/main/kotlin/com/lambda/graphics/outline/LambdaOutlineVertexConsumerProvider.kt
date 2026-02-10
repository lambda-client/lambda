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

package com.lambda.graphics.outline

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.BufferAllocator

/**
 * Custom VertexConsumerProvider that collects entity rendering to outline RenderLayers.
 * 
 * Uses MC's built-in outline mechanism via [RenderLayer.getAffectedOutline] which
 * automatically handles textures and alpha testing correctly.
 */
class LambdaOutlineVertexConsumerProvider : VertexConsumerProvider {
    
    private val immediate = VertexConsumerProvider.immediate(BufferAllocator(1536))
    
    override fun getBuffer(layer: RenderLayer): VertexConsumer {
        // If the layer is already an outline layer, wrap it
        if (layer.isOutline) {
            return OutlineVertexConsumer(immediate.getBuffer(layer))
        }
        
        // Try to get the outline variant of this layer
        val outlineLayer = layer.affectedOutline
        if (outlineLayer.isPresent) {
            return OutlineVertexConsumer(immediate.getBuffer(outlineLayer.get()))
        }
        
        // No outline variant - return a no-op consumer
        return NoopVertexConsumer
    }
    
    /**
     * Draw all collected outline geometry.
     */
    fun draw() {
        immediate.draw()
    }
    
    /**
     * Wrapper that passes through vertex data to the actual consumer.
     */
    private class OutlineVertexConsumer(
        private val consumer: VertexConsumer
    ) : VertexConsumer {
        
        override fun vertex(x: Float, y: Float, z: Float): VertexConsumer {
            consumer.vertex(x, y, z)
            return this
        }
        
        override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
            consumer.color(red, green, blue, alpha)
            return this
        }
        
        override fun color(argb: Int): VertexConsumer {
            consumer.color(argb)
            return this
        }
        
        override fun texture(u: Float, v: Float): VertexConsumer {
            consumer.texture(u, v)
            return this
        }
        
        override fun overlay(u: Int, v: Int): VertexConsumer {
            // Skip overlay for outlines
            return this
        }
        
        override fun light(u: Int, v: Int): VertexConsumer {
            // Skip lighting for outlines
            return this
        }
        
        override fun normal(x: Float, y: Float, z: Float): VertexConsumer {
            // Skip normals for outlines
            return this
        }
        
        override fun lineWidth(width: Float): VertexConsumer {
            return this
        }
    }
    
    /**
     * No-op vertex consumer for layers without outline variants.
     */
    private object NoopVertexConsumer : VertexConsumer {
        override fun vertex(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = this
        override fun color(argb: Int): VertexConsumer = this
        override fun texture(u: Float, v: Float): VertexConsumer = this
        override fun overlay(u: Int, v: Int): VertexConsumer = this
        override fun light(u: Int, v: Int): VertexConsumer = this
        override fun normal(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun lineWidth(width: Float): VertexConsumer = this
    }
}
