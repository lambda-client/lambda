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

package com.lambda.pathing.refinement

enum class ShortcutFailureReason(val description: String) {
    Accepted("The shortcut satisfied all prechecks and arrival checks."),
    StartBlocked("The start point is not a safe standing position."),
    EndBlocked("The end point is not a safe standing position."),
    SweepBlocked("A dense same-height standing-position sample intersected a block or lost support."),
    HorizontalCollision("The movement simulator reported a horizontal collision during the attempt."),
    Backtracked("The simulated actor moved materially behind the beginning of the shortcut segment."),
    Overshot("The simulated actor travelled meaningfully past the end of the segment and was still not near the goal."),
    LeftCorridor("The simulated actor drifted outside the allowed lateral corridor around the shortcut segment."),
    UnplannedRise("The profile climbed higher than its allowed vertical envelope."),
    UnplannedDrop("The profile fell lower than its allowed vertical envelope."),
    Stagnated("The attempt stopped making meaningful progress toward the target before arriving."),
    Timeout("The attempt ran out of simulated ticks before reaching or softly recovering near the target."),
    HybridStepBlocked("The vertical transition phase of a hybrid shortcut could not reach the target y level."),
    HybridSweepBlocked("The vertical transition of a hybrid shortcut succeeded but the flat sweep tail hit an obstacle."),
}