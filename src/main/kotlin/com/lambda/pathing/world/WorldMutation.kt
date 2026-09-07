package com.lambda.pathing.world

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection

sealed interface WorldMutation {
    val revision: Long

    data class Section(
        val section: PathingSection,
        override val revision: Long,
    ) : WorldMutation

    data class Chunk(
        val chunk: PathingChunk,
        override val revision: Long,
    ) : WorldMutation
}
