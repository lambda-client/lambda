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

package com.lambda.module.modules.combat.autodisconnect

import com.lambda.gui.OverlayBackgroundScreen
import com.lambda.util.render.CursorOverrideProvider
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.cursor.Cursor
import net.minecraft.client.gui.cursor.StandardCursors
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import net.minecraft.client.gui.screen.world.SelectWorldScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import kotlin.math.min

class AutoDisconnectScreen(
    private val details: DisconnectDetails
) : Screen(Text.literal("Disconnected: ").append(details.reason)),
    OverlayBackgroundScreen,
    CursorOverrideProvider
{
    //state
    private val parent = TitleScreen()
    private var showDetails = !details.hideDetails

    //text
    private lateinit var detailText: DisconnectDetailsWidget

    //buttons
    private lateinit var toggleDetailsButton: ButtonWidget
    private lateinit var fullScreenButton: ButtonWidget
    private lateinit var reconnectButton: ButtonWidget
    private lateinit var titleScreenButton: ButtonWidget
    private lateinit var worldListButton: ButtonWidget
    private lateinit var quitButton: ButtonWidget

    //image
    private var previewBounds = Bounds.EMPTY
    private var keepTextureOnRemove = false
    private var textureReleased = false

    override fun init() {
        super.init()

        detailText = addDrawableChild(
	        DisconnectDetailsWidget(0, 0, 0, 0, details.sections, textRenderer)
        )

        toggleDetailsButton = addButton(detailToggleText()) { showDetails = !showDetails; updateDetailVisibility() }
        fullScreenButton = addButton(Text.literal("View Full Screen")) { openImagePreview() }
        reconnectButton = addButton(Text.literal("Reconnect")) { reconnect() }
        titleScreenButton = addButton(Text.literal("Title Screen")) { releaseTexture(); client?.setScreen(parent) }
        worldListButton =  addButton(listButtonText()) { openWorldListScreen() }
        quitButton = addButton(Text.literal("Rage Quit")) { releaseTexture(); this.client.scheduleStop() }

        updateLayout()
        updateDetailVisibility()
        updateReconnectButton()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        renderDarkening(context) //aka, render the page background
        context.drawTextWithShadow(textRenderer, title, MARGIN, MARGIN, 0xFFFFFFFF.toInt())
        super.render(context, mouseX, mouseY, deltaTicks)

        if (showDetails) {
            drawScreenshot(context, previewBounds)
        }
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (showDetails && previewBounds.contains(click.x().toInt(), click.y().toInt())) {
            openImagePreview()
            return true
        }

        return super.mouseClicked(click, doubled)
    }

    override fun getCursorOverride(mouseX: Int, mouseY: Int): Cursor? =
        if (::detailText.isInitialized) detailText.hoverCursor(mouseX, mouseY) else null

    override fun close() {
        releaseTexture()
        client?.setScreen(parent)
    }

    override fun removed() {
        if (keepTextureOnRemove) {
            keepTextureOnRemove = false
            return
        }

        releaseTexture()
    }

    override fun onOverlaidByGui() {
        // The click GUI temporarily replaces this screen and restores it on close,
        // so keep the screenshot texture instead of releasing it on removed().
        keepTextureOnRemove = true
    }

    private fun openImagePreview() {
        keepTextureOnRemove = true
        client?.setScreen(ImagePreviewScreen(this, details))
    }

    private fun reconnect() {
        releaseTexture()
        when (val target = AutoDisconnect.lastReconnectTarget) {
            is MultiplayerReconnectTarget -> {
                ConnectScreen.connect(parent, client, target.address, target.info, false, target.cookieStorage)
            }

            is SingleplayerReconnectTarget -> {
                client.createIntegratedServerLoader().start(target.levelName) {
                    client.setScreen(SelectWorldScreen(parent))
                }
            }

            null -> return
        }
    }

    private fun openWorldListScreen() {
        releaseTexture()
        client?.setScreen(
            when (AutoDisconnect.lastReconnectTarget) {
                is SingleplayerReconnectTarget -> SelectWorldScreen(parent)
                else -> MultiplayerScreen(parent)
            }
        )
    }

    private fun addButton(text: Text, onPress: (ButtonWidget) -> Unit): ButtonWidget {
        val buttonWidth = textRenderer.getWidth(text) + BUTTON_EXTRA_WIDTH
        return addDrawableChild(
            ButtonWidget.builder(text, onPress)
                .dimensions(0, 0, buttonWidth.coerceAtLeast(MIN_BUTTON_WIDTH), BUTTON_HEIGHT)
                .build()
        )
    }

    private fun updateLayout() {
        val titleBottom = MARGIN + textRenderer.fontHeight + SECTION_GAP
        val columnGap = SECTION_GAP
        val columnWidth = ((width - MARGIN * 2 - columnGap) / 2).coerceAtLeast(0)
        val leftColumnX = MARGIN
        val rightColumnX = leftColumnX + columnWidth + columnGap
        val bottomButtons = listOf(reconnectButton, titleScreenButton, worldListButton, quitButton)
        val bottomRowsHeight = measureButtonRowsHeight(bottomButtons)
        val bottomButtonsY = (height - MARGIN - bottomRowsHeight)
            .coerceAtLeast(titleBottom + BUTTON_HEIGHT + CONTROL_CONTENT_GAP)
        val bodyTop = titleBottom + BUTTON_HEIGHT + CONTROL_CONTENT_GAP
        val bodyBottom = bottomButtonsY - SECTION_GAP
        val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(0)

        toggleDetailsButton.x = leftColumnX
        toggleDetailsButton.y = titleBottom
        fullScreenButton.x = rightColumnX
        fullScreenButton.y = titleBottom

        layoutButtons(bottomButtonsY, bottomButtons)

        detailText.x = leftColumnX
        detailText.y = bodyTop
        detailText.width = columnWidth
        detailText.height = bodyHeight

        previewBounds = fitImage(
            maxX = rightColumnX,
            maxY = bodyTop,
            maxWidth = columnWidth,
            maxHeight = bodyHeight
        )
    }

    private fun layoutButtons(y: Int, buttons: List<ButtonWidget>) {
        var currentY = y
        val rows = buttonRows(buttons)

        rows.forEach { row ->
            var currentX = MARGIN

            row.buttons.forEach { button ->
                button.x = currentX
                button.y = currentY
                currentX += button.width + BUTTON_GAP
            }

            currentY += BUTTON_HEIGHT + BUTTON_GAP
        }
    }

    private fun measureButtonRowsHeight(buttons: List<ButtonWidget>): Int {
        val rowCount = buttonRows(buttons).size

        return rowCount * BUTTON_HEIGHT + (rowCount - 1).coerceAtLeast(0) * BUTTON_GAP
    }

    private fun buttonRows(buttons: List<ButtonWidget>): List<ButtonRow> {
        if (buttons.isEmpty()) return emptyList()

        val rows = mutableListOf<ButtonRow>()
        val rowButtons = mutableListOf<ButtonWidget>()
        var currentX = MARGIN

        buttons.forEach { button ->
            if (currentX > MARGIN && currentX + button.width > width - MARGIN) {
                rows += ButtonRow(rowButtons.toList(), currentX - MARGIN - BUTTON_GAP)
                rowButtons.clear()
                currentX = MARGIN
            }

            rowButtons += button
            currentX += button.width + BUTTON_GAP
        }

        rows += ButtonRow(rowButtons.toList(), currentX - MARGIN - BUTTON_GAP)
        return rows
    }

    private fun updateDetailVisibility() {
        val detailsVisible = showDetails
        detailText.visible = detailsVisible
        fullScreenButton.active = detailsVisible
        fullScreenButton.visible = detailsVisible
        toggleDetailsButton.message = detailToggleText()
    }

    private fun updateReconnectButton() {
        reconnectButton.active = AutoDisconnect.lastReconnectTarget != null
    }

    private fun detailToggleText() =
        Text.literal(if (showDetails) "Hide Details" else "Show Details")

    private fun listButtonText() =
        Text.literal(
            when (AutoDisconnect.lastReconnectTarget) {
                is SingleplayerReconnectTarget -> "World List"
                else -> "Server List"
            }
        )

    private fun fitImage(maxX: Int, maxY: Int, maxWidth: Int, maxHeight: Int): Bounds {
        if (maxWidth <= 0 || maxHeight <= 0 || details.imageWidth <= 0 || details.imageHeight <= 0) {
            return Bounds.EMPTY
        }

        val scale = min(
	        maxWidth.toDouble() / details.imageWidth.toDouble(),
	        maxHeight.toDouble() / details.imageHeight.toDouble()
        )
        val imageWidth = (details.imageWidth * scale).toInt().coerceAtLeast(1)
        val imageHeight = (details.imageHeight * scale).toInt().coerceAtLeast(1)

        return Bounds(
            x = maxX + (maxWidth - imageWidth) / 2,
            y = maxY,
            width = imageWidth,
            height = imageHeight
        )
    }

    private fun drawScreenshot(context: DrawContext, bounds: Bounds) {
        if (bounds.isEmpty) return

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            details.imageIdentifier,
            bounds.x,
            bounds.y,
            0f,
            0f,
            bounds.width,
            bounds.height,
            details.imageWidth,
            details.imageHeight,
            details.imageWidth,
            details.imageHeight
        )
    }

    private fun releaseTexture() {
        if (textureReleased) return
        client?.textureManager?.destroyTexture(details.imageIdentifier)
        textureReleased = true
    }

    private class ImagePreviewScreen(
	    private val parent: Screen,
	    private val details: DisconnectDetails
    ) : Screen(Text.literal("Screenshot Preview")), CursorOverrideProvider {
        private var viewportBounds = Bounds.EMPTY
        private var fitBounds = Bounds.EMPTY
        private var imageBounds = Bounds.EMPTY
        private var zoom = 1.0
        private var panX = 0.0
        private var panY = 0.0

        override fun init() {
            val text = "Back"
            val buttonWidth = textRenderer.getWidth(text) + BUTTON_EXTRA_WIDTH
            addDrawableChild(
                ButtonWidget.builder(Text.literal(text)) {
                    client?.setScreen(parent)
                }.dimensions(PREVIEW_PADDING, PREVIEW_PADDING, buttonWidth.coerceAtLeast(MIN_BUTTON_WIDTH), BUTTON_HEIGHT).build()
            )

            updateImageBounds()
        }

        override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            renderDarkening(context)
            context.drawCenteredTextWithShadow(
                textRenderer,
                PREVIEW_INSTRUCTIONS,
                width / 2,
                PREVIEW_PADDING + (BUTTON_HEIGHT - textRenderer.fontHeight) / 2,
                0xFFFFFFFF.toInt()
            )
            drawScreenshot(context)
            super.render(context, mouseX, mouseY, deltaTicks)
        }

        override fun close() { client?.setScreen(parent) }

        override fun getCursorOverride(mouseX: Int, mouseY: Int) = if (zoom > MIN_ZOOM && viewportBounds.contains(mouseX, mouseY)) {
            StandardCursors.RESIZE_ALL
        } else {
            null
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
            if (verticalAmount == 0.0 || fitBounds.isEmpty) {
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            }

            val oldBounds = imageBounds
            val oldZoom = zoom
            zoom = (zoom * if (verticalAmount > 0.0) ZOOM_STEP else 1.0 / ZOOM_STEP)
                .coerceIn(MIN_ZOOM, MAX_ZOOM)

            if (zoom == oldZoom) return true

            if (zoom == MIN_ZOOM) {
                panX = 0.0
                panY = 0.0
            } else if (!oldBounds.isEmpty) {
                val cursorRatioX = ((mouseX - oldBounds.x) / oldBounds.width.toDouble()).coerceIn(0.0, 1.0)
                val cursorRatioY = ((mouseY - oldBounds.y) / oldBounds.height.toDouble()).coerceIn(0.0, 1.0)
                val zoomedWidth = fitBounds.width * zoom
                val zoomedHeight = fitBounds.height * zoom
                val centeredX = fitBounds.x + (fitBounds.width - zoomedWidth) / 2.0
                val centeredY = fitBounds.y + (fitBounds.height - zoomedHeight) / 2.0

                panX = mouseX - centeredX - cursorRatioX * zoomedWidth
                panY = mouseY - centeredY - cursorRatioY * zoomedHeight
            }

            updateImageBounds()
            return true
        }

        override fun mouseDragged(click: Click, deltaX: Double, deltaY: Double): Boolean {
            if (zoom <= MIN_ZOOM || click.button() != 0) {
                return super.mouseDragged(click, deltaX, deltaY)
            }

            panX += deltaX
            panY += deltaY
            updateImageBounds()
            return true
        }

        private fun fitImage(): Bounds {
            val availableTop = PREVIEW_PADDING + BUTTON_HEIGHT + PREVIEW_PADDING
            val availableHeight = height - availableTop - PREVIEW_PADDING
            val availableWidth = width - PREVIEW_PADDING * 2

            viewportBounds = Bounds(PREVIEW_PADDING, availableTop, availableWidth, availableHeight)

            if (availableWidth <= 0 || availableHeight <= 0) {
                viewportBounds = Bounds.EMPTY
                return Bounds.EMPTY
            }

            val scale = min(
	            availableWidth.toDouble() / details.imageWidth.toDouble(),
	            availableHeight.toDouble() / details.imageHeight.toDouble()
            )
            val imageWidth = (details.imageWidth * scale).toInt().coerceAtLeast(1)
            val imageHeight = (details.imageHeight * scale).toInt().coerceAtLeast(1)

            return Bounds(
                x = PREVIEW_PADDING + (availableWidth - imageWidth) / 2,
                y = availableTop + (availableHeight - imageHeight) / 2,
                width = imageWidth,
                height = imageHeight
            )
        }

        private fun updateImageBounds() {
            fitBounds = fitImage()

            if (fitBounds.isEmpty) {
                imageBounds = Bounds.EMPTY
                return
            }

            clampPan()

            val zoomedWidth = (fitBounds.width * zoom).toInt().coerceAtLeast(1)
            val zoomedHeight = (fitBounds.height * zoom).toInt().coerceAtLeast(1)

            imageBounds = Bounds(
                x = (fitBounds.x + (fitBounds.width - zoomedWidth) / 2.0 + panX).toInt(),
                y = (fitBounds.y + (fitBounds.height - zoomedHeight) / 2.0 + panY).toInt(),
                width = zoomedWidth,
                height = zoomedHeight
            )
        }

        private fun clampPan() {
            if (zoom == MIN_ZOOM || viewportBounds.isEmpty || fitBounds.isEmpty) {
                panX = 0.0
                panY = 0.0
                return
            }

            val zoomedWidth = fitBounds.width * zoom
            val zoomedHeight = fitBounds.height * zoom
            val centeredX = fitBounds.x + (fitBounds.width - zoomedWidth) / 2.0
            val centeredY = fitBounds.y + (fitBounds.height - zoomedHeight) / 2.0

            panX = clampAxis(
                pan = panX,
                centeredStart = centeredX,
                contentSize = zoomedWidth,
                viewportStart = viewportBounds.x.toDouble(),
                viewportSize = viewportBounds.width.toDouble()
            )
            panY = clampAxis(
                pan = panY,
                centeredStart = centeredY,
                contentSize = zoomedHeight,
                viewportStart = viewportBounds.y.toDouble(),
                viewportSize = viewportBounds.height.toDouble()
            )
        }

        private fun clampAxis(
            pan: Double,
            centeredStart: Double,
            contentSize: Double,
            viewportStart: Double,
            viewportSize: Double
        ): Double {
            if (contentSize <= viewportSize) {
                return viewportStart + (viewportSize - contentSize) / 2.0 - centeredStart
            }

            val minPan = viewportStart + viewportSize - contentSize - centeredStart
            val maxPan = viewportStart - centeredStart
            return pan.coerceIn(minPan, maxPan)
        }

        private fun drawScreenshot(context: DrawContext) {
            if (imageBounds.isEmpty) return

            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                details.imageIdentifier,
                imageBounds.x,
                imageBounds.y,
                0f,
                0f,
                imageBounds.width,
                imageBounds.height,
                details.imageWidth,
                details.imageHeight,
                details.imageWidth,
                details.imageHeight
            )
        }
    }

    private data class Bounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) {
        val isEmpty: Boolean
            get() = width <= 0 || height <= 0

        fun contains(pointX: Int, pointY: Int) =
            pointX in x until x + width && pointY in y until y + height

        companion object {
            val EMPTY = Bounds(0, 0, 0, 0)
        }
    }

    private data class ButtonRow(
        val buttons: List<ButtonWidget>,
        val width: Int
    )

    private companion object {
        const val MARGIN = 16
        const val SECTION_GAP = 8
        const val BUTTON_GAP = 4
        const val CONTROL_CONTENT_GAP = 2
        const val BUTTON_HEIGHT = 16
        const val BUTTON_EXTRA_WIDTH = 8
        const val MIN_BUTTON_WIDTH = 32
        const val PREVIEW_PADDING = 2
        val PREVIEW_INSTRUCTIONS: Text = Text.literal("Scroll to zoom, click and drag to pan")
        const val MIN_ZOOM = 1.0
        const val MAX_ZOOM = 8.0
        const val ZOOM_STEP = 1.2
    }
}
