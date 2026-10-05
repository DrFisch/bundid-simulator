FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

FROM eclipse-temurin:25-jre
WORKDIR /app
# Fork bpsim/standardkonform (siehe FORK.md): eigener Benutzer statt root,
# Datenverzeichnis fuer den Signaturschluessel, Entrypoint erzeugt bei Bedarf einen Test-Schluessel.
RUN useradd --system --uid 10001 --home-dir /app simulator \
    && mkdir -p /app/data && chown simulator /app/data
COPY --from=build /app/target/bundid-simulator.jar .
COPY src/main/docker/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
USER 10001
ENTRYPOINT ["sh", "/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "bundid-simulator.jar"]
