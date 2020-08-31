package no.nav.klage.common

import io.micrometer.core.instrument.MeterRegistry
import no.nav.klage.getLogger
import org.springframework.stereotype.Component

@Component
class KlageMetrics(private val meterRegistry: MeterRegistry) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private const val COUNTER_KLAGER_RESENT = "klager_resent"
    }

    fun incrementKlagerResent() {
        try {
            meterRegistry.counter(COUNTER_KLAGER_RESENT).increment()
        } catch (e: Exception) {
            logger.warn("incrementKlagerResent failed", e)
        }
    }
}