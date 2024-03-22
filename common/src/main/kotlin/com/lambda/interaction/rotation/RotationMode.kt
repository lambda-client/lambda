package com.lambda.interaction.rotation

/**
 * @property SILENT Spoofing server-side rotation.
 * @property SYNC Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
 * @property LOCK Locks the camera client-side.
 */
enum class RotationMode {
    SILENT,
    SYNC,
    LOCK
}