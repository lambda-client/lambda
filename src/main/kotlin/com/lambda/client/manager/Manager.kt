package com.lambda.client.manager

import com.lambda.client.util.Wrapper

interface Manager {
    val mc get() = Wrapper.minecraft
}