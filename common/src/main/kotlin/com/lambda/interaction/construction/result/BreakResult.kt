package com.lambda.interaction.construction.result

import com.lambda.interaction.construction.context.BreakContext
import com.lambda.task.tasks.BreakBlock.Companion.uncheckedBreak

sealed class BreakResult : BuildResult() {

    /**
     * Represents a successful break. All checks have been passed.
     * @param context The context of the break.
     */
    data class Success(val context: BreakContext) : Resolvable, BreakResult() {
        override val rank = Rank.BREAK_SUCCESS

        override val resolve = uncheckedBreak(context.hitPos)

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is Success -> context.compareTo(other.context)
                else -> super.compareTo(other)
            }
        }
    }
}
