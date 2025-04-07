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

package com.lambda.module

import com.lambda.event.events.GuiEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.GlowRect.Companion.glow
import com.lambda.gui.component.core.OutlineRect.Companion.outline
import com.lambda.gui.component.core.TextField.Companion.textField
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.layout.Layout.Companion.layout
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import kotlin.math.max
import kotlin.math.min

abstract class HudModule(
    name: String,
    description: String = "",
    defaultTags: Set<ModuleTag> = setOf(),
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: KeyCode = KeyCode.UNBOUND,
    background: Boolean = true
) : Module(name, description, defaultTags, alwaysListening, enabledByDefault, defaultKeybind) {

    protected val animation = AnimationTicker()

    private val base by lazy {
        Layout(null).apply {
            if (!background) return@apply

            rect {
                onUpdate {
                    position = this@apply.position
                    size = this@apply.size

                    setColor(ClickGui.backgroundColor)
                    setRadius(ClickGui.roundRadius)

                    shade = ClickGui.backgroundShade
                }
            }

            glow {
                onUpdate {
                    position = this@apply.position
                    size = this@apply.size

                    setColor(ClickGui.glowColor)
                    setRadius(ClickGui.roundRadius)

                    outerSpread = ClickGui.glowWidth * ClickGui.glow.toInt().toDouble()
                    shade = ClickGui.glowShade
                }
            }

            outline {
                onUpdate {
                    position = this@apply.position
                    size = this@apply.size

                    setColor(ClickGui.outlineColor)
                    setRadius(ClickGui.roundRadius)

                    outlineWidth = ClickGui.outlineWidth * ClickGui.outline.toInt().toDouble()
                    shade = ClickGui.outlineShade
                }
            }
        }
    }

    protected val content by lazy {
        base.layout {
            onUpdate {
                val maxRadius = min(size.x, size.y)
                val w = ClickGui.roundRadius.coerceAtMost(maxRadius)
                val h = if (this@HudModule is Text) 0.0 else ClickGui.roundRadius.coerceAtMost(maxRadius)
                val padding = Vec2d(max(ClickGui.hudPadding, w), max(ClickGui.hudPadding, h))
                base.size = size + padding * 2
                position = base.position + padding
            }
        }
    }

    private val scheduled = mutableListOf<() -> Unit>()

    init {
        listen<TickEvent.Pre> {
            animation.tick()
        }

        listen<RenderEvent.GUI.HUD> {
            while (scheduled.isNotEmpty()) {
                scheduled.removeAt(0).invoke()
            }

            base.onEvent(GuiEvent.Update)
            base.onEvent(GuiEvent.Render)
        }

        onEnable {
            base.onEvent(GuiEvent.Show)
        }

        onDisable {
            base.onEvent(GuiEvent.Hide)
        }
    }

    @UIBuilder
    protected fun build(block: Layout.() -> Unit) {
        scheduled += { content.apply(block) }
    }

    @UIBuilder
    protected fun Layout.customDrawable(render: Layout.() -> Unit) {
        layout {
            onUpdate {
                width = this@customDrawable.width
                height = this@customDrawable.height
            }

            onRender {
                render(this@layout)
            }
        }
    }

    fun getRootLayout() = base

    abstract class Text(
        name: String,
        description: String = "",
        defaultTags: Set<ModuleTag> = setOf(),
        alwaysListening: Boolean = false,
        enabledByDefault: Boolean = false,
        defaultKeybind: KeyCode = KeyCode.UNBOUND
    ) : HudModule(name, description, defaultTags, alwaysListening, enabledByDefault, defaultKeybind) {
        abstract fun getText(): String

        init {
            build {
                val text = textField {
                    onUpdate {
                        text = getText()
                    }
                }

                onUpdate {
                    content.width = text.textWidth
                    content.height = text.textHeight
                }
            }
        }
    }
}
