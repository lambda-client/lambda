package com.lambda.module.modules.render

import com.lambda.event.events.AttackEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withBlendFunc
import com.lambda.graphics.gl.GlStateUtils.withDepth
import com.lambda.graphics.gl.Matrices.buildWorldProjection
import com.lambda.graphics.gl.Matrices.withVertexTransform
import com.lambda.graphics.shader.Shader
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.colorSpeed
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.random
import com.lambda.util.math.transform
import com.lambda.util.player.MovementUtils.moveDelta
import com.lambda.util.player.MovementUtils.movementVector
import com.lambda.util.player.MovementUtils.randomDirection
import com.lambda.util.primitives.extension.partialTicks
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d

import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import kotlin.math.sin

object Particles : Module(
    name = "Particles",
    description = "Spawns fancy particles",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    private val duration by setting("Duration", 5.0, 1.0..500.0, 1.0)
    private val fadeDuration by setting("Fade Ticks", 5.0, 1.0..30.0, 1.0)
    private val spawnAmount by setting("Spawn Amount", 30.0, 3.0..500.0, 1.0)
    private val size by setting("Size", 2.0, 0.1..50.0, 0.1)
    private val alphaSetting by setting("Alpha", 1.5, 0.01..2.0, 0.01)
    private val speedH by setting("Speed H", 1.0, 0.0..10.0, 0.1)
    private val speedV by setting("Speed V", 1.0, 0.0..10.0, 0.1)
    private val inertia by setting("Inertia", 0.0, 0.0..1.0, 0.01)
    private val gravity by setting("Gravity", 0.2, 0.0..1.0, 0.01)

    private val onMove by setting("On Move", true)

    private var particles = mutableListOf<Particle>()
    private val vao = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.PARTICLE)
    private val shader = Shader("renderer/particle", "renderer/particle")

    init {
        listener<TickEvent.Pre> {
            particles.forEach(Particle::tick)
            particles.removeIf(Particle::shouldRemove)
        }

        listener<RenderEvent.World> {
            // Todo: interpolated tickbased upload?
            particles.forEach(Particle::build)

            withBlendFunc(GL_SRC_ALPHA, GL_ONE) {
                shader.use()
                shader["u_CameraPosition"] = mc.gameRenderer.camera.pos

                vao.upload()
                withDepth(vao::render)
                vao.clear()
            }
        }

        listener<AttackEvent.Pre> { event ->
            spawnForEntity(event.entity)
        }

        listener<MovementEvent.Post> {
            if (!onMove || player.moveDelta < 0.05) return@listener
            spawnForEntity(player)
        }
    }

    private fun spawnForEntity(entity: Entity) {
        repeat(spawnAmount.toInt()) {
            val i = (it + 1) / spawnAmount

            val pos = entity.pos
            val height = entity.boundingBox.lengthY
            val spawnHeight = height * transform(i, 0.0, 1.0, 0.2, 0.8)
            val particlePos = pos.add(0.0, spawnHeight, 0.0)

            particles.add(Particle(particlePos))
        }
    }

    private class Particle(posIn: Vec3d) {
        var initTick = 0
        var maxAge = 0
        var fadeTicks = fadeDuration

        var shouldRemove = false

        var prevPos = posIn
        var pos = posIn

        var motion: Vec3d = Vec3d.ZERO

        init {
            runSafe {
                initTick = player.age
                maxAge = (duration + random(0.0, 20.0)).toInt()


                motion = movementVector(randomDirection(), sin(random(0.0, Math.PI)))
                    .multiply(random(0.0, 1.0), random(0.0, 1.0), random(0.0, 1.0))
                    .multiply(
                        speedH * random(0.9, 1.1),
                        speedV * random(0.9, 1.1),
                        speedH * random(0.9, 1.1)
                    ).multiply(0.1)
            }
        }

        fun tick() = runSafe {
            prevPos = pos
            pos = pos.add(motion)

            motion = motion
                .subtract(0.0, gravity * 0.01, 0.0)
                .multiply(0.9 + inertia * 0.1)

            shouldRemove = player.age - initTick > 5.0 + maxAge + fadeTicks * 2
        }

        fun build() = runSafe {
            val age = player.age - initTick + mc.partialTicks
            val colorTicks = age * 0.1 / colorSpeed

            val alpha = when {
                age < fadeTicks -> age / fadeTicks
                age in fadeTicks..fadeTicks + maxAge -> 1.0
                else -> {
                    val min = fadeTicks + maxAge
                    val max = fadeTicks * 2 + maxAge
                    transform(age, min, max, 1.0, 0.0)
                }
            } * alphaSetting

            val (c1, c2) = GuiSettings.primaryColor to GuiSettings.secondaryColor
            val color = lerp(c1, c2, sin(colorTicks) * 0.5 + 0.5).multAlpha(alpha)

            val position = lerp(prevPos, pos, mc.partialTicks)

            withVertexTransform(buildWorldProjection(position, size)) {
                vao.use {
                    grow(4) // DO NOT FUCKING FORGOTEOIJTOWKET TO GROW (cost me an hour)
                    putQuad(
                        vec3m(-1.0, -1.0, 0.0).vec2(0.0, 0.0).color(color).end(),
                        vec3m(-1.0, 1.0, 0.0).vec2(0.0, 1.0).color(color).end(),
                        vec3m(1.0, 1.0, 0.0).vec2(1.0, 1.0).color(color).end(),
                        vec3m(1.0, -1.0, 0.0).vec2(1.0, 0.0).color(color).end()
                    )
                }
            }
        }
    }
}