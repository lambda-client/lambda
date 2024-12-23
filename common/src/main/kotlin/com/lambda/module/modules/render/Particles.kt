/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withBlendFunc
import com.lambda.graphics.gl.GlStateUtils.withDepth
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.buildWorldProjection
import com.lambda.graphics.gl.Matrices.withVertexTransform
import com.lambda.graphics.shader.Shader
import com.lambda.interaction.rotation.Rotation
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.colorSpeed
import com.lambda.module.tag.ModuleTag
import com.lambda.util.extension.partialTicks
import com.lambda.util.math.MathUtils.random
import com.lambda.util.math.VecUtils
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.math.VecUtils.times
import com.lambda.util.math.lerp
import com.lambda.util.math.multAlpha
import com.lambda.util.math.transform
import com.lambda.util.player.MovementUtils.moveDelta
import com.lambda.util.world.raycast.RayCastMask
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
    // ToDo: resort, cleanup settings
    private val duration by setting("Duration", 5.0, 1.0..500.0, 1.0)
    private val fadeDuration by setting("Fade Ticks", 5.0, 1.0..30.0, 1.0)
    private val spawnAmount by setting("Spawn Amount", 20, 3..500, 1)
    private val sizeSetting by setting("Size", 2.0, 0.1..50.0, 0.1)
    private val alphaSetting by setting("Alpha", 1.5, 0.01..2.0, 0.01)
    private val speedH by setting("Speed H", 1.0, 0.0..10.0, 0.1)
    private val speedV by setting("Speed V", 1.0, 0.0..10.0, 0.1)
    private val inertia by setting("Inertia", 0.0, 0.0..1.0, 0.01)
    private val gravity by setting("Gravity", 0.2, 0.0..1.0, 0.01)
    private val onMove by setting("On Move", false)

    private val environment by setting("Environment", true)
    private val environmentSpawnAmount by setting("E Spawn Amount", 10, 3..100, 1) { environment }
    private val environmentSize by setting("E Size", 2.0, 0.1..50.0, 0.1) { environment }
    private val environmentRange by setting("E Spread", 5.0, 1.0..20.0, 0.1) { environment }
    private val environmentSpeedH by setting("E Speed H", 0.0, 0.0..10.0, 0.1) { environment }
    private val environmentSpeedV by setting("E Speed V", 0.1, 0.0..10.0, 0.1) { environment }

    private var particles = mutableListOf<Particle>()
    private val pipeline = VertexPipeline(VertexMode.TRIANGLES, VertexAttrib.Group.PARTICLE)
    private val shader = Shader("renderer/particle", "renderer/particle")

    init {
        listen<TickEvent.Pre> {
            if (environment) spawnForEnvironment()
            particles.removeIf(Particle::update)
        }

        listen<RenderEvent.World> {
            // Todo: interpolated tickbased upload?
            particles.forEach(Particle::build)

            withBlendFunc(GL_SRC_ALPHA, GL_ONE) {
                shader.use()
                shader["u_CameraPosition"] = mc.gameRenderer.camera.pos

                pipeline.upload()
                withDepth(false, pipeline::render)
                pipeline.clear()
            }
        }

        listen<PlayerEvent.Attack.Entity> { event ->
            spawnForEntity(event.entity)
        }

        listen<MovementEvent.Player.Post> {
            if (!onMove || player.moveDelta < 0.05) return@listen
            spawnForEntity(player)
        }
    }

    private fun spawnForEntity(entity: Entity) {
        repeat(spawnAmount) {
            val i = (it + 1) / spawnAmount.toDouble()

            val pos = entity.pos
            val height = entity.boundingBox.lengthY
            val spawnHeight = height * transform(i, 0.0, 1.0, 0.2, 0.8)
            val particlePos = pos.add(0.0, spawnHeight, 0.0)
            val particleMotion = Rotation(
                random(-180.0, 180.0),
                random(-90.0, 90.0)
            ).vector * Vec3d(speedH, speedV, speedH) * 0.1

            particles += Particle(particlePos, particleMotion, false)
        }
    }

    private fun SafeContext.spawnForEnvironment() {
        if (mc.paused) return
        repeat(environmentSpawnAmount) {
            var particlePos = player.pos + Rotation(random(-180.0, 180.0), 0.0).vector * random(0.0, environmentRange)

            Rotation.DOWN.rayCast(6.0, particlePos + VecUtils.UP * 2.0, true, RayCastMask.BLOCK)?.pos?.let {
                particlePos = it + VecUtils.UP * 0.03
            } ?: return@repeat

            val particleMotion = Rotation(
                random(-180.0, 180.0),
                random(-90.0, 90.0)
            ).vector * Vec3d(environmentSpeedH, environmentSpeedV, environmentSpeedH) * 0.1

            particles += Particle(particlePos, particleMotion, true)
        }
    }

    private class Particle(
        initialPosition: Vec3d,
        initialMotion: Vec3d,
        val lay: Boolean
    ) {
        private val fadeTicks = fadeDuration

        private var age = 0
        private val maxAge = (duration + random(0.0, 20.0)).toInt()

        private var prevPos = initialPosition
        private var position = initialPosition
        private var motion = initialMotion

        private val projRotation = if (lay) Matrices.ProjRotationMode.UP else Matrices.ProjRotationMode.TO_CAMERA

        fun update(): Boolean {
            if (mc.paused) return false
            age++

            prevPos = position

            if (!lay) motion += VecUtils.DOWN * gravity * 0.01
            motion *= 0.9 + inertia * 0.1

            position += motion

            return age > maxAge + fadeTicks * 2 + 5
        }

        fun build() {
            val smoothAge = age + mc.partialTicks
            val colorTicks = smoothAge * 0.1 / colorSpeed

            val alpha = when {
                smoothAge < fadeTicks -> smoothAge / fadeTicks
                smoothAge in fadeTicks..fadeTicks + maxAge -> 1.0
                else -> {
                    val min = fadeTicks + maxAge
                    val max = fadeTicks * 2 + maxAge
                    transform(smoothAge, min, max, 1.0, 0.0)
                }
            }

            val (c1, c2) = GuiSettings.primaryColor to GuiSettings.secondaryColor
            val color = lerp(sin(colorTicks) * 0.5 + 0.5, c1, c2).multAlpha(alpha * alphaSetting)

            val position = lerp(mc.partialTicks, prevPos, position)
            val size = if (lay) environmentSize else sizeSetting * lerp(alpha, 0.5, 1.0)

            withVertexTransform(buildWorldProjection(position, size, projRotation)) {
                pipeline.use {
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
