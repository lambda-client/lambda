/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.gson
import net.minecraft.util.Identifier
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object StringUtils {
    /**
     * Returns a sanitized file path for both Unix and Linux systems
     */
    fun String.sanitizeForFilename() =
        replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(255) // truncate to 255 characters for Windows compatibility


    /**
     * Capitalizes the first character of a string using its Unicode mapping
     */
    fun String.capitalize() = replaceFirstChar { it.titlecase() }

    /**
     * Returns a Minecraft [net.minecraft.util.Identifier] from a string with the given namespace
     */
    fun String.toIdentifier(namespace: String = Lambda.MOD_ID): Identifier =
        Identifier.of(namespace, this)

    /**
     * Returns a [Lambda] Minecraft [Identifier] from a string
     */
    val String.asIdentifier: Identifier get() = toIdentifier()

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

    /**
     * Takes the receiver string and decodes it to the input type
     *
     * @return Instance of [T]
     */
    inline fun <reified T : Any> String.json() = gson.fromJson(this, T::class.java)

    /**
     * @see kotlin.io.encoding.Base64.decode
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun String.base64UrlDecode() = Base64.UrlSafe.decode(this).decodeToString()

    /**
     * See [MessageDigest section](https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#messagedigest-algorithms) of the Java Security Standard Algorithm Names Specification
     *
     * @receiver        The string to hash
     * @param algorithm The algorithm instance to use
     * @param extra     Additional data to digest with the string
     *
     * @return          The string representation of the hash
     */
    fun String.hashString(algorithm: String, vararg extra: ByteArray): String =
        toByteArray().hash(algorithm, *extra)
            .joinToString(separator = "") { "%02x".format(it) }

    /**
     * See [MessageDigest section](https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#messagedigest-algorithms) of the Java Security Standard Algorithm Names Specification
     *
     * @receiver        The byte array to hash
     * @param algorithm The algorithm instance to use
     * @param extra     Additional data to digest with the byte array
     *
     * @return          The string representation of the hash
     */
    fun ByteArray.hashString(algorithm: String, vararg extra: ByteArray): String =
        hash(algorithm, *extra)
            .joinToString(separator = "") { "%02x".format(it) }

    /**
     * See [MessageDigest section](https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#messagedigest-algorithms) of the Java Security Standard Algorithm Names Specification
     *
     * @receiver        The byte array to hash
     * @param algorithm The algorithm instance to use
     * @param extra     Additional data to digest with the byte array
     *
     * @return          The digested data
     */
    fun ByteArray.hash(algorithm: String, vararg extra: ByteArray): ByteArray =
        MessageDigest
            .getInstance(algorithm)
            .apply { update(this@hash); extra.forEach(::update) }
            .digest()
}
