package com.princeyadav.grayout.scheduling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
