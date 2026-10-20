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

package com.lambda.module.hud

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.lambda.imgui.ImGui
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.render.movementColor
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.SearchExit
import com.lambda.pathing.search.SearchStats
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.RehearsalView
import com.lambda.pathing.session.Telemetry
import java.awt.Color

/**
 * The walk's numbers on one card exactly [panelWidth] wide. Rows hold up to two label/value pairs
 * drawn straight into the window's draw list, so every value sits right-aligned in its half and no
 * text ever goes through a printf-style ImGui call. A label that would run into its value is
 * shortened instead, so the card keeps the width the user chose whatever it shows.
 */
@Suppress("unused")
object PathingHud : HudModule(
    name = "Pathing",
    description = "Live state, tape, search and coarse planner figures of the pathfinder.",
    tag = ModuleTag.HUD,
) {
    private val showTape by setting("Tape", true, "Progress, runway and movement mix of the running tape.")
    private val showSearch by setting("Search", true, "Expansions and the open, parked and spent nodes of the trajectory search.")
    private val showCoarse by setting("Coarse", true, "Frames, graph and frontier anchors of the coarse planner.")
    private val showWorld by setting("World", false, "Captured terrain and pending capture interest.")
    private val showIdle by setting("Show When Idle", false, "Keep the element visible while nothing is being walked.")
    private val panelWidth by setting("Width", 340, 200..2000, 10, "Width of the card; labels that do not fit are shortened.", " px")

    private val width: Float get() = panelWidth.toFloat()

    private var rateExpansions = -1
    private var rateNanos = 0L
    private var expansionsPerSecond = 0.0

    override fun ImGuiBuilder.buildLayout() {
        PlanningDebugChannel.hudWanted = isEnabled
        val status = PathingService.status
        val telemetry = PathingService.telemetry
        if (status is State.Idle && telemetry.published == null && !showIdle) return textDisabled("Pathing idle")
        card {
            header(status, telemetry)
            telemetry.rehearsal?.let { rehearsal(it) }
            if (showTape) telemetry.published?.let { tape(it, status, telemetry) }
            if (showSearch) search(status)
            if (showCoarse) coarse()
            if (showWorld) world()
        }
    }

    private fun ImGuiBuilder.header(status: State, telemetry: Telemetry) {
        val (label, color) = badge(status, telemetry)
        val draw = windowDrawList
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        val h = ImGui.getTextLineHeight()
        val legs = buildList {
            if (telemetry.leg > 0) add("leg ${telemetry.leg}")
            if (telemetry.queuedWaypoints > 0) add("+${telemetry.queuedWaypoints} queued")
            if (telemetry.sessionRestarts > 0) add("${telemetry.sessionRestarts} restarts")
        }.joinToString("  ")
        val legsWidth = ImGui.calcTextSizeX(legs)
        draw.addCircleFilled(x + h * 0.35f, y + h * 0.5f, h * 0.25f, im(color))
        draw.addText(x + h * 0.9f, y, im(color), elide(label, width - h * 0.9f - COLUMN_GAP - legsWidth))
        draw.addText(x + width - legsWidth, y, im(Palette.MUTED), legs)
        ImGui.dummy(width, h)
        goal(status, telemetry)?.let { row(Cell("goal", it)) }
        if (status is State.Failed) message(status.reason, Palette.DANGER)
    }

    private fun ImGuiBuilder.tape(path: PublishedPath, status: State, telemetry: Telemetry) {
        val frames = path.plan.tape.frameCount
        val cursor = (status as? State.Executing)?.frame ?: if (status is State.Complete) frames else 0
        val runway = frames - cursor
        val runwayColor = when {
            !path.partial -> Palette.OK
            runway < STARVING_RUNWAY_FRAMES -> Palette.DANGER
            runway < 2 * STARVING_RUNWAY_FRAMES -> Palette.WARNING
            else -> Palette.OK
        }
        section("TAPE")
        bar(if (frames > 0) cursor.toFloat() / frames else 0f, runwayColor)
        row(Cell("frame", "$cursor/$frames"), Cell("runway", if (path.partial) "$runway" else "$runway to goal", runwayColor))
        row(Cell("adopted", "${telemetry.adopted}/${telemetry.adopted + telemetry.rejectedImprovements}"), Cell("repairs", telemetry.repairs))
        row(Cell("holds", telemetry.holds), Cell("recovered", telemetry.recoveries))
        row(Cell("pushes", telemetry.pushes))
        row(
            Cell("ratio", if (!path.partial && path.excessRatio.isFinite()) "%.2fx".format(path.excessRatio) else "-"),
            Cell("deviation", if (telemetry.maxDeviation > 0.0) "%.0e".format(telemetry.maxDeviation) else "0"),
        )
        movementMix(path.plan.segments.map { it.movement to it.frameCount })
    }

    private fun ImGuiBuilder.rehearsal(view: RehearsalView) {
        section("PLAN")
        val history = view.history
        val best = history.lastOrNull()?.frames
        val spine = history.firstOrNull()?.frames
        row(
            Cell("best", best?.let { "$it  %.2f s".format(it / Vanilla.TICKS_PER_SECOND.toDouble()) } ?: "-", Palette.OK),
            Cell("gain", if (best != null && spine != null) "%.1f%%".format(100.0 * (spine - best) / spine) else "-"),
        )
        row(Cell("spine", spine?.toString() ?: "-"), Cell("improved", (history.size - 1).coerceAtLeast(0)))
        if (!view.finished && spine != null) row(Cell("temperature", view.temperature?.let { "%.2f f".format(it) } ?: "greedy"))
        row(Cell(if (view.finished) "converged" else "refining", if (view.finished) "" else view.progress, if (view.finished) Palette.OK else Palette.TEXT))
        sparkline(history.takeLast(SPARK_ROUNDS).map { it.frames }, best)
    }

    /** One bar per plan the rehearsal kept, taller for a longer tape; the best is drawn in the OK colour. */
    private fun ImGuiBuilder.sparkline(values: List<Int?>, best: Int?) {
        if (values.isEmpty()) return
        val known = values.filterNotNull()
        val low = known.minOrNull() ?: 0
        val high = known.maxOrNull() ?: 0
        val draw = windowDrawList
        val x0 = ImGui.getCursorScreenPosX()
        val y0 = ImGui.getCursorScreenPosY() + 2f
        val slot = width / SPARK_ROUNDS
        values.forEachIndexed { index, value ->
            val left = x0 + index * slot
            val right = left + slot * BAR_FILL
            if (value == null) {
                draw.addRectFilled(left, y0 + SPARK_HEIGHT - 2f, right, y0 + SPARK_HEIGHT, im(Palette.DANGER))
                return@forEachIndexed
            }
            val share = if (high > low) 0.25f + 0.75f * (value - low) / (high - low).toFloat() else 1f
            val color = if (value == best) Palette.OK else Palette.MUTED
            draw.addRectFilled(left, y0 + SPARK_HEIGHT * (1f - share), right, y0 + SPARK_HEIGHT, im(color))
        }
        ImGui.dummy(width, SPARK_HEIGHT + 4f)
    }

    private fun ImGuiBuilder.search(status: State) {
        val live = status is State.Planning || status is State.Executing || status is State.Aligning || status is State.Rehearsing && !status.finished
        val stats = PlanningDebugChannel.stats
        if (live && stats != null) liveSearch(stats)
        else PlanningDebugChannel.lastExhaustion?.let { lastSearch(it) }
    }

    private fun ImGuiBuilder.liveSearch(stats: SearchStats) {
        sampleRate(stats.expansions)
        section("SEARCH")
        row(Cell("expanded", compact(stats.expansions)), Cell("rate", "${compact(expansionsPerSecond.toLong())}/s"))
        row(Cell("open", stats.openAnchors), Cell("parked", stats.parkedAnchors))
        row(Cell("spent", stats.spentAnchors), Cell("blocked", stats.blockedAttempts))
        row(Cell("route", "${stats.deepestRouteIndex}/${stats.routeNodes}"), Cell("solutions", stats.solutionsFound))
        row(Cell("window", "${compact(stats.windowExpansions)}/${compact(stats.windowBudget)}"), Cell("restarts", stats.tapeRestarts))
    }

    private fun ImGuiBuilder.lastSearch(report: SearchStats) {
        section("LAST SEARCH")
        row(Cell("exit", report.exit.name.lowercase().replace('_', ' '), exitColor(report.exit)))
        row(Cell("expanded", compact(report.expansions)), Cell("route", "${report.deepestRouteIndex}/${report.routeNodes}"))
        row(Cell("solutions", report.solutionsFound), Cell("restarts", report.tapeRestarts))
    }

    private fun ImGuiBuilder.coarse() {
        val sample = PlanningDebugChannel.coarse ?: return
        section("COARSE")
        row(Cell("frames", sample.frames), Cell("settle", "${sample.settleMillis} ms"))
        row(Cell("graph", compact(sample.graphNodes)), Cell("anchors", sample.anchors))
    }

    private fun ImGuiBuilder.world() {
        val world = PathingService.world ?: return
        section("WORLD")
        row(Cell("sections", compact(world.sectionsDerived)), Cell("changes", compact(world.blockChanges)))
        row(Cell("revision", compact(world.revision)))
    }

    /** The content on a rounded translucent card, unless the element has its own background colour. */
    private inline fun ImGuiBuilder.card(content: () -> Unit) {
        val draw = windowDrawList
        draw.channelsSplit(2)
        draw.channelsSetCurrent(1)
        content()
        draw.channelsSetCurrent(0)
        if (backgroundColor.value.alpha == 0) {
            val x = ImGui.getWindowPosX()
            val y = ImGui.getWindowPosY()
            draw.addRectFilled(x, y, x + ImGui.getWindowWidth(), y + ImGui.getWindowHeight(), im(Palette.CARD), CARD_ROUNDING)
        }
        draw.channelsMerge()
    }

    private fun ImGuiBuilder.section(title: String) {
        ImGui.dummy(0f, SECTION_GAP)
        val draw = windowDrawList
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        val h = ImGui.getTextLineHeight()
        draw.addText(x, y, im(Palette.HEADING), title)
        draw.addLine(x + ImGui.calcTextSizeX(title) + 6f, y + h * 0.55f, x + width, y + h * 0.55f, im(Palette.HAIRLINE), 1f)
        ImGui.dummy(width, h)
    }

    private class Cell(val label: String, val value: String, val color: Color = Palette.TEXT) {
        constructor(label: String, value: Number, color: Color = Palette.TEXT) : this(label, value.toString(), color)
    }

    /** One line of up to two cells; each label sits left in its half, its value right. */
    private fun ImGuiBuilder.row(vararg cells: Cell) {
        val draw = windowDrawList
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        val half = width / cells.size
        cells.forEachIndexed { index, cell ->
            val left = x + index * half
            val right = left + half - if (index < cells.lastIndex) COLUMN_GAP else 0f
            val valueWidth = ImGui.calcTextSizeX(cell.value)
            val labelRoom = right - left - if (cell.value.isEmpty()) 0f else valueWidth + VALUE_GAP
            draw.addText(left, y, im(Palette.MUTED), elide(cell.label, labelRoom))
            draw.addText(right - valueWidth, y, im(cell.color), cell.value)
        }
        ImGui.dummy(width, ImGui.getTextLineHeight())
    }

    /** The text cut to [room] pixels, ending in an ellipsis when it had to be cut. */
    private fun elide(text: String, room: Float): String {
        if (ImGui.calcTextSizeX(text) <= room) return text
        var end = text.length
        while (end > 0 && ImGui.calcTextSizeX(text.take(end) + ELLIPSIS) > room) end--
        return if (end == 0) "" else text.take(end) + ELLIPSIS
    }

    private fun ImGuiBuilder.bar(fraction: Float, color: Color) {
        val draw = windowDrawList
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY() + 1f
        draw.addRectFilled(x, y, x + width, y + BAR_HEIGHT, im(Palette.TRACK), BAR_HEIGHT / 2)
        val filled = width * fraction.coerceIn(0f, 1f)
        if (filled > 0f) draw.addRectFilled(x, y, x + filled, y + BAR_HEIGHT, im(color), BAR_HEIGHT / 2)
        ImGui.dummy(width, BAR_HEIGHT + 2f)
    }

    /** A wrapped message cut to a few lines; the full text is in the chat and the log. */
    private fun ImGuiBuilder.message(text: String, color: Color) {
        val shown = if (text.length > MAX_MESSAGE_CHARS) text.take(MAX_MESSAGE_CHARS).trimEnd() + ELLIPSIS else text
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + width)
        textColored(shown, color)
        ImGui.popTextWrapPos()
    }

    /** The tape's frames by movement kind as one stacked bar in the world render's colours. */
    private fun ImGuiBuilder.movementMix(parts: List<Pair<MovementId, Int>>) {
        val total = parts.sumOf { it.second }
        if (total <= 0) return
        val frames = LinkedHashMap<MovementId, Int>()
        parts.forEach { (movement, count) -> frames.merge(movement, count, Int::plus) }
        val ordered = frames.entries.sortedByDescending { it.value }

        val draw = windowDrawList
        val x0 = ImGui.getCursorScreenPosX()
        val y0 = ImGui.getCursorScreenPosY() + 2f
        var x = x0
        ordered.forEach { (movement, count) ->
            val w = width * count / total
            draw.addRectFilled(x, y0, x + w, y0 + BAR_HEIGHT, im(PathingService.renderConfig.movementColor(movement)))
            x += w
        }
        ImGui.dummy(width, BAR_HEIGHT + 2f)

        val legend = StringBuilder()
        for ((movement, count) in ordered) {
            val entry = "${movement.key} ${100 * count / total}%"
            val next = if (legend.isEmpty()) entry else "$legend   $entry"
            if (ImGui.calcTextSizeX(next) > width) break
            legend.setLength(0)
            legend.append(next)
        }
        row(Cell(legend.toString(), ""))
    }

    private fun badge(status: State, telemetry: Telemetry): Pair<String, Color> = when (status) {
        State.Idle -> "IDLE" to Palette.MUTED
        is State.Settling -> "SETTLING" to Palette.WARNING
        is State.Planning -> "PLANNING" to Palette.ACCENT
        is State.Rehearsing -> if (status.finished) "PLAN READY" to Palette.OK else if (status.bestFrames == null) "PLANNING SPINE" to Palette.ACCENT else "REFINING PLAN" to Palette.ACCENT
        is State.Aligning -> "ALIGNING %.1f deg".format(status.yawError) to Palette.ACCENT
        is State.Executing -> "WALKING" to Palette.OK
        is State.Complete -> "ARRIVED in ${status.frames} frames" to Palette.OK
        is State.Failed -> "FAILED" to Palette.DANGER
    }

    private fun goal(status: State, telemetry: Telemetry): String? = when (status) {
        is State.Settling -> status.goal
        is State.Planning -> status.goal
        is State.Rehearsing -> status.goal
        else -> telemetry.published?.finalGoal?.toString()
    }

    private fun exitColor(exit: SearchExit): Color = when (exit) {
        SearchExit.SOLVED -> Palette.OK
        SearchExit.BUDGET -> Palette.WARNING
        else -> Palette.DANGER
    }

    /** Expansions per second, smoothed over the stats the search publishes a few times a second. */
    private fun sampleRate(expansions: Int) {
        val now = System.nanoTime()
        if (rateExpansions < 0 || expansions < rateExpansions) {
            rateExpansions = expansions
            rateNanos = now
            expansionsPerSecond = 0.0
            return
        }
        val seconds = (now - rateNanos) / 1e9
        if (seconds < RATE_WINDOW_SECONDS) return
        val instant = (expansions - rateExpansions) / seconds
        expansionsPerSecond = if (expansionsPerSecond == 0.0) instant else expansionsPerSecond + (instant - expansionsPerSecond) * RATE_SMOOTHING
        rateExpansions = expansions
        rateNanos = now
    }

    /** 1234 -> "1.2k", 1_234_567 -> "1.2M": counters that only need their magnitude. */
    private fun compact(value: Number): String {
        val v = value.toLong()
        return when {
            v >= 1_000_000 -> "%.1fM".format(v / 1e6)
            v >= 10_000 -> "%.0fk".format(v / 1e3)
            v >= 1_000 -> "%.1fk".format(v / 1e3)
            else -> v.toString()
        }
    }

    private fun im(color: Color): Int = ImColor.rgba(color.red, color.green, color.blue, color.alpha)

    private object Palette {
        val TEXT = Color(235, 238, 245)
        val MUTED = Color(148, 163, 184)
        val HEADING = Color(203, 213, 225)
        val ACCENT = Color(96, 165, 250)
        val OK = Color(52, 211, 153)
        val WARNING = Color(251, 191, 36)
        val DANGER = Color(248, 113, 113)
        val TRACK = Color(255, 255, 255, 28)
        val HAIRLINE = Color(148, 163, 184, 70)
        val CARD = Color(15, 17, 23, 175)
    }

    private const val BAR_HEIGHT = 4f
    private const val SPARK_HEIGHT = 18f
    private const val SPARK_ROUNDS = 20
    private const val BAR_FILL = 0.7f
    private const val COLUMN_GAP = 14f
    private const val VALUE_GAP = 10f
    private const val SECTION_GAP = 4f
    private const val CARD_ROUNDING = 6f
    private const val MAX_MESSAGE_CHARS = 200
    private const val ELLIPSIS = "..."
    private const val RATE_WINDOW_SECONDS = 0.5
    private const val RATE_SMOOTHING = 0.5

    /** Certified frames left ahead of the body before the walk is about to stand still. */
    private const val STARVING_RUNWAY_FRAMES = 20
}
