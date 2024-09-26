package com.lambda.module.hud

import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d

object TaskFlow : HudModule(
    name = "TaskFlowHud",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    override val width = 50.0
    override val height = 50.0

    init {
        onRender {
            font.build("TaskFlow", Vec2d.ZERO)
        }
    }
}