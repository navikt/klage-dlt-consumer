package no.nav.klage.clients

import no.nav.klage.common.KlageMetrics
import no.nav.klage.getLogger
import no.nav.klage.getTeamLogger
import no.nav.slackposter.Severity
import no.nav.slackposter.SlackClient
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class DLTKafkaConsumer(
    private val slackClient: SlackClient,
    private val consumerProps: Map<String, Any>,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val klageMetrics: KlageMetrics
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun kafkaConsumer(): KafkaConsumer<String, String> {
        val kafkaConsumer = KafkaConsumer<String, String>(consumerProps)
        kafkaConsumer.subscribe(listOf(topic))
        return kafkaConsumer
    }

    @Value("\${KAFKA_TOPIC}")
    private lateinit var topic: String

    @Scheduled(cron = "\${DLT_CHECK_CRON}", zone = "Europe/Oslo")
    fun dltListener() {
        kafkaConsumer().use { kafkaConsumer ->
            logger.debug("Looking for failed klager from DLT")
            slackClient.postMessage("Sjekker DLT for klager som feilet", Severity.INFO)

            var successfullySent = 0
            var failedRecordsCount = 0

            runCatching {
                val failedRecords = getFailedRecords(kafkaConsumer)
                failedRecordsCount = failedRecords.count()
                logger.debug("Found $failedRecordsCount failed records")
                slackClient.postMessage("Fant $failedRecordsCount klager som har feilet", Severity.INFO)

                //Mark records as read, even if they fail later, to make sure we don't read them again.
                kafkaConsumer.commitSync()

                failedRecords.forEach { record ->
                    logger.debug("Sending failed klage to original topic. See team-logs for more details.")
                    teamLogger.debug("Previously failed klage received from DLT: {}", record.value())
                    runCatching {
                        //Send to original topic
                        kafkaTemplate.send(topic.removeSuffix("-dlt"), record.value())

                        successfullySent++
                        logger.debug("Klage sent back successfully")

                        //Record metrics
                        klageMetrics.incrementKlagerResent()
                    }.onFailure { failure ->
                        logger.error("Could not send klage. See team-logs for details.")
                        teamLogger.error("Failed to send failed klage message back to original topic", failure)
                        slackClient.postMessage(
                            "Kunne ikke legge tilbake feilet klage til klage-topic! " +
                                    "(${causeClass(rootCause(failure))})", Severity.ERROR
                        )
                    }
                }
            }.onFailure {
                logger.error("Could not poll from DLT", it)
            }

            //Only log if there is something to report back
            if (failedRecordsCount > 0) {
                logger.debug("In total, $successfullySent klager was sent back to original topic")
                slackClient.postMessage(
                    "Totalt $successfullySent klager ble sendt tilbake til opprinnelig topic for behandling",
                    Severity.INFO
                )
            }
        }
    }

    private fun getFailedRecords(kafkaConsumer: KafkaConsumer<String, String>): ConsumerRecords<String, String> {
        val maxTries = 2
        var tries = 0
        while (true) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(15))
            if (records.count() > 0) {
                return records
            }
            if (++tries >= maxTries) {
                break
            }
        }
        return ConsumerRecords.empty()
    }

    private fun rootCause(t: Throwable): Throwable = t.cause?.run { rootCause(this) } ?: t

    private fun causeClass(t: Throwable) = t.stackTrace[0].className
}
