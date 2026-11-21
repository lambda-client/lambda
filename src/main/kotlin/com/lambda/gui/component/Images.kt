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

package com.lambda.gui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.lambda.gui.render.ItemAtlas
import com.lambda.gui.render.ItemAtlas.drawItem
import com.lambda.util.stream
import net.minecraft.item.ItemStack
import org.jetbrains.skia.Image
import kotlin.math.roundToInt

@Composable
fun rememberTexture(path: String): ImageBitmap =
    remember(path) {
        path.stream
            .use { Image.makeFromEncoded(it.readAllBytes()) }
            .toComposeImageBitmap()
    }

@Composable
fun ItemIcon(stack: ItemStack, size: Dp, modifier: Modifier = Modifier) {
    val slot = remember { ItemAtlas.Slot() }
    val density = LocalDensity.current
    val pixels =
        with(density) {
            ItemAtlas.ITEM * (size.toPx() / ItemAtlas.ITEM).roundToInt().coerceAtLeast(1)
        }
    SideEffect {
        slot.stack = stack
        slot.size = pixels
    }
    DisposableEffect(slot) {
        ItemAtlas.add(slot)
        onDispose { ItemAtlas.remove(slot) }
    }
    Spacer(modifier.size(with(density) { pixels.toDp() }).drawBehind { drawItem(slot) })
}
