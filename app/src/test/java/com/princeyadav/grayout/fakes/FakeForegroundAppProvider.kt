package com.princeyadav.grayout.fakes

import com.princeyadav.grayout.service.ForegroundAppProvider

/**
 * In-memory [ForegroundAppProvider] for JVM unit tests. Set [foregroundPackage]
 * to whatever the next poll should observe; `null` simulates an empty query
 * window / usage access denied.
 */
class FakeForegroundAppProvider : ForegroundAppProvider {
    var foregroundPackage: String? = null
    override fun currentForegroundPackage(): String? = foregroundPackage
}
