import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
	id("org.jetbrains.kotlin.multiplatform") version "latest.release"
}

group = "io.github.hummel009"
version = LocalDate.now().format(DateTimeFormatter.ofPattern("yy.MM.dd"))

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

kotlin {
	mingwX64 {
		binaries {
			executable {
				entryPoint("io.github.hummel009.winapi.renderer.main")
				linkerOpts("-lwinmm")
				baseName = "${project.name}-${project.version}"
				runTaskProvider?.configure {
					standardInput = System.`in`
				}
			}
		}
	}
	sourceSets {
		configureEach {
			languageSettings {
				optIn("kotlinx.cinterop.ExperimentalForeignApi")
				optIn("kotlin.experimental.ExperimentalNativeApi")
			}
		}
	}
}