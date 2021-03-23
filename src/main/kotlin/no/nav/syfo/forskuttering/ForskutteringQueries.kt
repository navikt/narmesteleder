package no.nav.syfo.forskuttering

import no.nav.syfo.application.db.DatabaseInterface
import java.sql.ResultSet

fun DatabaseInterface.finnForskuttering(fnr: String, orgnummer: String): ForskutteringRespons {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT arbeidsgiver_forskutterer from narmeste_leder where bruker_fnr = ? and orgnummer = ?
        """
        ).use {
            it.setString(1, fnr)
            it.setString(2, orgnummer)
            it.executeQuery().toForskutteringRespons()
        }
    }
}

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
