package com.lambda.pathing.graph

class LongGraph(
	private val successorProvider: LongEdgeProvider,
	private val predecessorProvider: LongEdgeProvider = successorProvider,
) {
	fun successors(node: Long): EdgeList = EdgeList().also { successorProvider.edges(node, it) }.dropNonFinite()

	fun predecessors(node: Long): EdgeList = EdgeList().also { predecessorProvider.edges(node, it) }.dropNonFinite()

	internal fun successorsInto(node: Long, sink: EdgeList): EdgeList {
		sink.clear()
		successorProvider.edges(node, sink)
		return sink.dropNonFinite()
	}

	internal fun predecessorsInto(node: Long, sink: EdgeList): EdgeList {
		sink.clear()
		predecessorProvider.edges(node, sink)
		return sink.dropNonFinite()
	}
}
