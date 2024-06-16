package com.frugs.yomo.util

import java.util.concurrent.ConcurrentHashMap

private val tags: MutableMap<Class<Any>, String> = ConcurrentHashMap()

val Any.TAG: String
  get() {
    return tags.computeIfAbsent(javaClass) {
      if (!javaClass.isAnonymousClass) {
        javaClass.simpleName
      } else {
        javaClass.name
      }
    }
  }
