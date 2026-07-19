
package com.minato.util

import com.minato.Minato
import net.minecraft.util.Identifier
import java.security.MessageDigest
import java.util.*

@Suppress("unused")
object StringUtils {
    fun String.sanitizeForFilename() =
        replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(255) // truncate to 255 characters for Windows compatibility


    fun String.capitalize() = replaceFirstChar { it.titlecase() }

    fun String.toIdentifier(namespace: String = Minato.MOD_ID): Identifier =
        Identifier.of(namespace, this)

    val String.asIdentifier: Identifier get() = toIdentifier()

    /**
     * Find similar strings in a set of words.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Levenshtein_distance">Levenshtein distance</a<
     */
    fun String.findSimilarStrings(
        words: Set<String>,
        threshold: Int,
    ) = words.filter { it.levenshteinDistance(this) <= threshold }.toSet()

    /**
     * @see <a href="https://en.wikipedia.org/wiki/Levenshtein_distance">Levenshtein distance</a<
     */
    fun CharSequence.levenshteinDistance(rhs: CharSequence): Int {
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

    fun String.base64UrlDecode() = Base64.getUrlDecoder().decode(toByteArray()).decodeToString()

    /**
     * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#messagedigest-algorithms">Java Security Standard Algorithm Names Specification</a>
     */
    fun String.hashString(algorithm: String, vararg extra: ByteArray): String =
        toByteArray().hash(algorithm, *extra)
            .joinToString(separator = "") { "%02x".format(it) }

    /**
     * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#messagedigest-algorithms">Java Security Standard Algorithm Names Specification</a>
     */
    fun ByteArray.hashString(algorithm: String, vararg extra: ByteArray): String =
        hash(algorithm, *extra)
            .joinToString(separator = "") { "%02x".format(it) }

    /**
     * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#messagedigest-algorithms">Java Security Standard Algorithm Names Specification</a>
     */
    fun ByteArray.hash(algorithm: String, vararg extra: ByteArray): ByteArray =
        MessageDigest
            .getInstance(algorithm)
            .apply { update(this@hash); extra.forEach(::update) }
            .digest()
}
