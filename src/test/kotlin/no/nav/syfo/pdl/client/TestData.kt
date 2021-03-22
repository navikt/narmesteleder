package no.nav.syfo.pdl.client

fun getTestData(): String {
    return "{\n" +
        "  \"data\": {\n" +
        "    \"hentPersonBolk\": [\n" +
        "      {\n" +
        "        \"ident\": \"12345678910\",\n" +
        "        \"person\": {\n" +
        "          \"navn\": [\n" +
        "            {\n" +
        "              \"fornavn\": \"RASK\",\n" +
        "              \"mellomnavn\": null,\n" +
        "              \"etternavn\": \"SAKS\"\n" +
        "            }\n" +
        "          ]\n" +
        "        },\n" +
        "        \"code\": \"ok\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"ident\": \"01987654321\",\n" +
        "        \"person\": {\n" +
        "          \"navn\": [\n" +
        "            {\n" +
        "              \"fornavn\": \"GLAD\",\n" +
        "              \"mellomnavn\": null,\n" +
        "              \"etternavn\": \"BOLLE\"\n" +
        "            }\n" +
        "          ]\n" +
        "        },\n" +
        "        \"code\": \"ok\"\n" +
        "      }\n" +
        "    ]\n" +
        "  }\n" +
        "}"
}

fun getErrorResponse(): String {
    return "{\n" +
        "  \"errors\": [\n" +
        "    {\n" +
        "      \"message\": \"Ikke tilgang til å se person\",\n" +
        "      \"locations\": [\n" +
        "        {\n" +
        "          \"line\": 2,\n" +
        "          \"column\": 3\n" +
        "        }\n" +
        "      ],\n" +
        "      \"path\": [\n" +
        "        \"hentPerson\"\n" +
        "      ],\n" +
        "      \"extensions\": {\n" +
        "        \"code\": \"unauthorized\",\n" +
        "        \"classification\": \"ExecutionAborted\"\n" +
        "      }\n" +
        "    }\n" +
        "  ],\n" +
        "  \"data\": {\n" +
        "    \"hentPerson\": null\n" +
        "  }\n" +
        "}"
}
