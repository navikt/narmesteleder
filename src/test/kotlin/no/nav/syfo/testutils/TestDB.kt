package no.nav.syfo.testutils

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.log
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDB : DatabaseInterface {

    companion object {
        private var database: DatabaseInterface
        private val psqlContainer: PsqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")
            .withInitScript("db/test-db.sql")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            coEvery { mockEnv.databasePassword } returns "password"
            coEvery { mockEnv.databaseUsername } returns "username"
            every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl

            database = try {
                Database(mockEnv)
            } catch (ex: Exception) {
                log.error("error", ex)
                Database(mockEnv)
            }
        }
    }

    override val connection: Connection
        get() = database.connection

    fun stop() {
        this.connection.dropData()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM narmeste_leder").executeUpdate()
        connection.prepareStatement("DELETE FROM orgnummer").execute()
        connection.commit()
    }
}

fun Connection.lagreNarmesteleder(
    orgnummer: String,
    fnr: String,
    fnrNl: String,
    arbeidsgiverForskutterer: Boolean?,
    aktivFom: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
    aktivTom: OffsetDateTime? = null,
    timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
) {
    use { connection ->
        connection.prepareStatement(
            """
                INSERT INTO narmeste_leder(
                    orgnummer,
                    bruker_fnr,
                    narmeste_leder_fnr,
                    narmeste_leder_telefonnummer,
                    narmeste_leder_epost,
                    arbeidsgiver_forskutterer,
                    aktiv_fom,
                    aktiv_tom,
                    timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """,
        ).use {
            it.setString(1, orgnummer)
            it.setString(2, fnr)
            it.setString(3, fnrNl)
            it.setString(4, "90909090")
            it.setString(5, "epost@nav.no")
            it.setObject(6, arbeidsgiverForskutterer)
            it.setTimestamp(7, Timestamp.from(aktivFom.toInstant()))
            it.setTimestamp(8, aktivTom?.let { Timestamp.from(aktivTom.toInstant()) })
            it.setTimestamp(9, Timestamp.from(timestamp.toInstant()))
            it.execute()
        }
        connection.commit()
    }
}
