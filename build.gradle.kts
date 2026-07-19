import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotest)
    alias(libs.plugins.jooq.codegen)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    `jvm-test-suite`
    idea
}

group = "com.example"
version = "0.1.0"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
    // Workaround for KTOR-6802 (see also opentelemetry-java-instrumentation#16430 / #11101): Ktor's
    // SuspendFunctionGun pipeline optimization leaks the coroutine ThreadLocal on the Netty engine,
    // so KtorServerTelemetry drops SERVER spans and grafts independent requests onto one trace.
    // Disabling SFG fixes it with no measurable throughput cost (benchmarked). Remove once KTOR-6802
    // is fixed upstream. Mirrored in the Dockerfile ENTRYPOINT and the test tasks (see below), and
    // guarded by TracingLeakNettyTest.
    applicationDefaultJvmArgs = listOf("-Dio.ktor.internal.disable.sfg=true")
}

// Match the runtime JVM arg above in tests so CI exercises the same configuration and
// TracingLeakNettyTest (which fails if the leak returns) guards the workaround. See application {}.
tasks.withType<Test>().configureEach {
    systemProperty("io.ktor.internal.disable.sfg", "true")
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

// Configure common paths used in source sets and tasks
val generationDir: String = "$projectDir/src/generated"

sourceSets {
    main {
        java {
            srcDir("$generationDir/java")
        }

        kotlin {
            srcDir("$generationDir/kotlin")
        }
    }
}

// Setup configuration types used for dependencies
val fabrikt: Configuration by configurations.creating

dependencies {
    // Gradle plugins
    fabrikt(libs.fabrikt)
    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)
    // Ktor
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)
    // Coroutines
    implementation(libs.bundles.kotlinx.coroutines)
    // Database
    implementation(libs.bundles.jooq)
    implementation(libs.r2dbc.postgresql)
    // NOTE: Pool must come after the postgres driver, otherwise fatJar can break due to ServiceLoader discovery
    // being classpath-order sensitive
    implementation(libs.r2dbc.pool)
    jooqCodegen(libs.postgresql)
    // Redis
    implementation(libs.lettuce.core)
    // Types
    implementation(libs.bundles.kotlin.result)
    implementation(libs.kotlinx.datetime)
    // Validation
    implementation(libs.konform)
    // Monitoring
    implementation(libs.bundles.cohort)
    implementation(libs.micrometer.registry.prometheus)
    // Logging
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)

    // OpenFGA
    implementation(libs.openfga.sdk)

    // Tracing (OpenTelemetry)
    implementation(platform(libs.opentelemetry.instrumentation.bom.alpha))
    implementation(libs.bundles.opentelemetry)

    // Transitive dependency bumps for CVE fixes. These packages are pulled in
    // transitively (Netty via Ktor/Lettuce/R2DBC/OpenFGA, Jackson via the OpenFGA
    // SDK, SCRAM via the Postgres driver) and are not used directly.
    implementation(enforcedPlatform(libs.netty.bom))
    implementation(enforcedPlatform(libs.jackson.bom))
    implementation(enforcedPlatform(libs.opentelemetry.bom))
    constraints {
        // GHSA-p9jg-fcr6-3mhf: SCRAM channel-binding auth downgrade (fixed > 3.2)
        implementation(libs.scram.client) { because("CVE fix GHSA-p9jg-fcr6-3mhf") }
        implementation(libs.scram.common) { because("CVE fix GHSA-p9jg-fcr6-3mhf") }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

testing {
    suites {
        @Suppress("UnstableApiUsage")
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation.bundle(libs.bundles.kotest)
            }
        }

        @Suppress("UnstableApiUsage")
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()

            configurations["integrationTestImplementation"].extendsFrom(configurations.implementation.get())
            configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

            dependencies {
                implementation(project())
                implementation.bundle(libs.bundles.kotest)
                implementation.bundle(libs.bundles.testcontainers)
                implementation(libs.ktor.server.test.host)
                // In-memory span exporter for asserting trace nesting (see TracingIntegrationTest)
                implementation(libs.opentelemetry.sdk.testing)
                // JDBC driver needed for test table truncation in beforeEach
                runtimeOnly(libs.postgresql)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

detekt {
    autoCorrect = project.findProperty("autoCorrect") as String? == "true"
    buildUponDefaultConfig = false
    config.setFrom("$projectDir/config/detekt.yml")
    source.setFrom(
        "src/main/kotlin",
        "src/test/kotlin",
        "src/integrationTest/kotlin",
    )
}

kover {
    currentProject {
        sources {
            excludedSourceSets.add("integrationTest")
        }
    }
    reports {
        filters {
            excludes {
                // Exclude code-generated packages (Fabrikt API + jOOQ DB)
                packages("com.example.generated")
            }
        }
        total {
            html {
                onCheck = true
                title = "Ktor Koin Template Coverage"
            }
            xml {
                onCheck = true
            }
        }
    }
}

idea {
    module {
        // Mark the generated source set as generatedRoot
        generatedSourceDirs.plusAssign(file("$generationDir/java"))
        generatedSourceDirs.plusAssign(file("$generationDir/kotlin"))
        // Mark integration test sources
        testSources.from(sourceSets["integrationTest"].kotlin.srcDirs)
        testResources.from(sourceSets["integrationTest"].resources.srcDirs)
    }
}

tasks {
    val apiFile = "$projectDir/src/main/resources/openapi/todo.yaml"

    val deleteGeneratedApi by registering(Delete::class) {
        group = "Fabrikt"
        delete("$generationDir/kotlin/com/example/generated/api")
    }

    val generateApi by registering(JavaExec::class) {
        group = "Fabrikt"
        description = "Generate code from OpenAPI specification. Run contracts/build.sh first to rebundle contracts/todo/openapi.yaml → src/main/resources/openapi/todo.yaml after spec changes"
        inputs.files(apiFile)
        outputs.dir(generationDir)
        outputs.cacheIf { true }
        classpath(fabrikt)
        mainClass.set("com.cjbooms.fabrikt.cli.CodeGen")
        args = listOf(
            "--output-directory", generationDir,
            "--resources-path", "resources",
            "--src-path", "kotlin",
            "--base-package", "com.example.generated.api",
            "--api-file", apiFile,
            "--targets", "http_models",
            "--targets", "controllers",
            "--http-model-suffix", "Contract",
            "--http-controller-target", "KTOR",
            "--serialization-library", "KOTLINX_SERIALIZATION",
            "--instant-library", "KOTLIN_TIME_INSTANT",
            "--validation-library", "NO_VALIDATION",
        )
        dependsOn(deleteGeneratedApi)
    }

    withType<KotlinCompile> {
        mustRunAfter(generateApi)
    }

    check {
        @Suppress("UnstableApiUsage")
        dependsOn(testing.suites.named("integrationTest"))
    }
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://localhost:5432/todo"
            user = "postgres"
            password = "admin"
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                inputSchema = "public"
                forcedTypes {
                    forcedType {
                        name = "Instant"
                        includeTypes = "TIMESTAMPTZ"
                    }
                }
            }
            target {
                packageName = "com.example.generated.db"
                directory = "$generationDir/kotlin"
            }
        }
    }
}
