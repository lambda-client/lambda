package com.lambda.client.setting

import com.lambda.client.setting.configs.NameableConfig
import com.lambda.client.util.FolderUtils

internal object GenericConfig : NameableConfig<GenericConfigClass>(
    "generic",
    "${FolderUtils.lambdaFolder}config/"
)