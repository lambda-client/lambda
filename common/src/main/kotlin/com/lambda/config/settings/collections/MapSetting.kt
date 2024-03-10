package com.lambda.config.settings.collections

import com.lambda.config.AbstractSetting

class MapSetting<K, V>(
    override val name: String,
    defaultValue: Map<K, V>,
    visibility: () -> Boolean,
    description: String
) : AbstractSetting<Map<K, V>>(
    defaultValue,
    visibility,
    description
)