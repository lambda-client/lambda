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

package com.lambda.gui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lambda.config.entries.Setting
import com.lambda.gui.clickgui.Reveal
import com.lambda.gui.component.LabeledRow
import com.lambda.gui.component.LocalClickOnRelease
import com.lambda.gui.component.LocalKeyboardReachable
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Radius

/**
 * The row of one setting in a list, whatever its type: the description as its tooltip, the
 * light the search puts on it, and the dimming of one that is disabled. What the row holds is
 * the setting's own, see [Setting.compose]. A right click does nothing to a setting: in this
 * interface it only ever opens and closes things.
 *
 * Every editor is one row tall, so a list of settings reads as a list, and each kind looks
 * unlike the others, so it is plain at a glance what a row is: a tick box, a bar that fills,
 * a chip with an arrow, a key cap, a swatch, a field to type in. Editors that need more room
 * than a row open over the interface instead of pushing the rows below apart.
 *
 * Nothing is drawn around or between them. The row under the pointer is lit, which shows
 * how far it reaches and which value belongs to which name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingRow(setting: Setting<*>) {
    val disabled by observe { setting.disabled() }

    // Lit and scrolled to while it is what the search points at, once the tab has turned to it
    // and the groups around it have unfolded
    val revealed = Reveal.setting === setting
    val lit by animateColorAsState(if (revealed) LambdaTheme.colors.onSurface.copy(alpha = 0.35f) else Color.Transparent, tween(250))
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(revealed) {
        if (revealed) {
            awaitFrames(SETTLE_MILLIS)
            intoView.bringIntoView()
        }
    }

    Tooltip(setting.description, Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .settingCell()
                .bringIntoViewRequester(intoView)
                // Over what the row holds: a slider fills its row and would hide a light behind it
                .drawWithContent {
                    drawContent()
                    drawRect(lit)
                }
                .enabled(!disabled)
        ) {
            // A setting is changed when the button is let go, so a slip of the hand can be taken back.
            // One that is disabled is out of the keyboard's reach as it is out of the pointer's.
            CompositionLocalProvider(LocalClickOnRelease provides true, LocalKeyboardReachable provides !disabled) {
                setting.compose()
            }
        }
    }
}

/**
 * The room a setting takes in a list: the width of the panel, lit while the pointer is on it.
 * For what is listed among settings without being one, too.
 */
internal fun Modifier.settingCell(): Modifier = composed {
    fillMaxWidth()
        // Half above and half below, so the first and the last keep their distance from the edges too
        .padding(vertical = Look.settingSpacing / 2)
        .clip(RoundedCornerShape(Radius.Control))
        .hoverHighlight(LambdaTheme.colors.hover)
}

/** How long a tab takes to turn and a group to unfold, before a setting in them is where it will stay */
private const val SETTLE_MILLIS = 250L

/** Dims the element and keeps the pointer from reaching it while not [enabled] */
private fun Modifier.enabled(enabled: Boolean): Modifier =
    if (enabled) this
    else alpha(0.5f).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/** The shape most settings share: the [name], and at the end of the row whatever edits the value */
@Composable
internal fun Labeled(name: String, modifier: Modifier = Modifier, editor: @Composable RowScope.() -> Unit) =
    LabeledRow(name, modifier.padding(start = 3.dp), editor)

/** Width of the field a line of text is typed into, beside its name */
internal val TextFieldWidth = 90.dp

/** Width of each field of a position; longer numbers scroll inside it */
internal val NumberFieldWidth = 34.dp
