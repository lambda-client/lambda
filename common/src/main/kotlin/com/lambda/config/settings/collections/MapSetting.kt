package com.lambda.config.settings.collections

import com.lambda.config.AbstractSetting
import java.lang.reflect.Type

class MapSetting<K, V>(
    override val name: String,
    defaultValue: MutableMap<K, V>,
    type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableMap<K, V>>(
    defaultValue,
    type,
    description,
    visibility
)
