package com.lambda.graphics.renderer.world.core.tracer

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.world.AbstractEspRenderer
import com.lambda.graphics.renderer.world.core.IESPEntry
import com.lambda.graphics.shader.Shader
import net.minecraft.util.math.Vec3d
import java.awt.Color

class DynamicTracerRenderer : AbstractEspRenderer<IESPEntry.ITracerEntry>(
    VertexMode.LINES, shader, true
) {
    override fun newEntry(block: IESPEntry.ITracerEntry.() -> Unit) = Builder(this, block)

    class Builder(
        override val owner: DynamicTracerRenderer,
        override val updateBlock: IESPEntry.ITracerEntry.() -> Unit
    ) : IESPEntry.ITracerEntry {
        private var prevPos by owner.field(Vec3d.ZERO)
        override var position by owner.field(Vec3d.ZERO)

        override var color by owner.field(Color.WHITE)

        init {
            update()
            prevPos = position
        }

        override fun update() {
            prevPos = position
            super.update()
        }

        override fun build(ctx: IRenderContext) = ctx.use {
            // Each other vertex is moved into the center of the screen (provided by shader)
            val i = vec3(prevPos.x, prevPos.y, prevPos.z).vec3(position.x, position.y, position.z).color(color).end()
            putLine(i, i)
        }
    }

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/tracer_dynamic")
    }
}