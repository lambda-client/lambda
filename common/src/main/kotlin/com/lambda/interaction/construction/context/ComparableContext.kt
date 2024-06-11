package com.lambda.interaction.construction.context

interface ComparableContext : Comparable<ComparableContext> {
    override fun compareTo(other: ComparableContext): Int {
        return 0
    }
}
