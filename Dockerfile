ARG GRADLE_VERSION=8.14.3
ARG JAVA_VERSION=21.0.7_6
ARG GRADLE_CONTAINER=gradle:${GRADLE_VERSION}-jdk21-alpine
ARG SERVICE_USER=template

# Stage 1: Cache Gradle dependencies
FROM ${GRADLE_CONTAINER} AS cache
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home
COPY build.gradle.* gradle.properties /home/gradle/app/
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
ENTRYPOINT ["java","-jar","/app/ktor-koin-template.jar"]
EXPOSE 8080
