package com.lambda.graphics.renderer.world.core.tracer

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.world.AbstractEspRenderer
import com.lambda.graphics.renderer.world.core.IESPEntry
import com.lambda.graphics.shader.Shader
import net.minecraft.util.math.Vec3d
import java.awt.Color

class StaticTracerRenderer : AbstractEspRenderer<IESPEntry.ITracerEntry>(
    VertexMode.LINES, shader, false
) {
    override fun newEntry(block: IESPEntry.ITracerEntry.() -> Unit) = Builder(this, block)

    class Builder(
        override val owner: StaticTracerRenderer,
        override val updateBlock: IESPEntry.ITracerEntry.() -> Unit
    ) : IESPEntry.ITracerEntry {
        override var position by owner.field(Vec3d.ZERO!!)
        override var color by owner.field(Color.WHITE!!)

        override fun build(ctx: IRenderContext) = ctx.use {
            // Each other vertex is moved into the center of the screen (provided by shader)
            val i = vec3(position.x, position.y, position.z).color(color).end()
            putLine(i, i)
        }
    }

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/tracer_static")
    }
}