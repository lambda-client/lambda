package com.lambda.client.commons.extension

import java.util.*


fun <K, V> SortedMap<K, V>.firstKeyOrNull(): K? =
    if (this.isNotEmpty()) firstKey() else null

fun <K, V> NavigableMap<K, V>.firstValueOrNull(): V? =
    this.firstEntryOrNull()?.value

fun <K, V> NavigableMap<K, V>.firstValue(): V =
    this.firstEntry().value

fun <K, V> NavigableMap<K, V>.firstEntryOrNull(): MutableMap.MutableEntry<K, V>? =
    if (this.isNotEmpty()) firstEntry() else null

fun <K, V> MutableMap<K, V>.synchronized(): MutableMap<K, V> =
    Collections.synchronizedMap(this)

fun <K, V> SortedMap<K, V>.synchronized(): SortedMap<K, V> =
    Collections.synchronizedSortedMap(this)

fun <K, V> NavigableMap<K, V>.synchronized(): NavigableMap<K, V> =
    Collections.synchronizedNavigableMap(this)