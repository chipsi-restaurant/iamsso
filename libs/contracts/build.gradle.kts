import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    kotlin("jvm")
    id("org.openapi.generator") version "7.20.0"
    `java-library`
    `maven-publish`
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "org.iamsso"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("jakarta.validation:jakarta.validation-api:3.1.1")
    api("jakarta.annotation:jakarta.annotation-api:3.0.0")
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.3")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    api("org.springframework:spring-web")
    api("org.springframework:spring-webflux")




    compileOnly("org.springframework.boot:spring-boot-starter-web")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

data class ApiSpec(
    val name: String,
    val specFile: String,
    val basePackage: String,
    val generateApis: Boolean = true,
)

val apiSpecs = listOf(
    ApiSpec(
        name = "user-service",
        specFile = "$projectDir/openapi/user-service-api.yaml",
        basePackage = "org.iamsso.contracts.user",
    ),
    ApiSpec(
        name = "auth-service",
        specFile = "$projectDir/openapi/auth-service-api.yaml",
        basePackage = "org.iamsso.contracts.auth",
    ),
)

val generatorConfig = mapOf(
    // Только интерфейсы — реализацию пишем сами в сервисах
    "interfaceOnly"         to "true",
    "delegatePattern"       to "false",

    // Jakarta EE (Spring Boot 4 / Spring Framework 7)
    "useJakartaEe"          to "true",
    "jakartaee"             to "true",
    "useSpringBoot3"        to "true",

    // Kotlin / Jackson
    "serializationLibrary"  to "jackson",
    "enumPropertyNaming"    to "UPPERCASE",
    "dateLibrary"           to "java8",

    // Генерировать data class
    "dataClasses"           to "true",

    // Bean Validation (@Valid, @NotNull, @Size ...)
    "useBeanValidation"     to "true",

    // Не генерировать Swagger UI и лишние файлы
    "documentationProvider" to "none",
    "gradleBuildFile"       to "false",

    "useTags" to "true",
)

val typeMap = mapOf(
    "DateTime" to "java.time.Instant",
    "Date"     to "java.time.LocalDate",
)

val importMap = mapOf(
    "Instant"   to "java.time.Instant",
    "LocalDate" to "java.time.LocalDate",
)

val generateTasks = apiSpecs.map { spec ->
    tasks.register<GenerateTask>("generate-${spec.name}") {
        group = "openapi"
        description = "Генерация Kotlin-кода из ${spec.name}"

        inputSpec.set(spec.specFile)
        generatorName.set("kotlin")
        library.set("jvm-spring-restclient")

        outputDir.set("${layout.buildDirectory.get()}/generated/${spec.name}")

        globalProperties.set(buildMap {
            put("models", "")
            if (spec.generateApis) put("apis", "")
            put("supportingFiles", "")
        })

        apiPackage.set("${spec.basePackage}.api")
        modelPackage.set("${spec.basePackage}.model")
        invokerPackage.set(spec.basePackage)

        configOptions.set(
            generatorConfig + mapOf(
                "packageName" to spec.basePackage
            )
        )

        typeMappings.set(typeMap)
        importMappings.set(importMap)
    }
}

val generateAll = tasks.register("generateAllContracts") {
    group = "openapi"
    description = "Генерация кода из всех OpenAPI-спецификаций"
    dependsOn(generateTasks)
}

kotlin {
    sourceSets {
        main {
            apiSpecs.forEach { spec ->
                kotlin.srcDir(
                    "${layout.buildDirectory.get()}/generated/${spec.name}/src/main/kotlin"
                )
            }
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateAll)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "org.iamsso"
            artifactId = "contracts"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("bootJar") {
    enabled = false
}

tasks.named("jar") {
    enabled = true
}