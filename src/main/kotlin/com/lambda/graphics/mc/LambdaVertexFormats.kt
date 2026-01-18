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

package com.lambda.graphics.mc

import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement

/**
 * Custom vertex formats for Lambda's advanced rendering features.
 * Extends Minecraft's standard formats with additional attributes.
 */
object LambdaVertexFormats {
    /**
     * Custom vertex format element for Normal as 3 floats.
     * MC's NORMAL uses signed bytes which is unsuitable for world-space direction vectors.
     */
    val NORMAL_FLOAT: VertexFormatElement = VertexFormatElement.register(
        30, // ID
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.NORMAL,
        3   // count (x, y, z direction)
    )
    
    /**
     * Custom vertex format element for LineWidth as float.
     * Ensures we get a proper float value in the shader.
     */
    val LINE_WIDTH_FLOAT: VertexFormatElement = VertexFormatElement.register(
        29, // ID
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        1   // count (single float)
    )
    
    /**
     * Custom vertex format element for dash parameters.
     * Contains: dashLength, gapLength, dashOffset, animationSpeed (as vec4 of floats)
     * 
     * Uses ID 31 (high value to avoid conflicts with Minecraft/mods).
     * Uses index 0 and GENERIC usage since this is a custom attribute.
     */
    val DASH_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        31, // ID (use high value to avoid conflicts with MC/mods)
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4   // count (dashLength, gapLength, dashOffset, animationSpeed)
    )
    
    /**
     * Anchor position element for billboard text.
     * Contains the world-space position (camera-relative) that the text is anchored to.
     */
    val ANCHOR_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        20, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3   // count (x, y, z anchor position)
    )
    
    /**
     * Billboard data element for text rendering.
     * Contains: scale, billboard flag (0 = billboard towards camera, non-zero = use rotation)
     */
    val BILLBOARD_DATA_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        21, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2   // count (scale, billboardFlag)
    )
    
    /**
     * Extended line format with dash support.
     * Layout: Position (vec3), Color (vec4), Normal (vec3 FLOAT), LineWidth (float), Dash (vec4)
     * 
     * Total size: 12 + 4 + 12 + 4 + 16 = 48 bytes
     * 
     * - Position: World-space vertex position (3 floats = 12 bytes)
     * - Color: RGBA color (4 bytes)
     * - Normal: Segment direction vector as FLOATS (3 floats = 12 bytes)
     * - LineWidth: Per-vertex line width in world units (1 float = 4 bytes)
     * - Dash: vec4(dashLength, gapLength, dashOffset, animationSpeed) (4 floats = 16 bytes)
     */
    val POSITION_COLOR_NORMAL_LINE_WIDTH_DASH: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Normal", NORMAL_FLOAT)
        .add("LineWidth", LINE_WIDTH_FLOAT)
        .add("Dash", DASH_ELEMENT)
        .build()
    
    /**
     * Billboard text format with anchor position for GPU-based billboard rotation.
     * Layout: Position (vec3), UV0 (vec2), Color (vec4), Anchor (vec3), BillboardData (vec2)
     * 
     * Total size: 12 + 8 + 4 + 12 + 8 = 44 bytes
     * 
     * - Position: Local glyph offset (x, y) with z unused (3 floats = 12 bytes)
     * - UV0: Texture coordinates (2 floats = 8 bytes)
     * - Color: RGBA color with alpha encoding layer type (4 bytes)
     * - Anchor: Camera-relative world position of text anchor (3 floats = 12 bytes)
     * - BillboardData: vec2(scale, billboardFlag) where billboardFlag 0 = auto-billboard (2 floats = 8 bytes)
     */
    val POSITION_TEXTURE_COLOR_ANCHOR: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", ANCHOR_ELEMENT)
        .add("BillboardData", BILLBOARD_DATA_ELEMENT)
        .build()

    /**
     * 2D direction element for screen-space lines.
     * Contains the line direction vector (dx, dy) used to compute perpendicular offset.
     */
    val DIRECTION_2D_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        22, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2   // count (dx, dy)
    )

    /**
     * Screen-space line format with dash support.
     * Layout: Position (vec3), Color (vec4), Direction2D (vec2), LineWidth (float), Dash (vec4)
     *
     * Total size: 12 + 4 + 8 + 4 + 16 = 44 bytes
     *
     * - Position: Screen-space position (x, y, z where z = 0) (3 floats = 12 bytes)
     * - Color: RGBA color (4 bytes)
     * - Direction2D: Line direction for perpendicular offset (2 floats = 8 bytes)
     * - LineWidth: Line width in pixels (1 float = 4 bytes)
     * - Dash: vec4(dashLength, gapLength, dashOffset, animationSpeed) (4 floats = 16 bytes)
     */
    val SCREEN_LINE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Direction", DIRECTION_2D_ELEMENT)
        .add("LineWidth", LINE_WIDTH_FLOAT)
        .add("Dash", DASH_ELEMENT)
        .build()
}

