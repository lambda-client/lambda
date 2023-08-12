package com.lambda.client.activity.types

import com.lambda.client.util.color.ColorHolder

interface BuildActivity {
    var context: Context
    var availability: Availability
    var type: Type
    var exposedSides: Int
    var distance: Double

    enum class Context(val color: ColorHolder) {
        IN_PROGRESS(ColorHolder(240, 222, 60)),
        RESTOCK(ColorHolder(3, 252, 169)),
        PICKUP(ColorHolder(252, 3, 207)),
        NONE(ColorHolder(0, 0, 0, 0)),
        PENDING(ColorHolder(11, 11, 175))
    }

    enum class Availability(val color: ColorHolder) {
        VALID(ColorHolder(0, 255, 0, 0)),
        WRONG_ITEM_SELECTED(ColorHolder(3, 252, 169)),
        BLOCKED_BY_PLAYER(ColorHolder(252, 3, 207)),
        NOT_IN_RANGE(ColorHolder(252, 3, 207)),
        NOT_REPLACEABLE(ColorHolder(46, 0, 0, 30)),
        NOT_EXPOSED(ColorHolder(46, 0, 0, 30)),
        NEEDS_SUPPORT(ColorHolder(46, 0, 0, 30)),
        NOT_VISIBLE(ColorHolder(46, 0, 0, 30)),
        NEEDS_LIQUID_HANDLING(ColorHolder(50, 12, 112)),
        NONE(ColorHolder(11, 11, 11))
    }

    enum class Type(val color: ColorHolder) {
        LIQUID_FILL(ColorHolder(114, 27, 255)),
        BREAK_BLOCK(ColorHolder(222, 0, 0)),
        IS_SUPPORT(ColorHolder(0, 166, 0)),
        PLACE_BLOCK(ColorHolder(35, 188, 254)),
    }
}