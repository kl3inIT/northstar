plugins {
    id("northstar.spring-library-conventions")
}

dependencies {
    api(project(":core"))
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.50.0@jar")
    implementation("tools.jackson.core:jackson-databind")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
