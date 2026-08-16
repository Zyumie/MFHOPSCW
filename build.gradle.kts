import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1" apply false
}

group = "fr.zyumie"
version = "0.5"

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://nexus.hc.to/content/repositories/pub_releases/")
    }
}

val pluginName: Map<String, String> = mapOf(
    "Main"        to "Main",
    "Oxygen"      to "Oxygen",
    "OxygenArmor" to "OxygenArmor",
    "Money"       to "Money",
    "ShopAdmin"   to "ShopAdmin",
    "Hdv"         to "Hdv",
    "Faction"     to "Faction",
    "WeeklyQuest" to "WeeklyQuest",
    "Profil"      to "Profile",
    "Home"        to "Home",
    "Storm"       to "Storm",
    "CustomItems" to "CustomItems",
)

// Modules that need a Vault compile dependency
val vaultModules = setOf("Money", "ShopAdmin", "Hdv", "Faction", "WeeklyQuest", "Profil")

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    // Expand ${version} placeholder inside plugin.yml
    tasks.named<ProcessResources>("processResources") {
        expand("version" to project.version)
        filteringCharset = "UTF-8"
    }

    tasks.named<Jar>("jar") {
        archiveBaseName.set(pluginName[project.name])
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

        when (project.name) {
            "Oxygen" -> compileOnly(project(":Main"))
            "OxygenArmor" -> {
                compileOnly(project(":Main"))
                compileOnly(project(":Oxygen"))
            }
            "Money", "ShopAdmin", "Hdv", "Faction", "WeeklyQuest" -> {
                compileOnly(project(":Main"))
                compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
            }
            "Profil" -> {
                compileOnly(project(":Main"))
                compileOnly(project(":Faction"))
                compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
            }
            "Home" -> compileOnly(project(":Main"))
            "Storm" -> {
                compileOnly(project(":Main"))
                compileOnly("com.google.code.gson:gson:2.11.0") // provided at runtime by Paper
            }
            "CustomItems" -> compileOnly(project(":Main"))
        }
    }
}

// ── Main: shade HikariCP (required at runtime for MySQL, not provided by Paper) ──
project(":Main") {
    apply(plugin = "com.gradleup.shadow")

    dependencies {
        // HikariCP is required at runtime by Main (MySQL) and is NOT provided by Paper -> shade it
        implementation("com.zaxxer:HikariCP:5.1.0")
        // Gson is bundled in Paper at runtime -> compileOnly only
        compileOnly("com.google.code.gson:gson:2.11.0")
    }

    tasks.named<ShadowJar>("shadowJar") {
        archiveBaseName.set("Main")
        archiveVersion.set("")
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "fr.zyumie.libs.hikari")
    }

    tasks.named("assemble") {
        dependsOn(tasks.named("shadowJar"))
    }
}

// ════════════════════════════════════════════════════════════════════════
//  build  → build everything + collect every plugin jar in build/plugins
// ════════════════════════════════════════════════════════════════════════
val distDir = layout.buildDirectory.dir("plugins")

tasks.register<Sync>("collectPlugins") {
    dependsOn(subprojects.map { it.tasks.named("build") }.plus(project(":Main").tasks.named("shadowJar")))
    into(distDir)
    subprojects.forEach { sub ->
        if (sub.name == "Main") {
            from(sub.layout.buildDirectory.dir("libs")) { include("Main.jar") }
        } else {
            from(sub.layout.buildDirectory.dir("libs")) { include("${pluginName[sub.name]}-*.jar") }
        }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("build") {
    dependsOn("collectPlugins")
}