package com.sanggwonai.api.common.util

import java.util.UUID

object IdGenerator {

    fun next(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(20)}"
}
