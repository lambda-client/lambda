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

object LambdaVertexFormats {
    val NormalFloat: VertexFormatElement = VertexFormatElement.register(
        30,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.NORMAL,
        3
    )
    
    val LineWidthFloat: VertexFormatElement = VertexFormatElement.register(
        29,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        1
    )
    
    val DashElement: VertexFormatElement = VertexFormatElement.register(
        31,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4
    )
    
    val AnchorElement: VertexFormatElement = VertexFormatElement.register(
        20,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3
    )
    
    val BillboardDataElement: VertexFormatElement = VertexFormatElement.register(
        21,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2
    )
    
    val PositionColorNormalLineWidthDash: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Normal", NormalFloat)
        .add("LineWidth", LineWidthFloat)
        .add("Dash", DashElement)
        .build()
    
    val PositionTextureColorAnchor: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", AnchorElement)
        .add("BillboardData", BillboardDataElement)
        .build()

    val Direction2dElement: VertexFormatElement = VertexFormatElement.register(
        22,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2
    )

    val LayerElement: VertexFormatElement = VertexFormatElement.register(
        24,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        1
    )

    val ScreenFaceFormat: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Layer", LayerElement)
        .build()

    val ScreenLineFormat: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Direction", Direction2dElement)
        .add("LineWidth", LineWidthFloat)
        .add("Dash", DashElement)
        .add("Layer", LayerElement)
        .build()

    val SdfStyleElement: VertexFormatElement = VertexFormatElement.register(
        23,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4
    )

    val PositionTextureColorAnchorSdf: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", AnchorElement)
        .add("BillboardData", BillboardDataElement)
        .add("SDFStyle", SdfStyleElement)
        .build()

    val ScreenTextSdfFormat: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("SDFStyle", SdfStyleElement)
        .add("Layer", LayerElement)
        .build()

    val OverlayUvElement: VertexFormatElement = VertexFormatElement.register(
        25,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4
    )

    val ScreenImageFormat: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("OverlayUV", OverlayUvElement)
        .add("Layer", LayerElement)
        .build()

    val WorldImageFormat: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", AnchorElement)
        .add("BillboardData", BillboardDataElement)
        .add("OverlayUV", OverlayUvElement)
        .build()

    val EdgeDataElement: VertexFormatElement = VertexFormatElement.register(
        26,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2
    )

    val LightDirElement: VertexFormatElement = VertexFormatElement.register(
        27,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3
    )

    val Light1DirElement: VertexFormatElement = VertexFormatElement.register(
        28,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3
    )

    val WorldModelFormat: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("OverlayUV", OverlayUvElement)
        .add("Light", VertexFormatElement.UV2)
        .add("LightDir", LightDirElement)
        .add("Light1Dir", Light1DirElement)
        .add("Normal", NormalFloat)
        .add("EdgeData", EdgeDataElement)
        .build()

    val PositionH: VertexFormatElement = VertexFormatElement.register(
        19,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.POSITION,
        4
    )

    val OutlineIdFormat: VertexFormat = VertexFormat.builder()
        .add("Position", PositionH)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .build()
}

