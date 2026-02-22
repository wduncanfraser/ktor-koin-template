import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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
}

repositories {
    mavenCentral()
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
    implementation(libs.hikari)
    implementation(libs.bundles.jooq)
    libs.postgresql
        .also(::runtimeOnly)
        .also(::jooqCodegen)
    // Redis
    implementation(libs.lettuce.core)
    // Types
    implementation(libs.bundles.kotlin.result)
    implementation(libs.kotlinx.datetime)
    // Monitoring
    implementation(libs.bundles.cohort)
    implementation(libs.micrometer.registry.prometheus)
    // Logging
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    // Tests
    testImplementation(libs.bundles.kotest)
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
        delete("$generationDir/kotlin/com/example/generate/api")
    }

    val generateApi by registering(JavaExec::class) {
        group = "Fabrikt"
        description = "Generate code from OpenAPI specification"
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

    // Fix issues arising form lettuce and ktor both including netty service files
    withType<ShadowJar> {
        mergeServiceFiles()
        // Also handle Netty's native lib index files specifically
        append("META-INF/io.netty.versions.properties")
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
