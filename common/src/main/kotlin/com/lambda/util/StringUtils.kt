package com.lambda.util

object StringUtils {
    /**
     * Find similar strings in a set of words.
     *
     * @param target The string to compare against.
     * @param words The set of words to compare against.
     * @param threshold The maximum Levenshtein distance between the target and the words.
     */
    fun findSimilarStrings(
        target: String,
        words: Set<String>,
        threshold: Int,
    ) = words.filter { it.levenshteinDistance(target) <= threshold }.toSet()

    /**
     * See [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance)
     *
     * @receiver The string to compare.
     * @param rhs The string to compare against.
     */
    private fun CharSequence.levenshteinDistance(rhs: CharSequence): Int {
        if (this == rhs) {
            return 0
        }

        if (isEmpty()) {
            return rhs.length
        }

        if (rhs.isEmpty()) {
            return length
        }

        val len0 = length + 1
        val len1 = rhs.length + 1

        var cost = IntArray(len0) { it }
        var newCost = IntArray(len0) { 0 }

        for (i in 1..<len1) {
            newCost[0] = i

            for (j in 1..<len0) {
                val match = if (this[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1

                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }

            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[len0 - 1]
    }
}