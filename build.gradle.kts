import org.apache.avro.tool.SpecificCompilerTool
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_21


val coroutinesVersion = "1.10.1"
val jacksonVersion = "2.18.3"
val kluentVersion = "1.73"
val ktorVersion = "3.1.1"
val logbackVersion = "1.5.18"
val logstashEncoderVersion = "8.0"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.13.17"
val nimbusdsVersion = "10.0.2"
val testContainerKafkaVersion = "1.20.6"
val postgresVersion = "42.7.5"
val flywayVersion = "11.4.0"
val hikariVersion = "6.2.1"
val testContainerPostgresVersion = "1.20.6"
val swaggerUiVersion = "5.20.1"
val kotlinVersion = "2.1.10"
val confluentVersion = "7.9.0"
val ktfmtVersion = "0.44"
val avroVersion = "1.12.0"
val junitJupiterVersion = "5.12.1"
val kafkaVersion = "3.9.0"


//Due to vulnerabilities
val nettyCommonVersion = "4.1.119.Final"
val snakeYamlVersion = "2.4"

plugins {
    id("application")
    id("com.diffplug.spotless") version "7.0.2"
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow") version "8.3.6"
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
    constraints {
        implementation("io.netty:netty-common:$nettyCommonVersion") {
            because("override transient from io.ktor:ktor-server-netty")
        }
    }
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

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")

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
    testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerPostgresVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}


buildscript {
    dependencies {
        classpath("org.apache.avro:avro-tools:1.12.0")
        classpath("org.apache.avro:avro:1.12.0")
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
