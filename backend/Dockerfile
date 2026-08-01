FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/podcast-collab-1.0.0.jar app.jar

RUN mkdir -p /data/storage/audio /data/storage/waveforms

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
