
package com.minato.interaction.construction.simulation.result

interface ComparableResult<T : Enum<T>> : Comparable<ComparableResult<T>> {
    val rank: T
    val compareBy: ComparableResult<T>

    override fun compareTo(other: ComparableResult<T>) =
        compareBy.compareResult(other.compareBy)

    fun compareResult(other: ComparableResult<T>): Int =
        compareBy.rank.compareTo(other.compareBy.rank)
}
