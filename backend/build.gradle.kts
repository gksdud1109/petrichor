plugins {
	kotlin("jvm") version "2.1.21"
	kotlin("plugin.spring") version "2.1.21"
	kotlin("plugin.jpa") version "2.1.21"
	id("org.springframework.boot") version "3.4.13"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.petrichor"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Web / API
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	// 관측성
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	// 영속성 (Postgres + Flyway)
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	// 캐시 / 큐 (Redis)
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// 오브젝트 스토리지 (S3/MinIO/R2). AWS SDK v2는 Spring Boot BOM이 관리하지 않으므로
	// 자체 BOM으로 버전 정렬(2.x 최신, Maven Central 확인). s3 + presigner 포함.
	implementation(platform("software.amazon.awssdk:bom:2.46.17"))
	implementation("software.amazon.awssdk:s3")
	// 레이트리밋 (in-memory per-IP token bucket; Redis 분산은 향후)
	implementation("com.bucket4j:bucket4j-core:8.10.1")
	// 버킷 맵 TTL/최대크기 관리 (Spring Boot BOM 버전 관리)
	implementation("com.github.ben-manes.caffeine:caffeine")
	// 보안 (인증 로직은 Phase 2; Phase 0/1은 SecurityConfig로 permitAll)
	implementation("org.springframework.boot:spring-boot-starter-security")
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
