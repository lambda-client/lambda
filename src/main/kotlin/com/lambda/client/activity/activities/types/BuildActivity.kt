package com.lambda.client.activity.activities.types

import com.lambda.client.util.color.ColorHolder
import net.minecraft.util.math.Vec3d

interface BuildActivity {
    var context: BuildContext
    var action: BuildAction
    var hitVec: Vec3d

    enum class BuildContext(val color: ColorHolder) {
        RESTOCK(ColorHolder()),
        LIQUID(ColorHolder()),
        NONE(ColorHolder()),
        PENDING(ColorHolder())
    }

    enum class BuildAction(val color: ColorHolder) {
        BREAKING(ColorHolder(240, 222, 60)),
        BREAK(ColorHolder(222, 0, 0)),
        PLACE(ColorHolder(35, 188, 254)),
        WRONG_POS_BREAK(ColorHolder(112, 0, 0)),
        WRONG_POS_PLACE(ColorHolder(20, 108, 145)),
        INVALID_BREAK(ColorHolder(46, 0, 0)),
        INVALID_PLACE(ColorHolder(11, 55, 74)),
        UNINIT(ColorHolder(11, 11, 11))
    }
}