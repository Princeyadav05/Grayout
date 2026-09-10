package com.princeyadav.grayout.data

import kotlinx.coroutines.sync.Mutex

/** Serializes schedule mutations, alarm replacement, and delivery across instances. */
internal val scheduleOperationMutex = Mutex()
