package com.kclaw.gateway.ws

/** Upper bound for a single WebSocket frame payload (25 MiB). */
const val MAX_FRAME_BYTES: Long = 25L * 1024L * 1024L
