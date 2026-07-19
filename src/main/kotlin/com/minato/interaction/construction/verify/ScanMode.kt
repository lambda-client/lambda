
package com.minato.interaction.construction.verify

enum class ScanMode(val priority: Int) {
    GreaterBlockHalf(1),
    LesserBlockHalf(1),
    Full(0)
}
