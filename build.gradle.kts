import org.apache.avro.tool.SpecificCompilerTool
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_21


val coroutinesVersion = "1.10.2"
val jacksonVersion = "2.20.2"
val kluentVersion = "1.73"
val ktorVersion = "3.4.0"
val logbackVersion = "1.5.26"
val logstashEncoderVersion = "8.1"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.14.5"
val nimbusdsVersion = "10.4"
val postgresVersion = "42.7.7"
val flywayVersion = "11.10.3"
val hikariVersion = "6.3.1"
val testcontainerVersion = "2.0.3"
val swaggerUiVersion = "5.26.2"
val kotlinVersion = "2.2.0"
val confluentVersion = "8.1.1"
val ktfmtVersion = "0.44"
val avroVersion = "1.12.1"
val junitJupiterVersion = "5.13.4"
val kafkaVersion = "3.9.1"


//Due to vulnerabilities
val snakeYamlVersion = "2.4"

plugins {
    id("application")
    id("com.diffplug.spotless") version "7.2.1"
    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "8.3.8"
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
}

application {
    mainClass.set("no.nav.syfo.BootstrapKt")
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    constraints {
        implementation("org.apache.avro:avro:$avroVersion") {
            because("override transient from io.confluent:kafka-avro-serializer")
        }
    }

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    constraints {
        implementation("org.yaml:snakeyaml:$snakeYamlVersion") {
            because("due to https://github.com/advisories/GHSA-mjmj-j48q-9wg2")
        }
    }

    swaggerUI("org.webjars:swagger-ui:$swaggerUiVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("org.testcontainers:testcontainers-kafka:$testcontainerVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainerVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}


buildscript {
    dependencies {
        classpath("org.apache.avro:avro-tools:1.12.1")
        classpath("org.apache.avro:avro:1.12.1")
    }
}

val avroSchemasDir = "src/main/avro"
val avroCodeGenerationDir = "build/generated-main-avro-custom-java"


sourceSets {
    main {
        java {
            srcDir( file(File(avroCodeGenerationDir)))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = javaVersion
    }
}

tasks {

    register("customAvroCodeGeneration") {
        inputs.dir(avroSchemasDir)
        outputs.dir(avroCodeGenerationDir)

        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.ERROR)

        doLast {
            SpecificCompilerTool().run(
                System.`in`, System.out, System.err,
                listOf(
                    "-encoding",
                    "UTF-8",
                    "-string",
                    "-fieldVisibility",
                    "private",
                    "-noSetters",
                    "schema",
                    "$projectDir/$avroSchemasDir",
                    "$projectDir/$avroCodeGenerationDir",
                ),
            )
        }
    }

    compileKotlin {
        dependsOn("customAvroCodeGeneration")
    }
    compileTestKotlin {
        dependsOn("customAvroCodeGeneration")
    }

    shadowJar {
        mergeServiceFiles {
            setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
        }
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.BootstrapKt",
                ),
            )
        }
        dependsOn("customAvroCodeGeneration")
    }

    test {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        dependsOn("customAvroCodeGeneration")
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
            dependsOn("customAvroCodeGeneration")
        }
    }
}
