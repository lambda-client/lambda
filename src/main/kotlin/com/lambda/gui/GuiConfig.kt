/*
 * Copyright 2026 Lambda
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

package com.lambda.gui

import com.lambda.config.Config
import com.lambda.config.Tab
import com.lambda.config.categories.GuiCategory
import com.lambda.config.entries.onValueChange
import com.lambda.core.Loadable
import com.lambda.util.KeyCode
import com.lambda.util.WindowUtils.setLambdaWindowIcon
import net.minecraft.SharedConstants
import net.minecraft.client.util.Icons
import java.awt.Color

@Suppress("unused")
object GuiConfig : Loadable, Config("GUI", GuiCategory) {
    private const val GENERAL_TAB = "General"
    private const val SNAPPING_TAB = "Snapping"

    @Tab(GENERAL_TAB) val keybind by setting("Keybind", KeyCode.Y, "Opens and closes the click GUI", screenCheck = false)
        .onPressUnsafe { LambdaGui.toggle() }
    @Tab(GENERAL_TAB) val setLambdaWindowIcon by setting("Set Lambda Window Icon", true)
        .onValueChange { _, to ->
            if (to) setLambdaWindowIcon()
            else {
                val icon = if (SharedConstants.getGameVersion().stable()) Icons.RELEASE else Icons.SNAPSHOT
                mc.window.setIcon(mc.defaultResourcePack, icon)
            }
        }
    @JvmStatic
    @Tab(GENERAL_TAB) val setLambdaWindowTitle by setting("Set Lambda Window Title", true)
        .onValueChange { _, _ -> mc.updateWindowTitle() }
    @Tab(GENERAL_TAB) val lambdaTitleAppendixName by setting("Append Username", true) { setLambdaWindowTitle }
        .onValueChange { _, _ -> mc.updateWindowTitle() }
    @Tab(GENERAL_TAB) val keepSettingsOpen by setting("Keep Settings Open", false, "Leaves a module's settings open when the click GUI closes, so they are still there when it opens again")
    @Tab(GENERAL_TAB) private val resetWindowLayout by setting("Reset Window Layout", ::resetWindows, "Puts every category window of the click GUI back in its place in the row, unfolded")

    @Tab(SNAPPING_TAB) val snapEnabled by setting("Enable Snapping", true, "Master toggle for snapping HUD elements and click GUI windows into line when they are dragged")
    @Tab(SNAPPING_TAB) val snapKey by setting("Snap Key", KeyCode.LeftShift, "What is dragged only snaps while this is held. Unbind it to snap all the time.") { snapEnabled }
    @Tab(SNAPPING_TAB) val showSnapLines by setting("Show Snap Lines", false, "Draw the grid, and the line an element snaps to, while it is dragged") { snapEnabled }
    @Tab(SNAPPING_TAB) val gridSize by setting("Grid Size", 25f, 2f..128f, 1f, "Grid step") { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToEdges by setting("Snap To Element Edges", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToCenters by setting("Snap To Element Centers", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToScreenCenter by setting("Snap To Screen Center", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToGrid by setting("Snap To Grid", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapDistanceElement by setting("Snap Distance (Elements)", 20f, 1f..48f, 1f) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapDistanceScreen by setting("Snap Distance (Screen Center)", 14f, 1f..48f, 1f) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapDistanceGrid by setting("Snap Distance (Grid)", 12f, 1f..48f, 1f) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapLineColor by setting("Snap Line Color", Color(255, 160, 0, 220)) { snapEnabled && showSnapLines }

    internal data class WindowPlacement(val x: Float, val y: Float, val collapsed: Boolean) {
        val inRow get() = x.isNaN() || y.isNaN()

        companion object {
            fun inRow(collapsed: Boolean) = WindowPlacement(Float.NaN, Float.NaN, collapsed)
        }
    }

    private var windowPlacements by property("")
    private var decodedFrom: String? = null
    private var decoded = emptyMap<String, WindowPlacement>()

    internal var windows: Map<String, WindowPlacement>
        get() {
            val saved = windowPlacements
            if (saved != decodedFrom) {
                decodedFrom = saved
                decoded = saved.split(';').mapNotNull(::decodeWindow).toMap()
            }
            return decoded
        }
        set(value) {
            windowPlacements = value.entries.joinToString(";") { (tag, window) ->
                "$tag:${window.x},${window.y},${window.collapsed}"
            }
        }

    internal fun resetWindows() {
        windows = emptyMap()
    }

    private fun decodeWindow(entry: String): Pair<String, WindowPlacement>? {
        val (tag, fields) = entry.split(':').takeIf { it.size == 2 } ?: return null
        val (x, y, collapsed) = fields.split(',').takeIf { it.size == 3 } ?: return null
        return tag to WindowPlacement(x.toFloatOrNull() ?: return null, y.toFloatOrNull() ?: return null, collapsed.toBoolean())
    }
}
