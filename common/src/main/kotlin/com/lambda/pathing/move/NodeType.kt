/*
 * Copyright 2025 Lambda
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

package com.lambda.pathing.move

enum class NodeType(val penalty: Float) {
    BLOCKED(-1.0f),
    OPEN(0.0f),
    WALKABLE(0.0f),
    WALKABLE_DOOR(0.0f),
    TRAPDOOR_CLOSED(0.0f),
    TRAPDOOR_OPEN(0.0f),
    POWDER_SNOW(-1.0f),
    DANGER_POWDER_SNOW(0.0f),
    FENCE(-1.0f),
    FENCE_GATE_OPEN(0.0f),
    FENCE_GATE_CLOSED(-1.0f),
    LAVA(-1.0f),
    WATER(8.0f),
    DANGER_FIRE(8.0f),
    DAMAGE_FIRE(16.0f),
    DANGER_OTHER(8.0f),
    DAMAGE_OTHER(-1.0f),
    DOOR_OPEN(0.0f),
    DOOR_WOOD_CLOSED(-1.0f),
    DOOR_IRON_CLOSED(-1.0f),
    LEAVES(-1.0f),
    STICKY_HONEY(8.0f),
    SLIME(0.0f),
    SOUL_SAND(0.0f),
    SOUL_SOIL(0.0f),
    DAMAGE_CAUTIOUS(0.0f),
    LADDER(0.0f),
    SCAFFOLDING(0.0f),
    DRIP_LEAF(8.0f)
}