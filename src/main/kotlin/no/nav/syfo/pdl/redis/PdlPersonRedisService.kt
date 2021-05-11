package no.nav.syfo.pdl.redis

import no.nav.syfo.application.jedisObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class PdlPersonRedisService(private val jedisPool: JedisPool, private val redisSecret: String) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(PdlPersonRedisService::class.java)
        private const val redisTimeoutSeconds: Int = 3600
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

    fun getPerson(fnrs: List<String>): Map<String, PdlPersonRedisModel?> {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            return jedis.mget(*fnrs.map { "${prefix}$it" }.toTypedArray()).mapNotNull { jedisObjectMapper.readValue(it, PdlPersonRedisModel::class.java) }.associateBy { it.fnr }
        } catch (ex: Exception) {
            log.error("Could not get redis for person", ex)
            emptyMap()
        } finally {
            jedis?.close()
        }
    }
}
