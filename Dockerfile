FROM openjdk:8-alpine AS build

RUN apk add git openjdk7

RUN mkdir ~/.gradle
RUN echo jdk7=/usr/lib/jvm/java-1.7-openjdk >> ~/.gradle/gradle.properties
RUN echo jdk8=/usr/lib/jvm/java-1.8-openjdk >> ~/.gradle/gradle.properties

RUN git clone https://github.com/newrelic/newrelic-java-agent
WORKDIR /newrelic-java-agent
RUN git checkout v6.1.0

COPY ./src instrumentation/netty-4.0.8/src/main/java/
RUN ./gradlew clean jar --parallel

FROM alpine
COPY --from=build /newrelic-java-agent/newrelic-agent/build/newrelicJar/newrelic.jar .
