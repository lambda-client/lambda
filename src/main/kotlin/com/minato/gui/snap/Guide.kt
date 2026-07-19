
package com.minato.gui.snap

data class Guide(
    val orientation: Orientation,
    val pos: Float,
    val strength: Int,
    val kind: Kind
) {
    enum class Orientation { Vertical, Horizontal }
    enum class Kind { ElementEdge, ElementCenter, ScreenCenter, Grid }
}
