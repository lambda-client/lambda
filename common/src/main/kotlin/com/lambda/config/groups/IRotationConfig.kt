package com.lambda.config.groups

import com.lambda.interaction.rotation.RotationMode

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

    /**
     * If true, rotation will be instant without any transition. If false, rotation will transition over time.
     */
    val instant: Boolean

    /**
     * The mean (average) value for the Gaussian distribution used to calculate rotation speed.
     * This value represents the center of the distribution.
     */
    val mean: Double

    /**
     * The standard deviation for the Gaussian distribution used to calculate rotation speed.
     * This value represents the spread or dispersion of the distribution.
     */
    val derivation: Double
}