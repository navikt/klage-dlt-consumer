package no.nav.klage.config

import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.*
import java.io.File
import java.util.*


@Configuration
class KafkaConfiguration {

    @Value("\${KAFKA_BOOTSTRAP_SERVERS}")
    private lateinit var bootstrapServers: String

    @Value("\${KAFKA_GROUP_ID}")
    private lateinit var groupId: String

    @Value("\${SERVICE_USER_USERNAME}")
    private lateinit var username: String

    @Value("\${SERVICE_USER_PASSWORD}")
    private lateinit var password: String

    @Value("\${KAFKA_TOPIC}")
    private lateinit var topic: String

    @Bean
    fun kafkaConsumer(): KafkaConsumer<String, String> {
        val kafkaConsumer = KafkaConsumer<String, String>(consumerProps())
        kafkaConsumer.subscribe(listOf(topic))
        return kafkaConsumer
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        return DefaultKafkaProducerFactory(producerProps())
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }

    @Bean
    fun producerProps(): Map<String, Any> {
        val props: MutableMap<String, Any> = HashMap()
        props[ProducerConfig.CLIENT_ID_CONFIG] = "clientId"
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props.putAll(commonSecurityProps())
        return props
    }

    private fun consumerProps(): Map<String, Any> {
        val props = mutableMapOf<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        props[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = Integer.MAX_VALUE
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = true
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props.putAll(commonSecurityProps())
        return props
    }

    private fun commonSecurityProps(): Map<String, Any> {
        val props: MutableMap<String, Any> = HashMap()
        props[SaslConfigs.SASL_JAAS_CONFIG] =
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
        props[SaslConfigs.SASL_MECHANISM] = "PLAIN"
        System.getenv("NAV_TRUSTSTORE_PATH")?.let {
            props[SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
            props[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = File(it).absolutePath
            props[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = System.getenv("NAV_TRUSTSTORE_PASSWORD")
        }
        return props
    }

}