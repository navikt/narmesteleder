package no.nav.syfo.orgnummer.db

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.orgnummer.kafka.ArbeidsgiverStatus
import java.sql.Connection
import java.sql.ResultSet

fun DatabaseInterface.saveOrUpdateOrgnummer(orgnummer: String, juridiskOrgnummer: String) {
    connection.use { connection: Connection ->
        connection.prepareStatement(
            """
            insert into orgnummer(orgnummer, juridisk_orgnummer) values (?, ?) 
            on conflict(orgnummer) do update
            set juridisk_orgnummer = ?
        """
        ).use { ps ->
            ps.setString(1, orgnummer)
            ps.setString(2, juridiskOrgnummer)
            ps.setString(3, juridiskOrgnummer)
            ps.execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.saveOrUpdateOrgnummer(arbeidsgiverStatuser: List<ArbeidsgiverStatus>) {
    connection.use { connection: Connection ->
        connection.prepareStatement(
            """
            insert into orgnummer(orgnummer, juridisk_orgnummer) values (?, ?) 
            on conflict(orgnummer) do update
            set juridisk_orgnummer = ?
        """
        ).use { ps ->
            arbeidsgiverStatuser.forEach {
                ps.setString(1, it.orgnummer)
                ps.setString(2, it.juridiskOrgnummer)
                ps.setString(3, it.juridiskOrgnummer)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        connection.commit()
    }
}

fun DatabaseInterface.getJuridiskOrgnummerMap(orgnummer: List<String>): Map<String, String> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
            select orgnummer, juridisk_orgnummer from orgnummer where orgnummer = ANY (?);
        """
        ).use { ps ->
            ps.setArray(1, connection.createArrayOf("VARCHAR", orgnummer.toTypedArray()))
            ps.executeQuery().toOrgnummerMap()
        }
    }
}

private fun ResultSet.toOrgnummerMap(): Map<String, String> {
    return mutableMapOf<String, String>().apply {
        while (next()) {
            this[(getString("orgnummer"))] = getString("juridisk_orgnummer")
        }
    }
}

fun DatabaseInterface.getOrgnummer(juridiskOrgnummer: String): List<String> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
            select orgnummer from orgnummer where juridisk_orgnummer = ?;
        """
        ).use { ps ->
            ps.setString(1, juridiskOrgnummer)
            ps.executeQuery().toOrgnummerList()
        }
    }
}

private fun ResultSet.toOrgnummerList(): List<String> {
    return mutableListOf<String>().apply {
        while (next()) {
            add(getString("orgnummer"))
        }
    }
}

fun DatabaseInterface.getJuridiskOrgnummer(orgnummer: String): String? {
    return connection.use { connection ->
        connection.prepareStatement("select juridisk_orgnummer from orgnummer where orgnummer = ?").use {
            it.setString(1, orgnummer)
            it.executeQuery().toJuridiskOrgnummer()
        }
    }
}

private fun ResultSet.toJuridiskOrgnummer(): String? {
    if (next()) {
        return getString("juridisk_orgnummer")
    }
    return null
}
