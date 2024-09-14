package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.newgui.LambdaScreen.Companion.toScreen
import com.lambda.newgui.gui
import com.lambda.newgui.layout
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color

object NewCGui : Module(
    name = "NewCGui",
    description = "ggs",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val clickGuiLayout =
        gui {
            layout {
                rect { Rect(Vec2d.ONE * 10.0, Vec2d.ONE * 200.0) }

                onRender {
                    filled.build(rect, color = Color.WHITE.setAlpha(0.5))
                }

                layout(true) {
                    rect { Rect(Vec2d.ONE * 10.0, Vec2d.ONE * 200.0) }

                    onRender {
                        filled.build(rect, color = Color.BLACK)
                    }

                    layout(true) {
                        rect { Rect(Vec2d.ONE * 10.0, Vec2d.ONE * 200.0) }

                        onRender {
                            filled.build(rect, color = Color.WHITE.setAlpha(0.5))
                        }

                        layout(true) {
                            rect { Rect(Vec2d.ONE * 10.0, Vec2d.ONE * 200.0) }

                            onRender {
                                filled.build(rect, color = Color.BLACK)
                            }
                        }
                    }
                }
            }
        }

    val CLICK_GUI = clickGuiLayout.toScreen("New Click Gui")

    init {
        onEnable {
            CLICK_GUI.show()
        }
    }
}
