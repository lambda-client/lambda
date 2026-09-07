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
import com.lambda.imgui.ImVec2
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.imgui.flag.ImGuiTableFlags
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.search.SearchStatsView
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry
import java.awt.Color

/**
 * The walk's numbers, in one fixed-width panel. Every widget is sized to [panelWidth]
 * explicitly: the HUD window auto-fits its content, so anything that stretches to the
 * available region would grow the window without bound.
 *
 * Compact by default: a status line, the tape bar with its runway, the search window
 * bar with its runway, one line per section. "Detailed" adds the long tail of counters.
 */
@Suppress("unused")
object PathingHud : HudModule(
    name = "Pathing",
    description = "Live state, tape, search and world metrics of the pathfinder.",
    tag = ModuleTag.HUD,
) {
    private val showTape by setting("Tape", true, "Progress, runway and movement mix of the running tape.")
    private val showSearch by setting("Search", true, "Live counters of the trajectory search while it runs.")
    private val showLastSession by setting("Last Session", true, "The exit report of the most recent search.")
    private val showWorld by setting("World", false, "Coarse graph and snapshot figures.")
    private val detailed by setting("Detailed", false, "Every counter, not only the ones that change what you do next.")
    private val showHistory by setting("History", false, "Runway sparkline under the search bar.") { showSearch }
    private val showIdle by setting("Show When Idle", false, "Keep the element visible while nothing is being walked.")
    private val historyLength by setting("History Length", 120, 30..600, 10, "Samples kept in the sparkline.", " samples") { showHistory }
    private val panelWidth by setting("Width", 230, 160..480, 10, unit = " px")

    // Sparkline buffer, sampled per HUD frame while a search publishes counters.
    private val runwayHistory = FloatArray(600)
    private var historyWrite = 0
    private var historyCount = 0
    private var lastSampledExpansions = -1

    private var widgetId = 0

    override fun ImGuiBuilder.buildLayout() {
        PlanningDebugChannel.hudWanted = isEnabled
        widgetId = 0
        val status = PathingService.status
        val telemetry = PathingService.telemetry
        if (status is State.Idle && telemetry.published == null && !showIdle) {
            textDisabled("Pathing idle")
            return
        }
        header(status, telemetry)
        if (showTape) tape(status, telemetry)
        if (showSearch) search(status)
        if (showLastSession) lastSession()
        if (showWorld) world()
    }

    // ---- Sections ------------------------------------------------------------------------

    private fun ImGuiBuilder.header(status: State, telemetry: Telemetry) {
        val (label, color) = badge(status, telemetry)
        val draw = windowDrawList
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        val h = ImGui.getTextLineHeight()
        draw.addCircleFilled(x + h * 0.4f, y + h * 0.5f, h * 0.26f, im(color))
        ImGui.dummy(h * 0.8f, h)
        sameLine()
        textColored(label, color)

        val right = buildString {
            if (telemetry.leg > 0) append("leg ").append(telemetry.leg)
            if (telemetry.queuedWaypoints > 0) append(" +").append(telemetry.queuedWaypoints)
            if (telemetry.sessionRestarts > 0) append("  r").append(telemetry.sessionRestarts)
        }
        if (right.isNotEmpty()) {
            val w = ImGui.calcTextSize(right).x
            sameLine(panelWidth - w)
            textDisabled(right)
        }
        val goal = goalLabel(status, telemetry)
        if (goal.isNotEmpty()) textDisabled(goal)
        if (status is State.Failed) {
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + panelWidth)
            textColored(status.reason, Palette.DANGER)
            ImGui.popTextWrapPos()
        }
    }

    private fun ImGuiBuilder.tape(status: State, telemetry: Telemetry) {
        val path = telemetry.published ?: return
        val plan = path.plan
        val frames = plan.tape.frameCount
        val cursor = (status as? State.Executing)?.frame ?: if (status is State.Complete) frames else 0
        val runway = frames - cursor

        section("Tape")
        val barColor = when {
            path.partial && runway < STARVING_RUNWAY_FRAMES -> Palette.DANGER
            path.partial -> Palette.WARNING
            else -> Palette.OK
        }
        bar(if (frames > 0) cursor.toFloat() / frames else 0f, barColor)
        line(
            "frame" to "$cursor/$frames",
            "runway" to "$runway${if (path.partial) "*" else ""}",
            "ratio" to if (path.excessRatio.isFinite()) "%.2fx".format(path.excessRatio) else "-",
        )
        line(
            "dev" to "%.0e".format(telemetry.maxDeviation),
            "adopt" to "${telemetry.adopted}/${telemetry.adopted + telemetry.rejectedImprovements}",
            "repairs" to telemetry.repairs.toString(),
        )
        if (detailed) {
            line(
                "holds" to telemetry.holds.toString(),
                "recover" to telemetry.recoveries.toString(),
                "standing" to "${path.standingFrames()}f",
            )
            line(
                "bound" to "%.0f".format(path.route.lowerBoundTicks),
                "route" to "${path.route.nodes.size}n",
                "seq" to path.publicationSequence.toString(),
            )
            if (telemetry.cadence.isNotEmpty() && telemetry.cadence != "no adoptions") {
                textDisabled(telemetry.cadence.substringAfter("(").substringBefore(")"))
            }
        }
        movementBar(path.segments.map { it.movement to it.frames })
    }

    private fun ImGuiBuilder.search(status: State) {
        val stats = PlanningDebugChannel.stats
        val live = status is State.Planning || status is State.Executing || status is State.Aligning
        if (stats == null || !live) return
        sample(stats)

        section("Search")
        val window = if (stats.windowBudget > 0) stats.windowExpansions.toFloat() / stats.windowBudget else 0f
        bar(window.coerceIn(0f, 1f), Palette.ACCENT)
        val runway = if (stats.publishedFrame >= 0 && stats.cursorFrame >= 0) stats.publishedFrame - stats.cursorFrame else null
        val runwayColor = when {
            runway == null -> Palette.MUTED
            runway < STARVING_RUNWAY_FRAMES -> Palette.DANGER
            runway < 2 * STARVING_RUNWAY_FRAMES -> Palette.WARNING
            else -> Palette.OK
        }
        line(
            "exp" to compact(stats.expansions),
            "window" to "${compact(stats.windowExpansions)}/${compact(stats.windowBudget)}",
            "runway" to (runway?.toString() ?: "-"),
            valueColors = mapOf("runway" to runwayColor),
        )
        line(
            "open" to stats.open.toString(),
            "parked" to stats.parked.toString(),
            "spent" to stats.spent.toString(),
        )
        if (detailed) {
            val mergeRate = if (stats.admitted > 0) 100.0 * stats.merged / stats.admitted else 0.0
            line(
                "blocked" to stats.blocked.toString(),
                "merged" to "%.0f%%".format(mergeRate),
                "restarts" to stats.restarts.toString(),
            )
            line(
                "temp" to "%.2f".format(stats.temperature),
                "guide" to compact(stats.guideExpansions),
                "best" to (stats.bestScore?.toString() ?: "-"),
            )
            line(
                "root" to stats.rootFrame.toString(),
                "tip" to stats.publishedFrame.toString(),
                "horizon" to if (stats.horizonEnd == Int.MAX_VALUE) "∞" else stats.horizonEnd.toString(),
            )
        }
        if (showHistory && historyCount > 1) {
            val count = minOf(historyCount, historyLength)
            val start = (historyWrite - count + runwayHistory.size) % runwayHistory.size
            val series = FloatArray(count) { runwayHistory[(start + it) % runwayHistory.size] }
            withStyleColor(ImGuiCol.PlotLines, runwayColor) {
                withStyleColor(ImGuiCol.FrameBg, Palette.FRAME) {
                    plotLines("##runway", series, scaleMin = 0f, graphSize = ImVec2(panelWidth.toFloat(), 26f))
                }
            }
        }
    }

    private fun ImGuiBuilder.lastSession() {
        val report = PlanningDebugChannel.lastExhaustion ?: return
        section("Last Session")
        val exitColor = when (report.exit) {
            "solved" -> Palette.OK
            "budget" -> Palette.WARNING
            else -> Palette.DANGER
        }
        line(
            "exit" to report.exit,
            "exp" to compact(report.expansions),
            "route" to "${report.deepestRouteIndex}/${report.routeNodes}",
            valueColors = mapOf("exit" to exitColor),
        )
        if (detailed) {
            line(
                "restarts" to report.tapeRestarts.toString(),
                "junction" to report.junctionRestarts.toString(),
                "repairs" to report.repairs.toString(),
            )
            line(
                "commits" to report.commitAttempts.toString(),
                "refused" to compact(report.publishRefusals),
                "starved" to report.forkStarvedDrops.toString(),
            )
            line(
                "splices" to report.improvementSplices.toString(),
                "saved" to "${report.improvementSaved}f",
                "sync" to PlanningDebugChannel.lastExhaustionLedger.substringAfter("coarseSync=", "-").substringBefore("["),
            )
            if (report.improvementDiagnosis.isNotEmpty()) {
                ImGui.pushTextWrapPos(ImGui.getCursorPosX() + panelWidth)
                textDisabled(report.improvementDiagnosis)
                ImGui.popTextWrapPos()
            }
        }
    }

    private fun ImGuiBuilder.world() {
        val graph = PlanningDebugChannel.graph
        val diagnostics = PathingService.diagnostics()
        val revision = diagnostics.substringAfter("revision=", "").substringBefore(" ")
        val pending = diagnostics.substringAfter("pendingInterest=", "").substringBefore(" ")
        if (graph == null && revision.isEmpty()) return
        section("World")
        if (graph != null) {
            line(
                "cells" to compact(graph.total),
                "drawn" to compact(graph.nodes.size),
                "edges" to compact(graph.totalEdges),
            )
            line(
                "frontier" to graph.nodes.count { it.frontier }.toString(),
                "anchors" to graph.nodes.count { it.anchor }.toString(),
                "cost" to "%.0f..%.0f".format(graph.cheapest, graph.dearest),
            )
        }
        if (revision.isNotEmpty()) line("rev" to revision, "pending" to pending)
    }

    // ---- Widgets -------------------------------------------------------------------------

    /** A muted section caption with a hairline, the width of the panel. */
    private fun ImGuiBuilder.section(title: String) {
        spacing()
        val draw = windowDrawList
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        val h = ImGui.getTextLineHeight()
        val w = ImGui.calcTextSize(title).x
        textDisabled(title)
        draw.addLine(x + w + 6f, y + h * 0.55f, x + panelWidth, y + h * 0.55f, im(Palette.HAIRLINE), 1f)
    }

    /** A slim, label-free progress bar the width of the panel. */
    private fun ImGuiBuilder.bar(fraction: Float, color: Color) {
        withStyleColor(ImGuiCol.PlotHistogram, color) {
            withStyleColor(ImGuiCol.FrameBg, Palette.FRAME) {
                ImGui.progressBar(fraction, panelWidth.toFloat(), BAR_HEIGHT, "")
            }
        }
    }

    /** One line of "label value" cells in equal columns; the label muted, the value bright. */
    private fun ImGuiBuilder.line(vararg cells: Pair<String, String>, valueColors: Map<String, Color> = emptyMap()) {
        val flags = ImGuiTableFlags.SizingStretchSame or ImGuiTableFlags.NoPadOuterX
        if (!ImGui.beginTable("##l${widgetId++}", cells.size, flags, panelWidth.toFloat(), 0f)) return
        cells.forEach { (label, value) ->
            ImGui.tableNextColumn()
            textDisabled(label)
            sameLine()
            textColored(value, valueColors[label] ?: Palette.TEXT)
        }
        ImGui.endTable()
    }

    /** One stacked bar of the tape's frames by movement kind, in the world render's colours. */
    private fun ImGuiBuilder.movementBar(parts: List<Pair<MovementId, Int>>) {
        val total = parts.sumOf { it.second }
        if (total <= 0) return
        val merged = LinkedHashMap<MovementId, Int>()
        parts.forEach { (movement, frames) -> merged.merge(movement, frames, Int::plus) }
        val ordered = merged.entries.sortedByDescending { it.value }

        val draw = windowDrawList
        val x0 = ImGui.getCursorScreenPosX()
        val y0 = ImGui.getCursorScreenPosY() + 2f
        val width = panelWidth.toFloat()
        var x = x0
        ordered.forEach { (movement, frames) ->
            val w = width * frames / total
            draw.addRectFilled(x, y0, x + w, y0 + BAR_HEIGHT, im(movementColor(movement)), 1.5f)
            x += w
        }
        ImGui.dummy(width, BAR_HEIGHT + 2f)

        val legend = ordered.take(4).joinToString("  ") { (movement, frames) ->
            "${movement.key} ${100 * frames / total}%"
        }
        textDisabled(legend)
    }

    private fun movementColor(movement: MovementId): Color {
        val config = PathingService.renderConfig
        return when (movement) {
            MovementId.WALK -> config.walkColor
            MovementId.STEP_UP -> config.stepUpColor
            MovementId.WALK_OFF -> config.walkOffColor
            MovementId.DROP -> config.dropColor
            MovementId.JUMP -> config.jumpColor
            MovementId.BOUNCE -> config.bounceColor
            MovementId.CLIMB, MovementId.LADDER_CATCH -> config.climbColor
            else -> config.unknownMovementColor
        }
    }

    private fun badge(status: State, telemetry: Telemetry): Pair<String, Color> = when (status) {
        State.Idle -> "IDLE" to Palette.MUTED
        is State.Settling -> "SETTLING" to Palette.WARNING
        is State.Planning -> "PLANNING" to Palette.ACCENT
        is State.Aligning -> "ALIGNING %.1f°".format(status.yawError) to Palette.ACCENT
        is State.Executing -> (if (telemetry.published?.partial == true) "WALKING · PARTIAL" else "WALKING") to Palette.OK
        is State.Complete -> "ARRIVED · ${status.frames}f" to Palette.OK
        is State.Failed -> "FAILED" to Palette.DANGER
    }

    private fun goalLabel(status: State, telemetry: Telemetry): String = when (status) {
        is State.Settling -> status.goal
        is State.Planning -> status.goal
        else -> telemetry.published?.finalGoal?.toString() ?: ""
    }

    /** One sample per HUD frame while a search publishes. */
    private fun sample(stats: SearchStatsView) {
        if (stats.expansions == lastSampledExpansions) return
        lastSampledExpansions = stats.expansions
        val runway = if (stats.publishedFrame >= 0 && stats.cursorFrame >= 0) (stats.publishedFrame - stats.cursorFrame).toFloat() else 0f
        runwayHistory[historyWrite] = runway
        historyWrite = (historyWrite + 1) % runwayHistory.size
        historyCount = minOf(historyCount + 1, runwayHistory.size)
    }

    /** 1234 -> "1.2k", 1_234_567 -> "1.2M": counters that only need their magnitude. */
    private fun compact(value: Int): String = when {
        value >= 1_000_000 -> "%.1fM".format(value / 1e6)
        value >= 10_000 -> "%.0fk".format(value / 1e3)
        value >= 1_000 -> "%.1fk".format(value / 1e3)
        else -> value.toString()
    }

    private fun im(color: Color): Int = ImColor.rgba(color.red, color.green, color.blue, color.alpha)

    private object Palette {
        val TEXT = Color(235, 238, 245)
        val MUTED = Color(148, 163, 184)
        val ACCENT = Color(96, 165, 250)
        val OK = Color(52, 211, 153)
        val WARNING = Color(251, 191, 36)
        val DANGER = Color(248, 113, 113)
        val FRAME = Color(255, 255, 255, 18)
        val HAIRLINE = Color(148, 163, 184, 70)
    }

    private const val BAR_HEIGHT = 5f

    /** Certified frames left ahead of the body before the walk is about to stand still. */
    private const val STARVING_RUNWAY_FRAMES = 20
}
