# Üretim imajı (ADR-14): SPA, Spring Boot jar'ının içinden servis edilir. Üç aşama:
#   1) node: frontend build  2) JDK + Maven Wrapper: dist/ -> static/ ve jar  3) yalnız JRE 21
# Derleme: docker compose --profile app up -d --build

FROM node:22-slim AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS derleme
WORKDIR /derleme
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# Windows'ta çıkış (checkout) CRLF'li olabilir: bash betiği bozmasın
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /frontend/dist src/main/resources/static
RUN ./mvnw -B -q -Dmaven.test.skip=true package && mv target/makinist-atama-*.jar uygulama.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /uygulama makinist \
    && mkdir -p /uygulama \
    && chown -R makinist:makinist /uygulama
WORKDIR /uygulama
COPY --from=derleme /derleme/uygulama.jar uygulama.jar
USER makinist
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "uygulama.jar"]
