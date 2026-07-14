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

import com.lambda.gui.components.ClickGuiLayout
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.text
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.cursor.Cursor
import net.minecraft.client.gui.cursor.StandardCursors
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ScrollableTextFieldWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A single entry in a [DisconnectDetailsWidget].
 */
sealed interface DetailSection {
    /**
     * A plain block of wrapped text that is always shown.
     */
    data class TextSection(val text: Text) : DetailSection

    /**
     * A clickable [header] that toggles the visibility of its contents. When
     * expanded it shows an optional [body] block followed by any nested
     * [children], each indented one level further. Starts expanded when
     * [expanded] is true.
     */
    data class CollapsibleSection(
        val header: Text,
        val body: Text? = null,
        val children: List<DetailSection> = emptyList(),
        val expanded: Boolean = true
    ) : DetailSection
}

/**
 * A single vertically-scrollable widget that renders a mix of plain
 * [DetailSection.TextSection]s and toggleable [DetailSection.CollapsibleSection]s.
 * Collapsible headers expand/collapse in place; the whole stack shares one scrollbar.
 */
class DisconnectDetailsWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val sections: List<DetailSection>,
    private val textRenderer: TextRenderer
) : ScrollableTextFieldWidget(x, y, width, height, Text.literal("Disconnect details")) {

    /**
     * Per-node expansion state, keyed by object identity so that structurally
     * equal sections still toggle independently.
     */
    private val expandedState = IdentityHashMap<DetailSection.CollapsibleSection, Boolean>().apply {
        fun visit(list: List<DetailSection>) {
            list.forEach { section ->
                if (section is DetailSection.CollapsibleSection) {
                    put(section, section.expanded)
                    visit(section.children)
                }
            }
        }
        visit(sections)
    }

    private fun contentWidth() = (width - padding).coerceAtLeast(1)

    private fun isExpanded(section: DetailSection.CollapsibleSection) =
        expandedState[section] ?: section.expanded

    override fun getContentsHeight(): Int =
        layout().lastOrNull()?.let { it.top + it.height } ?: 0

    override fun getDeltaYPerScroll(): Double = LINE_HEIGHT.toDouble()

    override fun renderContents(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val hovered = headerNodeAt(mouseX.toDouble(), mouseY.toDouble())

        layout().forEach { placed ->
            val top = textY + placed.top

            if (hovered != null && placed.node === hovered) {
                fillRounded(context, placed.left - HOVER_PADDING, top - HOVER_PADDING, placed.left + placed.textWidth + HOVER_PADDING, top + placed.height, HOVER_RADIUS, COLOR_HOVER_BACKGROUND)
            }

            val color = if (placed.node == null) COLOR_TEXT else COLOR_HEADER
            drawTextLines(context, placed.lines, placed.left, top, color)
        }
    }

    private val PlacedSection.left get() = textX + indent

    private val PlacedSection.textWidth get() = lines.maxOf { textRenderer.getWidth(it) }

    override fun drawBox(context: DrawContext) {
        val right = x + width
        val bottomEdge = y + height
        context.fill(x, y, right, bottomEdge, COLOR_BACKGROUND)
        context.fill(x, y, right, y + 1, COLOR_BORDER)                  // top
        context.fill(x, bottomEdge - 1, right, bottomEdge, COLOR_BORDER) // bottom
        context.fill(x, y, x + 1, bottomEdge, COLOR_BORDER)             // left
        context.fill(right - 1, y, right, bottomEdge, COLOR_BORDER)     // right
    }

    fun hoverCursor(mouseX: Int, mouseY: Int): Cursor? =
        if (visible && headerNodeAt(mouseX.toDouble(), mouseY.toDouble()) != null) {
            StandardCursors.POINTING_HAND
        } else {
            null
        }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (visible && click.button() == 0) {
            val node = headerNodeAt(click.x(), click.y())
            if (node != null) {
                expandedState[node] = !isExpanded(node)
                refreshScroll()
                return true
            }
        }

        return super.mouseClicked(click, doubled)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        builder.put(NarrationPart.TITLE, message)
    }

    private fun drawTextLines(context: DrawContext, lines: List<OrderedText>, x: Int, y: Int, color: Int) {
        var lineY = y
        lines.forEach { line ->
            context.drawTextWithShadow(textRenderer, line, x, lineY, color)
            lineY += LINE_HEIGHT
        }
    }

    @Suppress("SameParameterValue")
    private fun fillRounded(context: DrawContext, left: Int, top: Int, right: Int, bottom: Int, radius: Int, color: Int) {
        val r = radius.coerceIn(0, minOf((right - left) / 2, (bottom - top) / 2))
        if (r <= 0) {
            context.fill(left, top, right, bottom, color)
            return
        }
        //fill the main body area
        context.fill(left, top + r, right, bottom - r, color)
        //fill the top and bottom rectangles one pixel height at a time
        for (dy in 0 until r) {
            val inset = r - sqrt((r * r - (r - dy) * (r - dy)).toDouble()).roundToInt()
            context.fill(left + inset, top + dy, right - inset, top + dy + 1, color)
            context.fill(left + inset, bottom - dy - 1, right - inset, bottom - dy, color)
        }
    }

    private fun headerNodeAt(targetX: Double, targetY: Double): DetailSection.CollapsibleSection? {
        if (targetY < y || targetY >= bottom) return null

        layout().forEach { placedSection ->
            if (placedSection.node != null) {
                val top = textY + placedSection.top - scrollY
                val bottom = top + placedSection.height
                if (targetX >= placedSection.left && targetX < placedSection.left + placedSection.textWidth && targetY >= top && targetY < bottom) return placedSection.node
            }
        }

        return null
    }

    /**
     * Flattens the section tree into positioned rows for the current width and
     * expansion state. Collapsed sections contribute only their header; their
     * body and children are skipped entirely. Cheap enough to recompute per use
     * given how few sections a disconnect screen holds. Returns their position
     * relative to the top of the scrollable content, not the screen.
     */
    private fun layout(): List<PlacedSection> {
        val placements = ArrayList<PlacedSection>()
        var y = 0

        fun emit(node: DetailSection.CollapsibleSection?, indent: Int, lines: List<OrderedText>, gap: Int) {
            placements += PlacedSection(node, indent, y, lines, lines.size * LINE_HEIGHT)
            y += lines.size * LINE_HEIGHT + gap
        }

        fun place(list: List<DetailSection>, depth: Int) {
            val indent = depth * NEST_INDENT
            val width = (contentWidth() - indent).coerceAtLeast(1)

            list.forEach { section ->
                when (section) {
                    is DetailSection.TextSection ->
                        emit(null, indent, textRenderer.wrapLines(section.text, width), SECTION_SPACING)

                    is DetailSection.CollapsibleSection -> {
                        val expanded = isExpanded(section)
                        val arrow = if (expanded) ARROW_EXPANDED else ARROW_COLLAPSED
                        val headerLines = textRenderer.wrapLines(
                            buildText {
                                text(section.header)
                                highlighted(arrow)
                            },
                            width
                        )
                        val hasBody = expanded && section.body != null
                        emit(section, indent, headerLines, if (hasBody) HEADER_BODY_GAP else SECTION_SPACING)

                        if (expanded) {
                            section.body?.let { body ->
                                val childIndent = (depth + 1) * NEST_INDENT
                                val bodyWidth = (contentWidth() - childIndent).coerceAtLeast(1)
                                emit(null, childIndent, textRenderer.wrapLines(body, bodyWidth), SECTION_SPACING)
                            }
                            place(section.children, depth + 1)
                        }
                    }
                }
            }
        }

        place(sections, 0)
        return placements
    }

    /**
     * A single positioned row of wrapped lines. [node] is non-null only for
     * clickable headers; text and body blocks leave it null.
     */
    private data class PlacedSection(
        val node: DetailSection.CollapsibleSection?,
        val indent: Int,
        val top: Int,
        val lines: List<OrderedText>,
        val height: Int
    )

    private companion object {
        const val LINE_HEIGHT = 9
        const val SECTION_SPACING = 6
        const val HEADER_BODY_GAP = 2
        const val NEST_INDENT = 8
        const val ARROW_EXPANDED = " [-]"
        const val ARROW_COLLAPSED = " [+]"
        const val HOVER_PADDING = 2
        const val HOVER_RADIUS = 1

        // Pulled live from the click GUI theme so the panel matches the user's colors.
        val COLOR_TEXT get() = ClickGuiLayout.text.rgb
        val COLOR_BACKGROUND get() = ClickGuiLayout.windowBg.rgb
        val COLOR_BORDER get() = ClickGuiLayout.border.rgb
        val COLOR_HOVER_BACKGROUND get() = ClickGuiLayout.headerHovered.rgb

        // Header text tinted by compositing the highlight color over the text color.
        val COLOR_HEADER get() = ClickGuiLayout.text.rgb//composite(COLOR_TEXT, COLOR_HOVER_BACKGROUND)
    }
}
