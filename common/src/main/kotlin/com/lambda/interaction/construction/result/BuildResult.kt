package com.lambda.interaction.construction.result

abstract class BuildResult : ComparableResult<Rank> {

    /**
     * The build action is done.
     */
    data object Done : BuildResult() {
        override val rank = Rank.DONE
    }

    /**
     * The build action is ignored.
     */
    data object Ignored : BuildResult() {
        override val rank = Rank.IGNORED
    }
}