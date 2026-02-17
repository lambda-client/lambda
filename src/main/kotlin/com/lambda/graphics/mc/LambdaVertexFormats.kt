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
    val NORMAL_FLOAT: VertexFormatElement = VertexFormatElement.register(
        30,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.NORMAL,
        3
    )
    
    val LINE_WIDTH_FLOAT: VertexFormatElement = VertexFormatElement.register(
        29,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        1
    )
    
    val DASH_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        31,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4
    )
    
    val ANCHOR_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        20,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3
    )
    
    val BILLBOARD_DATA_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        21,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2
    )
    
    val POSITION_COLOR_NORMAL_LINE_WIDTH_DASH: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Normal", NORMAL_FLOAT)
        .add("LineWidth", LINE_WIDTH_FLOAT)
        .add("Dash", DASH_ELEMENT)
        .build()
    
    val POSITION_TEXTURE_COLOR_ANCHOR: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", ANCHOR_ELEMENT)
        .add("BillboardData", BILLBOARD_DATA_ELEMENT)
        .build()

    val DIRECTION_2D_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        22,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2
    )

    val LAYER_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        24,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        1
    )

    val SCREEN_FACE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Layer", LAYER_ELEMENT)
        .build()

    val SCREEN_LINE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("Direction", DIRECTION_2D_ELEMENT)
        .add("LineWidth", LINE_WIDTH_FLOAT)
        .add("Dash", DASH_ELEMENT)
        .add("Layer", LAYER_ELEMENT)
        .build()

    val SDF_STYLE_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        23,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4
    )

    val POSITION_TEXTURE_COLOR_ANCHOR_SDF: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", ANCHOR_ELEMENT)
        .add("BillboardData", BILLBOARD_DATA_ELEMENT)
        .add("SDFStyle", SDF_STYLE_ELEMENT)
        .build()

    val SCREEN_TEXT_SDF_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("SDFStyle", SDF_STYLE_ELEMENT)
        .add("Layer", LAYER_ELEMENT)
        .build()

    val OVERLAY_UV_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        25,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        4
    )

    val SCREEN_IMAGE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("OverlayUV", OVERLAY_UV_ELEMENT)
        .add("Layer", LAYER_ELEMENT)
        .build()

    val WORLD_IMAGE_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("Anchor", ANCHOR_ELEMENT)
        .add("BillboardData", BILLBOARD_DATA_ELEMENT)
        .add("OverlayUV", OVERLAY_UV_ELEMENT)
        .build()
    val EDGE_DATA_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        26,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        2
    )

    val LIGHT_DIR_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        27,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3
    )

    val LIGHT1_DIR_ELEMENT: VertexFormatElement = VertexFormatElement.register(
        28,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.GENERIC,
        3
    )

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

    val POSITION_H: VertexFormatElement = VertexFormatElement.register(
        19,
        0,
        VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.POSITION,
        4
    )

    val OUTLINE_ID_FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", POSITION_H)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .build()
}

