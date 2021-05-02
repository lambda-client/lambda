package com.lambda.client.setting

import com.lambda.client.LambdaMod
import com.lambda.client.setting.configs.NameableConfig

internal object GenericConfig : NameableConfig<GenericConfigClass>(
    "generic",
    "${LambdaMod.DIRECTORY}config/"
)