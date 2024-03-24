package com.lambda.interaction.rotation

data class RotationRequest(
    val priority: Int = 1,
    val config: IRotationConfig,
    val rotation: Rotation,
    var isPending: Boolean = true
) : Comparable<RotationRequest> {
    override fun compareTo(other: RotationRequest) =
        priority.compareTo(other.priority)
}