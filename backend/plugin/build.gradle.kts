/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Test Postgres container port/DB name: interpolated into docker-compose-base-test.yml and docker-compose-override.yml
// via the `environment` map below, and into application.yml via the test task's system properties. This only takes
// effect when running tests through Gradle (./gradlew test) - running docker-compose directly, or a test from the
// IDE without going through this task, falls back to the `:`/`:-` default literals baked into those three files,
// which must be updated by hand if the values below ever change.
val testDbPort = "54342"
val testDbName = "documenten-api-wopi-plugin-test"

dockerCompose {
    setProjectName("documenten-api-wopi-plugin")
    isRequiredBy(project.tasks.test)
    environment.put("TEST_DB_PORT", testDbPort)
    environment.put("TEST_DB_NAME", testDbName)

    tasks.test {
        useComposeFiles.addAll("$rootDir/docker-resources/docker-compose-base-test.yml", "docker-compose-override.yml")
        systemProperty("TEST_DB_PORT", testDbPort)
        systemProperty("TEST_DB_NAME", testDbName)
    }
}

val kotlinLoggingVersion: String by project
val mockitoKotlinVersion: String by project
val valtimoVersion: String by project
val operatonVersion: String by project
val okhttpVersion: String by project

dependencies {
    compileOnly("com.ritense.valtimo:authorization")
    compileOnly("com.ritense.valtimo:plugin-valtimo")
    compileOnly("com.ritense.valtimo:process-document")
    compileOnly("com.ritense.valtimo:catalogi-api")
    compileOnly("com.ritense.valtimo:contract")
    compileOnly("com.ritense.valtimo:documenten-api")
    compileOnly("com.ritense.valtimo:zgw")
    compileOnly("com.ritense.valtimo:logging")
    compileOnly("org.operaton.bpm:operaton-engine:$operatonVersion")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

    compileOnly("io.github.oshai:kotlin-logging:$kotlinLoggingVersion")

    // Testing
    testImplementation("com.ritense.valtimo:authorization")
    testImplementation("com.ritense.valtimo:building-block")
    testImplementation("com.ritense.valtimo:catalogi-api")
    testImplementation("com.ritense.valtimo:documenten-api")
    testImplementation("com.ritense.valtimo:local-resource")
    testImplementation("com.ritense.valtimo:plugin-valtimo")
    testImplementation("com.ritense.valtimo:test-utils-common")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")

    testImplementation("org.postgresql:postgresql")

    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

apply(from = "gradle/publishing.gradle")
