/*
 * Reference implementation of the generic D* Lite stack, kept as the oracle for
 * DStarDifferentialTest against the packed-long engine in com.lambda.pathing.graph.
 * Not used by production code.
 */

package pathing.reference

data class Key(
    val first: Double,
    val second: Double,
) : Comparable<Key> {
    override fun compareTo(other: Key): Int {
        val firstComparison = first.compareTo(other.first)
        return if (firstComparison != 0) firstComparison else second.compareTo(other.second)
    }

    override fun toString() = "(%.3f, %.3f)".format(first, second)

    companion object {
        val INFINITY = Key(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }
}
