package com.lambda.interaction.construction

import com.lambda.util.primitives.extension.Structure

data class StaticBlueprint(
    override val structure: Structure
) : Blueprint() {
    companion object {
        fun Structure.toBlueprint() = StaticBlueprint(this)
    }
}