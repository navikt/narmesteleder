package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import java.net.URI
import no.nav.syfo.pdl.util.RedisConfig
import org.amshove.kluent.shouldBeEqualTo

class RedisConfigTest :
    FunSpec({
        test("Test config") {
            val redisConfig =
                RedisConfig(
                    redisUri = URI("rediss://redis-teamsykmelding-infotrygd.no:12345"),
                    redisUsername = "Username",
                    redisPassword = "Password",
                )

            redisConfig.host shouldBeEqualTo "redis-teamsykmelding-infotrygd.no"
            redisConfig.port shouldBeEqualTo 12345
        }
    })
