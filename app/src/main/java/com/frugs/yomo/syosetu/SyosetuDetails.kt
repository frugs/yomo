package com.frugs.yomo.syosetu

import java.util.Date

data class SyosetuDetails(
    val ncode: String,
    val author: String,
    val title: String,
    val synopsis: String,
    val pages: Int,
    val lastUpdatedDateTime: Date?)