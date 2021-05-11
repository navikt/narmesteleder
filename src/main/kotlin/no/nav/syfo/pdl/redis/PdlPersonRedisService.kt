package no.nav.syfo.pdl.redis

import no.nav.syfo.application.jedisObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class PdlPersonRedisService(private val jedisPool: JedisPool, private val redisSecret: String) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(PdlPersonRedisService::class.java)
        private const val redisTimeoutSeconds: Int = 600
        private const val prefix = "PDL"
    }

    fun updatePerson(pdlPersonRedisModel: PdlPersonRedisModel, fnr: String) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            jedis.setex("${prefix}$fnr", redisTimeoutSeconds, jedisObjectMapper.writeValueAsString(pdlPersonRedisModel))
        } catch (ex: Exception) {
            log.error("Could not update redis for person {}", ex.message)
        } finally {
            jedis?.close()
        }
    }

    fun getPerson(fnr: String): PdlPersonRedisModel? {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            when (val stringValue = jedis.get("${prefix}$fnr")) {
                null -> null
                else -> jedisObjectMapper.readValue(stringValue, PdlPersonRedisModel::class.java)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for person", ex.message)
            null
        } finally {
            jedis?.close()
        }
    }
}
