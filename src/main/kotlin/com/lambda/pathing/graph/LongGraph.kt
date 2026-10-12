package com.lambda.pathing.graph

class LongGraph(
	private val successorProvider: LongEdgeProvider,
	private val predecessorProvider: LongEdgeProvider = successorProvider,
	private val reachedSuccessorProvider: ReachedEdgeProvider? = null,
) {
	fun successors(node: Long): EdgeList = EdgeList().also { successorProvider.edges(node, it) }.dropNonFinite()

	internal fun successorsInto(node: Long, sink: EdgeList): EdgeList {
		sink.clear()
		successorProvider.edges(node, sink)
		return sink.dropNonFinite()
	}

	internal fun reachedSuccessorsInto(node: Long, sink: EdgeList, reached: java.util.function.LongPredicate): EdgeList {
		val provider = reachedSuccessorProvider ?: return successorsInto(node, sink)
		sink.clear()
		provider.edges(node, sink, reached)
		return sink.dropNonFinite()
	}

	internal fun predecessorsInto(node: Long, sink: EdgeList): EdgeList {
		sink.clear()
		predecessorProvider.edges(node, sink)
		return sink.dropNonFinite()
	}
}
