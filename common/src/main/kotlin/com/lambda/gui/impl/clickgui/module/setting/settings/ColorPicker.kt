/*
 * Copyright 2025 Lambda
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

package com.lambda.gui.impl.clickgui.module.setting.settings

import com.lambda.graphics.renderer.gui.TextureRenderer
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.OutlineRect.Companion.outline
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.setting.SettingLayout
import com.lambda.gui.impl.clickgui.module.setting.settings.NumberSlider.Companion.numberSlider
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.math.MathUtils.toDegree
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.math.a
import com.lambda.util.math.lerp
import com.lambda.util.math.multAlpha
import com.lambda.util.math.setAlpha
import com.lambda.util.math.transform
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.reflect.KMutableProperty0

class ColorPicker(
    owner: Layout,
    name: String,
    field: KMutableProperty0<Color>,
) : SettingLayout<Color>(owner, name, field, true) {

    private var hsb get() = Color.RGBtoHSB(settingDelegate.red, settingDelegate.green, settingDelegate.blue, null).let {
        Vec3d(it[0].toDouble(), it[1].toDouble(), it[2].toDouble())
    }; set(value) {
        settingDelegate = Color(Color.HSBtoRGB(value.x.toFloat(), value.y.toFloat(), value.z.toFloat())).setAlpha(alpha)
    }

    private var hue get() = hsb.x; set(value) {
        val cache = hsb
        if (cache.x == value) return
        hsb = Vec3d(value, cache.y, cache.z)
    }

    private var saturation get() = hsb.y; set(value) {
        val cache = hsb
        if (cache.y == value) return
        hsb = Vec3d(cache.x, value, cache.z)
    }

    private var brightness get() = hsb.z; set(value) {
        val cache = hsb
        if (cache.z == value) return
        hsb = Vec3d(cache.x, cache.y, value)
    }

    private var alpha get() = settingDelegate.a; set(value) {
        settingDelegate = settingDelegate.setAlpha(value)
    }

    init {
        val preview = rect {
            val shrink = 3.0

            onUpdate {
                rect = titleBar.rect
                    .moveFirst(Vec2d.RIGHT * (titleBar.width - titleBar.height))
                    .shrink(shrink + (1.0 - showAnimation)) +
                        Vec2d.RIGHT * lerp(showAnimation, 5.0, -ClickGui.fontOffset + shrink)

                setColor(settingDelegate.multAlpha(showAnimation))
                setRadius(100.0)
            }
        }

        outline {
            onUpdate {
                rect = preview.rect
                setRadius(100.0)
                outlineWidth = 0.5
                setColor(Color.BLACK.setAlpha(0.1 * showAnimation))
            }
        }

        content.layout {
            val getU = { transform(mousePosition.x, positionX, positionX + width, 0.0, 1.0) }
            val getV = { transform(mousePosition.y, positionY, positionY + height, 0.0, 1.0) }

            onUpdate {
                width = content.width * 0.5
                positionX = content.positionX + (content.width - width) * 0.5
                height = width
            }

            onRender {
                hueCircleShader.use()
                TextureRenderer.drawInternal(rect)
            }

            onMouseMove {
                if (pressedButton != Mouse.Button.Left) return@onMouseMove

                val (u, v) = getU() to getV()
                hue = uvToHue(u, v)
                saturation = hypot(u - 0.5, v - 0.5).coerceIn(0.0, 0.5) * 2
            }

            val circle = this
            val knobSize = 2.0

            outline {
                width = knobSize; height = knobSize

                setColor(Color.BLACK)
                outlineWidth = 1.0
                setRadius(100.0)

                onUpdate {
                    val uv = if (circle.pressedButton == Mouse.Button.Left) clampToCircle(Vec2d(getU(), getV()))
                    else hueToUv(hue, saturation)

                    positionX = transform(uv.x, 0.0, 1.0, circle.positionX, circle.positionX + circle.width) - knobSize * 0.5
                    positionY = transform(uv.y, 0.0, 1.0, circle.positionY, circle.positionY + circle.height) - knobSize * 0.5
                }
            }
        }

        listOf(::brightness, ::alpha).map {
            content.numberSlider(it.name.capitalize(), "", 0.0, 1.0, 0.01, it).apply {
                forceRoundDisplayValue = true
            }
        }.onEach {
            it.onUpdate {
                width = this@ColorPicker.content.width
            }
        }

        content.listify()
    }

    companion object {
        private val hueCircleShader = shader("renderer/hue_circle")

        private fun uvToHue(u: Double, v: Double): Double {
            val x = (u - 0.5) * 2.0
            val y = ((1.0 - v) - 0.5) * -2.0

            var hue = atan2(y, x).toDegree() + 90.0
            if (hue < 0) hue += 360.0
            return hue.div(360.0).coerceIn(0.0, 1.0)
        }

        private fun hueToUv(hue: Double, radius: Double): Vec2d {
            val angle = (hue * 360 - 90.0).toRadian()

            val x = cos(angle) * radius
            val y = sin(angle) * radius

            val u = (x * 0.5f) + 0.5f
            val v = (y * -0.5f) + 0.5f

            return Vec2d(u, 1.0 - v)
        }

        private fun clampToCircle(uv: Vec2d): Vec2d {
            val center = Vec2d(0.5f, 0.5f)
            val radius = 0.5f
            val dx = uv.x - center.x
            val dy = uv.y - center.y
            val dist = sqrt(dx * dx + dy * dy)

            return if (dist > radius) {
                val scale = radius / dist
                Vec2d(center.x + dx * scale, center.y + dy * scale)
            } else uv
        }

        /**
         * Creates a [ColorPicker]
         */
        @UIBuilder
        fun Layout.colorPicker(name: String, field: KMutableProperty0<Color>) =
            ColorPicker(this, name, field).apply(children::add)
    }
}