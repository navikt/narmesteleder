package no.nav.syfo.narmesteleder.organisasjon.redis

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.jedisObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class OrganisasjonsinfoRedisService(private val jedisPool: JedisPool, private val redisSecret: String) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OrganisasjonsinfoRedisService::class.java)
        private const val redisTimeoutSeconds: Int = 28_800 // 8 timer
        private const val prefix = "ORG"
    }

    fun updateOrganisasjonsinfo(orgnummer: String, organisasjonsinfoRedisModel: OrganisasjonsinfoRedisModel) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            jedis.setex("$prefix$orgnummer", redisTimeoutSeconds, jedisObjectMapper.writeValueAsString(organisasjonsinfoRedisModel))
        } catch (ex: Exception) {
            log.error("Could not update redis for organisasjon", ex)
        } finally {
            jedis?.close()
        }
    }

    fun getOrganisasjonsinfo(orgnummer: String): OrganisasjonsinfoRedisModel? {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            when (val stringValue = jedis.get("$prefix$orgnummer")) {
                null -> null
                else -> jedisObjectMapper.readValue<OrganisasjonsinfoRedisModel>(stringValue)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for organisasjon", ex)
            null
        } finally {
            jedis?.close()
        }
    }
}
