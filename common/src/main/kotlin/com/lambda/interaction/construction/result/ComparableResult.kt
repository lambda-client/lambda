package com.lambda.interaction.construction.result

sealed interface ComparableResult<T : Enum<T>> : Comparable<ComparableResult<T>> {
    val rank: T

    override fun compareTo(other: ComparableResult<T>): Int {
        return rank.compareTo(other.rank)
    }
}
