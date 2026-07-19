
package com.minato.util

interface Nameable {
    val name: String
    val commandName get() = name.trim().replace(' ', '_')
}

interface NamedEnum {
    val displayName: String
}

interface Describable {
    val description: String
}
