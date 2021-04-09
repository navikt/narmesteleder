package no.nav.syfo.testutils

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.application.db.DatabaseInterface
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TestDB : DatabaseInterface {
    private var pg: EmbeddedPostgres? = null
    override val connection: Connection
        get() = pg!!.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = EmbeddedPostgres.start()
        pg!!.postgresDatabase.connection.use {
            connection ->
            connection.prepareStatement("create role cloudsqliamuser;").executeUpdate()
        }
        Flyway.configure().run {
            dataSource(pg?.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg?.close()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM narmeste_leder").executeUpdate()
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
    timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
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
                 """
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
