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

package com.lambda.graphics.outline;

import net.minecraft.client.gl.Framebuffer;

/**
 * Mixin interface for WorldRenderer to support custom framebuffer swapping.
 * Used by the outline rendering system to redirect entity rendering to custom
 * framebuffers.
 */
public interface IWorldRenderer {
    /**
     * Push the current entity outline framebuffer onto a stack and replace it with
     * the given framebuffer.
     */
    void lambda$pushEntityOutlineFramebuffer(Framebuffer framebuffer);

    /**
     * Pop the previous entity outline framebuffer from the stack and restore it.
     */
    void lambda$popEntityOutlineFramebuffer();
}
