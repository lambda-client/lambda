package com.lambda.interaction.construction.result

enum class Rank {
    // solvable
    BREAK_SUCCESS,
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
    BREAK_IS_BLOCKED_BY_LIQUID,
    UNBREAKABLE,
    BREAK_NO_PERMISSION,
    PLACE_SCAFFOLD_EXCEEDED,
    PLACE_BLOCK_FEATURE_DISABLED,
    PLACE_ILLEGAL_USAGE,

    // not an issue
    DONE,
    IGNORED;

    val solvable: Boolean
        get() = ordinal < OUT_OF_WORLD.ordinal
}