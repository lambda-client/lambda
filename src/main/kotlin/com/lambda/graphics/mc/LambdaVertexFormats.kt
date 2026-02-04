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
     * Layer depth element for screen-space ordering.
     * Contains a single float representing the draw order (higher = on top).
     */
    val LAYER_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        24, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        1   // count (single float: layer depth)
    )

    /**
     * Screen-space face format with layer support for draw order preservation.
     * Layout: Position (vec3), Color (vec4), Layer (float)
     *
     * Total size: 12 + 4 + 4 = 20 bytes
     *
     * - Position: Screen-space position (x, y, z=0) (3 floats = 12 bytes)
     * - Color: RGBA color (4 bytes)
     * - Layer: Depth for layering (1 float = 4 bytes)
     */
    val SCREEN_FACE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Layer", LAYER_ELEMENT)
        .build()

    /**
     * Screen-space line format with dash support and layer for draw order.
     * Layout: Position (vec3), Color (vec4), Direction2D (vec2), LineWidth (float), Dash (vec4), Layer (float)
     *
     * Total size: 12 + 4 + 8 + 4 + 16 + 4 = 48 bytes
     *
     * - Position: Screen-space position (x, y, z where z = 0) (3 floats = 12 bytes)
     * - Color: RGBA color (4 bytes)
     * - Direction2D: Line direction for perpendicular offset (2 floats = 8 bytes)
     * - LineWidth: Line width in pixels (1 float = 4 bytes)
     * - Dash: vec4(dashLength, gapLength, dashOffset, animationSpeed) (4 floats = 16 bytes)
     * - Layer: Depth for layering (1 float = 4 bytes)
     */
    val SCREEN_LINE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Direction", DIRECTION_2D_ELEMENT)
        .add("LineWidth", LINE_WIDTH_FLOAT)
        .add("Dash", DASH_ELEMENT)
        .add("Layer", LAYER_ELEMENT)
        .build()

    // ============================================================================
    // SDF Text Style Vertex Attributes (replaces SDFParams uniform buffer)
    // ============================================================================

    /**
     * SDF style parameters as vertex attributes.
     * Contains: OutlineWidth, GlowRadius, ShadowSoftness, SDFThreshold (as vec4 of floats)
     * 
     * This replaces the SDFParams uniform buffer, enabling per-vertex style control
     * and eliminating the need for style-based batching.
     */
    val SDF_STYLE_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        23, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4   // count (outlineWidth, glowRadius, shadowSoftness, sdfThreshold)
    )

    /**
     * Billboard text format with anchor position AND SDF style parameters.
     * Layout: Position (vec3), UV0 (vec2), Color (vec4), Anchor (vec3), BillboardData (vec2), SDFStyle (vec4)
     * 
     * Total size: 12 + 8 + 4 + 12 + 8 + 16 = 60 bytes
     * 
     * - Position: Local glyph offset (x, y) with z unused (3 floats = 12 bytes)
     * - UV0: Texture coordinates (2 floats = 8 bytes)
     * - Color: RGBA color with alpha encoding layer type (4 bytes)
     * - Anchor: Camera-relative world position of text anchor (3 floats = 12 bytes)
     * - BillboardData: vec2(scale, billboardFlag) (2 floats = 8 bytes)
     * - SDFStyle: vec4(outlineWidth, glowRadius, shadowSoftness, threshold) (4 floats = 16 bytes)
     */
    val POSITION_TEXTURE_COLOR_ANCHOR_SDF: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", ANCHOR_ELEMENT)
        .add("BillboardData", BILLBOARD_DATA_ELEMENT)
        .add("SDFStyle", SDF_STYLE_ELEMENT)
        .build()

    /**
     * Screen-space text format with SDF style parameters and layer for draw order.
     * Layout: Position (vec3), UV0 (vec2), Color (vec4), SDFStyle (vec4), Layer (float)
     * 
     * Total size: 12 + 8 + 4 + 16 + 4 = 44 bytes
     * 
     * - Position: Screen-space position (x, y, z=0) (3 floats = 12 bytes)
     * - UV0: Texture coordinates (2 floats = 8 bytes)
     * - Color: RGBA color with alpha encoding layer type (4 bytes)
     * - SDFStyle: vec4(outlineWidth, glowRadius, shadowSoftness, threshold) (4 floats = 16 bytes)
     * - Layer: Depth for layering (1 float = 4 bytes)
     */
    val SCREEN_TEXT_SDF_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("SDFStyle", SDF_STYLE_ELEMENT)
        .add("Layer", LAYER_ELEMENT)
        .build()

    // ============================================================================
    // Image Rendering Vertex Formats
    // ============================================================================

    /**
     * Overlay UV element for image rendering with overlay textures (e.g., enchantment glint).
     * Contains: overlayU, overlayV, hasOverlay, diffuseAmount (as vec4 of floats)
     */
    val OVERLAY_UV_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        25, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4   // count (overlayU, overlayV, hasOverlay, diffuseAmount)
    )

    /**
     * Screen-space image format with overlay support and layer for draw order.
     * Layout: Position (vec3), UV0 (vec2), Color (vec4), OverlayUV (vec3), Layer (float)
     *
     * Total size: 12 + 8 + 4 + 16 + 4 = 44 bytes
     *
     * - Position: Screen-space position (x, y, z=0) (3 floats = 12 bytes)
     * - UV0: Main texture coordinates (2 floats = 8 bytes)
     * - Color: RGBA tint color (4 bytes)
     * - OverlayUV: vec4(overlayU, overlayV, hasOverlay, diffuseAmount) (4 floats = 16 bytes)
     * - Layer: Depth for layering (1 float = 4 bytes)
     */
    val SCREEN_IMAGE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("OverlayUV", OVERLAY_UV_ELEMENT)
        .add("Layer", LAYER_ELEMENT)
        .build()

    /**
     * World-space image format with anchor for billboarding and overlay support.
     * Layout: Position (vec3), UV0 (vec2), Color (vec4), Anchor (vec3), BillboardData (vec2), OverlayUV (vec3)
     *
     * Total size: 12 + 8 + 4 + 12 + 8 + 16 = 60 bytes
     *
     * - Position: Local offset (x, y) with z unused (3 floats = 12 bytes)
     * - UV0: Main texture coordinates (2 floats = 8 bytes)
     * - Color: RGBA tint color (4 bytes)
     * - Anchor: Camera-relative world position (3 floats = 12 bytes)
     * - BillboardData: vec2(scale, billboardFlag) (2 floats = 8 bytes)
     * - OverlayUV: vec4(overlayU, overlayV, hasOverlay, diffuseAmount) (4 floats = 16 bytes)
     */
    val WORLD_IMAGE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", ANCHOR_ELEMENT)
        .add("BillboardData", BILLBOARD_DATA_ELEMENT)
        .add("OverlayUV", OVERLAY_UV_ELEMENT)
        .build()
    /**
     * Edge data element for analytic geometry anti-aliasing.
     * Contains face-relative coordinates (0.0 to 1.0) for edge distance calculation.
     */
    val EDGE_DATA_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        26, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2   // count (faceX, faceY)
    )

    /**
     * Custom light direction element for per-item shading (primary light).
     * Contains the world-space light direction vector.
     */
    val LIGHT_DIR_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        27, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3   // count (x, y, z light direction)
    )

    /**
     * Secondary light direction element for vanilla's two-light shading.
     * Contains the fill light direction vector.
     */
    val LIGHT1_DIR_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        28, // ID (unique, in valid range [0, 32))
        0,  // index
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3   // count (x, y, z light direction)
    )

    /**
     * World-space model format with overlay, lightmap, normals, edge data, and custom lighting.
     * Layout: Position (vec3), Color (vec4), UV0 (vec2), OverlayUV (vec4), Light (vec2), LightDir (vec3), Light1Dir (vec3), Normal (vec3), EdgeData (vec2)
     *
     * Total size: 12 + 4 + 8 + 16 + 4 + 12 + 12 + 12 + 8 = 88 bytes
     *
     * - Position: World-space position (3 floats = 12 bytes)
     * - Color: RGBA tint color (4 bytes)
     * - UV0: Main texture coordinates (2 floats = 8 bytes)
     * - OverlayUV: vec4(overlayU, overlayV, hasOverlay, diffuseAmount) (4 floats = 16 bytes)
     * - Light: Lightmap coordinates (2 shorts = 4 bytes)
     * - LightDir: Primary light direction (3 floats = 12 bytes)
     * - Light1Dir: Fill light direction (3 floats = 12 bytes)
     * - Normal: Normal vector as floats (3 floats = 12 bytes)
     * - EdgeData: Face-relative coordinates for AA (2 floats = 8 bytes)
     */
    val WORLD_MODEL_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("OverlayUV", OVERLAY_UV_ELEMENT)
        .add("Light", VertexFormatElement.UV2)
        .add("LightDir", LIGHT_DIR_ELEMENT)
        .add("Light1Dir", LIGHT1_DIR_ELEMENT)
        .add("Normal", NORMAL_FLOAT)
        .add("EdgeData", EDGE_DATA_ELEMENT)
        .build()
}

