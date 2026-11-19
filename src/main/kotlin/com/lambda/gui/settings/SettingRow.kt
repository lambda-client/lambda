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
import androidx.compose.runtime.key
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
import com.lambda.gui.Reveal
import com.lambda.gui.component.LabeledRow
import com.lambda.gui.component.LocalClickOnRelease
import com.lambda.gui.component.LocalKeyboardReachable
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look

private const val SETTLE_MILLIS = 250L
internal val TEXT_FIELD_WIDTH = 90.dp
internal val NUMBER_FIELD_WIDTH = 34.dp
internal val ROW_INSET = 3.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingRow(setting: Setting<*>) {
    val disabled by observe { setting.disabled() }

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
                .drawWithContent {
                    drawContent()
                    drawRect(lit)
                }
                .enabled(!disabled)
        ) {
            CompositionLocalProvider(LocalClickOnRelease provides true, LocalKeyboardReachable provides !disabled) {
                key(setting) { setting.Editor() }
            }
        }
    }
}

internal fun Modifier.settingCell(): Modifier = composed {
    fillMaxWidth()
        .padding(vertical = Look.settingSpacing / 2)
        .clip(RoundedCornerShape(Look.controlRounding))
        .hoverHighlight(LambdaTheme.colors.hover)
}

private fun Modifier.enabled(enabled: Boolean): Modifier =
    if (enabled) this
    else alpha(0.5f).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

@Composable
internal fun Labeled(name: String, modifier: Modifier = Modifier, editor: @Composable RowScope.() -> Unit) =
    LabeledRow(name, modifier.padding(start = ROW_INSET), editor)
