package com.lambda.pathing.graph

class LongGraph(
	private val successorProvider: LongEdgeProvider,
	private val predecessorProvider: LongEdgeProvider = successorProvider,
	private val reachedSuccessorProvider: ValuedEdgeProvider? = null,
	private val unknownPredecessorProvider: FilteredEdgeProvider? = null,
	private val relevantPredecessorProvider: RelevantEdgeProvider? = null,
) {
	fun successors(node: Long): EdgeList = EdgeList().also { successorProvider.edges(node, it) }.dropNonFinite()

	internal fun successorsInto(node: Long, sink: EdgeList): EdgeList {
		sink.clear()
		successorProvider.edges(node, sink)
		return sink.dropNonFinite()
	}

	internal fun reachedSuccessorsInto(node: Long, sink: EdgeList, value: java.util.function.LongToDoubleFunction): EdgeList {
		val provider = reachedSuccessorProvider ?: return successorsInto(node, sink)
		sink.clear()
		provider.edges(node, sink, value)
		return sink.dropNonFinite()
	}

	internal fun relevantPredecessorsInto(node: Long, sink: EdgeList, relevance: EdgeRelevance): EdgeList {
		val provider = relevantPredecessorProvider ?: return predecessorsInto(node, sink)
		sink.clear()
		provider.edges(node, sink, relevance)
		return sink.dropNonFinite()
	}

	internal fun unknownPredecessorsInto(node: Long, sink: EdgeList, unknown: java.util.function.LongPredicate): EdgeList {
		val provider = unknownPredecessorProvider ?: return predecessorsInto(node, sink)
		sink.clear()
		provider.edges(node, sink, unknown)
		return sink.dropNonFinite()
	}

	internal fun predecessorsInto(node: Long, sink: EdgeList): EdgeList {
		sink.clear()
		predecessorProvider.edges(node, sink)
		return sink.dropNonFinite()
	}
}
