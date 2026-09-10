package com.princeyadav.grayout.service

/**
 * Serializes display writes and exclusion read/decide/apply operations in this
 * process. The detector runs on a worker, schedule broadcasts run on Main, and
 * schedule resynchronization runs on IO. Locking only their final writes would
 * still let an exit restore a state captured before a schedule boundary.
 *
 * Keep UsageStats queries and suspending work outside this lock. Android settings
 * observers post callbacks to Main; no operation here waits for those callbacks.
 */
internal object GrayscaleStateLock
