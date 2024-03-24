package com.lambda.interaction.rotation

data class RotationRequest(
    val config: IRotationConfig,
    val rotation: Rotation,
    val priority: Int = 0,
) : Comparable<RotationRequest> {
    var isPending: Boolean = true

    override fun compareTo(other: RotationRequest) =
        priority.compareTo(other.priority)
}