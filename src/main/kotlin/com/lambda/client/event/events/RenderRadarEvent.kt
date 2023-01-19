package com.lambda.client.event.events

import com.lambda.client.event.Event
import com.lambda.client.util.graphics.VertexHelper

class RenderRadarEvent(
    val vertexHelper: VertexHelper,
    val radius: Float,
    val scale: Float,
    val chunkLines: Boolean
) : Event