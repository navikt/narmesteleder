package no.nav.syfo.db

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.db.toList
import no.nav.syfo.forskuttering.Forskuttering
import no.nav.syfo.forskuttering.ForskutteringRespons
import no.nav.syfo.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.narmesteleder.oppdatering.model.NlResponse
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun DatabaseInterface.finnAktiveNarmestelederkoblinger(narmesteLederFnr: String): List<NarmesteLederRelasjon> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT * from narmeste_leder where narmeste_leder_fnr = ? and aktiv_tom is null;
        """
        ).use {
            it.setString(1, narmesteLederFnr)
            it.executeQuery().toList { toNarmesteLederRelasjon() }
        }
    }
}

fun DatabaseInterface.getNarmestelederRelasjon(narmestelederId: UUID, fnr: String): NarmesteLederRelasjon? {
    return connection.use { connection ->
        connection.prepareStatement(
            """
            select * from narmeste_leder where narmeste_leder_id = ? and narmeste_leder_fnr = ? and aktiv_tom is null;"""
        ).use { ps ->
            ps.setObject(1, narmestelederId)
            ps.setString(2, fnr)
            ps.executeQuery().toSingleNarmesteLederRelasjon()
        }
    }
}

private fun ResultSet.toSingleNarmesteLederRelasjon(): NarmesteLederRelasjon? {
    return when (next()) {
        true -> toNarmesteLederRelasjon()
        false -> null
    }
}

fun DatabaseInterface.finnNarmestelederForSykmeldt(fnr: String, orgnummer: String): NarmesteLederRelasjon? {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT * from narmeste_leder where bruker_fnr = ? and orgnummer = ? and aktiv_tom is null;
        """
        ).use {
            it.setString(1, fnr)
            it.setString(2, orgnummer)
            it.executeQuery().toList { toNarmesteLederRelasjon() }.firstOrNull()
        }
    }
}

fun DatabaseInterface.finnAlleNarmesteledereForSykmeldt(fnr: String): List<NarmesteLederRelasjon> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT * from narmeste_leder where bruker_fnr = ?;
        """
        ).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toNarmesteLederRelasjon() }
        }
    }
}

fun DatabaseInterface.getAnsatte(fnr: String): List<NarmesteLederRelasjon> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT * from narmeste_leder where narmeste_leder_fnr = ? and aktiv_tom is null;
        """
        ).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toNarmesteLederRelasjon() }
        }
    }
}

fun DatabaseInterface.finnAlleNarmesteledereForSykmeldt(fnr: String, orgnummer: String): List<NarmesteLederRelasjon> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT * from narmeste_leder where bruker_fnr = ? and orgnummer = ?;
        """
        ).use {
            it.setString(1, fnr)
            it.setString(2, orgnummer)
            it.executeQuery().toList { toNarmesteLederRelasjon() }
        }
    }
}

fun DatabaseInterface.deaktiverNarmesteLeder(narmesteLederId: UUID, aktivTom: OffsetDateTime? = null) {
    connection.use { connection ->
        connection.deaktiverNarmesteLeder(narmesteLederId, aktivTom)
        connection.commit()
    }
}

fun DatabaseInterface.oppdaterNarmesteLeder(narmesteLederId: UUID, nlResponse: NlResponse) {
    connection.use { connection ->
        connection.oppdaterNarmesteLeder(narmesteLederId, nlResponse)
        connection.commit()
    }
}

fun DatabaseInterface.lagreNarmesteLeder(narmesteLederId: UUID, nlResponse: NlResponse, kafkaTimestamp: OffsetDateTime) {
    connection.use { connection ->
        connection.lagreNarmesteleder(narmesteLederId, nlResponse, kafkaTimestamp)
        connection.commit()
    }
}

fun DatabaseInterface.finnForskuttering(fnr: String, orgnummer: String): ForskutteringRespons {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT arbeidsgiver_forskutterer from narmeste_leder where bruker_fnr = ? and orgnummer = ? ORDER BY aktiv_tom DESC NULLS FIRST;
        """
        ).use {
            it.setString(1, fnr)
            it.setString(2, orgnummer)
            it.executeQuery().toForskutteringRespons()
        }
    }
}

