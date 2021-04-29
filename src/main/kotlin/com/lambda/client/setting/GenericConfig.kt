package com.lambda.client.setting

import com.lambda.client.setting.configs.NameableConfig

internal object GenericConfig : NameableConfig<GenericConfigClass>(
    "generic",
    "${com.lambda.client.LambdaMod.DIRECTORY}config/"
)