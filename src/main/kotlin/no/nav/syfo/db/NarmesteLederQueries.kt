package no.nav.syfo.db

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.db.toList
import no.nav.syfo.forskuttering.Forskuttering
import no.nav.syfo.forskuttering.ForskutteringRespons
import no.nav.syfo.narmesteleder.NarmesteLederRelasjon
import java.sql.ResultSet
import java.time.ZoneOffset

fun DatabaseInterface.finnAktiveNarmestelederkoblinger(narmesteLederFnr: String): List<NarmesteLederRelasjon> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT * from narmeste_leder where narmeste_leder_fnr = ? and aktiv_tom is null
        """
        ).use {
            it.setString(1, narmesteLederFnr)
            it.executeQuery().toList { toNarmesteLederRelasjon() }
        }
    }
}

fun DatabaseInterface.finnNarmestelederForSykmeldt(fnr: String, orgnummer: String): NarmesteLederRelasjon? {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT * from narmeste_leder where bruker_fnr = ? and orgnummer = ? and aktiv_tom is null
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
           SELECT * from narmeste_leder where bruker_fnr = ?
        """
        ).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toNarmesteLederRelasjon() }
        }
    }
}

fun DatabaseInterface.finnForskuttering(fnr: String, orgnummer: String): ForskutteringRespons {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT arbeidsgiver_forskutterer from narmeste_leder where bruker_fnr = ? and orgnummer = ? and aktiv_tom is null
        """
        ).use {
            it.setString(1, fnr)
            it.setString(2, orgnummer)
            it.executeQuery().toForskutteringRespons()
        }
    }
}

private fun ResultSet.toNarmesteLederRelasjon(): NarmesteLederRelasjon =
    NarmesteLederRelasjon(
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
