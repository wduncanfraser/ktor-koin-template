ARG GRADLE_VERSION=9.6.1
ARG JAVA_VERSION=25.0.3_9
ARG GRADLE_CONTAINER=gradle:${GRADLE_VERSION}-jdk25-alpine
ARG SERVICE_USER=template

# Stage 1: Cache Gradle dependencies
FROM ${GRADLE_CONTAINER} AS cache
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home
COPY build.gradle.* gradle.properties gradle.lockfile /home/gradle/app/
COPY gradle /home/gradle/app/gradle
WORKDIR /home/gradle/app
RUN gradle clean build -i --stacktrace

# Stage 2: Build Application
FROM ${GRADLE_CONTAINER} AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon

# Stage 3: Create the Runtime Image
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime
ARG SERVICE_USER
RUN addgroup --system ${SERVICE_USER} && \
  adduser --system --shell /bin/false -G ${SERVICE_USER} ${SERVICE_USER}
RUN mkdir /app
USER ${SERVICE_USER}
COPY --from=build /home/gradle/src/build/libs/*.jar /app/ktor-koin-template.jar
# TODO: Track Netty changes to support Java 25 native access https://github.com/netty/netty/issues/15404
# -Dio.ktor.internal.disable.sfg=true: workaround for KTOR-6802 (Ktor SuspendFunctionGun leaks the
# coroutine ThreadLocal on Netty, breaking OpenTelemetry server-span tracing). Mirrors the app JVM
# arg in build.gradle.kts. Remove once KTOR-6802 is fixed upstream.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-Dio.ktor.internal.disable.sfg=true", "-jar","/app/ktor-koin-template.jar"]
EXPOSE 8080
