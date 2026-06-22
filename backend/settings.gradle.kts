plugins {
	// Gradle toolchain이 요구하는 JDK(21)를 로컬/CI에서 자동 프로비저닝.
	// 설치 JDK가 17이어도 빌드가 'No compatible toolchains'로 막히지 않게 함.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
