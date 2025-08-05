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

package com.lambda.interaction.construction.result

enum class Rank {
    // solvable
    BREAK_SUCCESS,
    INTERACT_SUCCESS,
    PLACE_SUCCESS,
    WRONG_ITEM,
    BREAK_ITEM_CANT_MINE,
    PLACE_BLOCKED_BY_PLAYER,
    NOT_VISIBLE,
    OUT_OF_REACH,
    BREAK_NOT_EXPOSED,
    CHUNK_NOT_LOADED,
    PLACE_CANT_REPLACE,
    BREAK_PLAYER_ON_TOP,
    PLACE_NOT_ITEM_BLOCK,

    // not solvable
    OUT_OF_WORLD,
    BREAK_RESTRICTED,
    PLACE_NO_INTEGRITY,
    BREAK_SUBMERGE,
    BREAK_IS_BLOCKED_BY_FLUID,
    UNBREAKABLE,
    BREAK_NO_PERMISSION,
    PLACE_SCAFFOLD_EXCEEDED,
    PLACE_BLOCK_FEATURE_DISABLED,
    UNEXPECTED_POSITION,
    PLACE_ILLEGAL_USAGE,

    // not an issue
    DONE,
    IGNORED;

    val solvable: Boolean
        get() = ordinal < PLACE_NOT_ITEM_BLOCK.ordinal
}
