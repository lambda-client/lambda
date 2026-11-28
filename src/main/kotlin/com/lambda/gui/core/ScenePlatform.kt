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

package com.lambda.gui.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.Lambda.mc
import net.minecraft.client.gui.cursor.Cursor
import net.minecraft.client.gui.cursor.StandardCursors
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

@OptIn(InternalComposeUiApi::class)
internal class ScenePlatform : PlatformContext by PlatformContext.Empty() {
	private val window =
		object : WindowInfo {
			override val isWindowFocused get() = true
			override var containerSize by mutableStateOf(IntSize.Zero)
			override var containerDpSize by mutableStateOf(DpSize.Zero)
		}
	var cursor: Cursor? = null

	override val windowInfo: WindowInfo get() = window

	fun resize(size: IntSize, density: Float) {
		window.containerSize = size
		window.containerDpSize = DpSize((size.width / density).dp, (size.height / density).dp)
	}

	override fun setPointerIcon(pointerIcon: PointerIcon) {
		cursor =
			when (pointerIcon) {
				PointerIcon.Text -> StandardCursors.IBEAM
				PointerIcon.Hand -> StandardCursors.POINTING_HAND
				PointerIcon.Crosshair -> StandardCursors.CROSSHAIR
				ResizeVerticalIcon -> StandardCursors.RESIZE_NS
				ResizeHorizontalIcon -> StandardCursors.RESIZE_EW
				else -> null
			}
	}
}

object ResizeVerticalIcon : PointerIcon

object ResizeHorizontalIcon : PointerIcon

@OptIn(ExperimentalComposeUiApi::class)
internal object GameClipboard : Clipboard {
	@Suppress("OVERRIDE_DEPRECATION")
	override val nativeClipboard: Any get() = this

	override suspend fun getClipEntry(): ClipEntry? =
		mc.keyboard.clipboard.takeIf { it.isNotEmpty() }?.let {
			ClipEntry(StringSelection(it))
		}

	override suspend fun setClipEntry(clipEntry: ClipEntry?) {
		val transferable = clipEntry?.nativeClipEntry as? Transferable
		mc.keyboard.clipboard =
			transferable
				?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
				?.getTransferData(DataFlavor.stringFlavor) as? String
				?: ""
	}
}
