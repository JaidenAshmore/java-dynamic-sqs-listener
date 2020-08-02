
description = "Spring Starter for automatically setting up the Spring Core implementation in a Spring Boot Application"

val springBootVersion: String by project

dependencies {
    api(project(":java-dynamic-sqs-listener-spring-core"))

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-starter-aop")
}
