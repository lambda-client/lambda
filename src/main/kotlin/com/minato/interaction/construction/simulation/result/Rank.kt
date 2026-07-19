
package com.minato.interaction.construction.simulation.result

enum class Rank {
    // solvable
    PlaceSuccess,
    BreakSuccess,
    WrongItem,
    BreakItemCantMine,
    PlaceBlockedByPlayer,
    NotVisible,
    OutOfReach,
    PlaceBlockedByEntity,
    BreakNotExposed,
    ChunkNotLoaded,
    PlaceCantReplace,
    BreakPlayerOnTop,

    // not solvable
    OutOfWorld,
    BreakRestricted,
    PlaceNoIntegrity,
    BreakSubmerge,
    BreakIsBlockedByFluid,
    Unbreakable,
    BreakNoPermission,
    PlaceScaffoldExceeded,
    PlaceBlockFeatureDisabled,
    UnexpectedPosition,
    PlaceIllegalUsage,

    // not an issue
    Done,
    Ignored,
    NoMatch;
}
