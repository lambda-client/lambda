package com.lambda.interaction.rotation

interface IRotationConfig {
    /**
     * - [RotationMode.NONE] No rotation.
     * - [RotationMode.SILENT] Spoofing server-side rotation.
     * - [RotationMode.SYNC] Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
     * - [RotationMode.LOCK] Locks the camera client-side.
     */
    val rotationMode: RotationMode

    /**
     * The rotation speed (in degrees).
     */
    val turnSpeed: Double

    /**
     * Ticks the rotation should not be changed.
     */
    val keepTicks: Int

    /**
     * Ticks to rotate back to the actual rotation.
     */
    val resetTicks: Int
}