private fun Connection.lagreNarmesteleder(narmesteLederId: UUID, nlResponse: NlResponse, kafkaTimestamp: OffsetDateTime) {
    this.prepareStatement(
        """
                INSERT INTO narmeste_leder(
                    narmeste_leder_id,
                    orgnummer,
                    bruker_fnr,
                    narmeste_leder_fnr,
                    narmeste_leder_telefonnummer,
                    narmeste_leder_epost,
                    arbeidsgiver_forskutterer,
                    aktiv_fom,
                    aktiv_tom,
                    timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                 """
    ).use {
        it.setObject(1, narmesteLederId)
        it.setString(2, nlResponse.orgnummer)
        it.setString(3, nlResponse.sykmeldt.fnr)
        it.setString(4, nlResponse.leder.fnr)
        it.setString(5, nlResponse.leder.mobil)
        it.setString(6, nlResponse.leder.epost)
        it.setObject(7, nlResponse.utbetalesLonn)
        it.setTimestamp(
            8,
            nlResponse.aktivFom?.let { Timestamp.from(nlResponse.aktivFom.toInstant()) } ?: Timestamp.from(
                kafkaTimestamp.toInstant()
            )
        )
        it.setObject(9, nlResponse.aktivTom?.let { Timestamp.from(nlResponse.aktivTom.toInstant()) })
        it.setTimestamp(10, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
        it.execute()
    }
}

private fun Connection.deaktiverNarmesteLeder(narmesteLederId: UUID, aktivTom: OffsetDateTime? = null) =
    this.prepareStatement(
        """
            UPDATE narmeste_leder 
                SET aktiv_tom = ?, timestamp = ?
                WHERE narmeste_leder_id = ?;
            """
    ).use {
        it.setTimestamp(
            1,
            aktivTom?.let { Timestamp.from(aktivTom.toInstant()) } ?: Timestamp.from(
                OffsetDateTime.now(ZoneOffset.UTC).toInstant()
            )
        )
        it.setTimestamp(2, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
        it.setObject(3, narmesteLederId)
        it.execute()
    }

private fun Connection.oppdaterNarmesteLeder(narmesteLederId: UUID, nlResponse: NlResponse) =
    this.prepareStatement(
        """
            UPDATE narmeste_leder 
                SET narmeste_leder_telefonnummer = ?, narmeste_leder_epost = ?, arbeidsgiver_forskutterer = ?, aktiv_tom = null, timestamp = ?
                WHERE narmeste_leder_id = ?;
            """
    ).use {
        it.setString(1, nlResponse.leder.mobil)
        it.setString(2, nlResponse.leder.epost)
        it.setObject(3, nlResponse.utbetalesLonn)
        it.setTimestamp(4, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
        it.setObject(5, narmesteLederId)
        it.execute()
    }

private fun ResultSet.toNarmesteLederRelasjon(): NarmesteLederRelasjon =
    NarmesteLederRelasjon(
        narmesteLederId = getObject("narmeste_leder_id", UUID::class.java),
        fnr = getString("bruker_fnr"),
        orgnummer = getString("orgnummer"),
        narmesteLederFnr = getString("narmeste_leder_fnr"),
        narmesteLederTelefonnummer = getString("narmeste_leder_telefonnummer"),
        narmesteLederEpost = getString("narmeste_leder_epost"),
        aktivFom = getTimestamp("aktiv_fom").toInstant().atOffset(ZoneOffset.UTC).toLocalDate(),
        aktivTom = getTimestamp("aktiv_tom")?.toInstant()?.atOffset(ZoneOffset.UTC)?.toLocalDate(),
        arbeidsgiverForskutterer = getObject("arbeidsgiver_forskutterer")?.toString()?.toBoolean(),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC)
    )

private fun ResultSet.toForskutteringRespons(): ForskutteringRespons {
    return when (next()) {
        true -> {
            return when (getObject("arbeidsgiver_forskutterer")?.toString()?.toBoolean()) {
                true -> {
                    ForskutteringRespons(Forskuttering.JA)
                }
                false -> {
                    ForskutteringRespons(Forskuttering.NEI)
                }
                else -> {
                    ForskutteringRespons(Forskuttering.UKJENT)
                }
            }
        }
        false -> ForskutteringRespons(Forskuttering.UKJENT)
    }
}
