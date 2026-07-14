# ---------- Stage 1: build the Vue frontend ----------
FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# ---------- Stage 2: build the Spring Boot backend ----------
FROM maven:3.9-eclipse-temurin-25 AS backend-build
WORKDIR /backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src

# Embed the compiled Vue app into Spring Boot's static resources
COPY --from=frontend-build /frontend/dist ./src/main/resources/static

RUN mvn -B -q clean package -DskipTests

# ---------- Stage 3: minimal runtime image ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN mkdir -p /data
VOLUME ["/data"]

COPY --from=backend-build /backend/target/toolize.jar /app/toolize.jar

EXPOSE 8080
ENV TOOLIZE_DATA_DIR=/data
# Default admin credentials for the login page - override in production, e.g.:
#   docker run -e TOOLIZE_ADMIN_USERNAME=admin -e TOOLIZE_ADMIN_PASSWORD=change-me ...
ENV TOOLIZE_ADMIN_USERNAME=admin
ENV TOOLIZE_ADMIN_PASSWORD=admin

ENTRYPOINT ["java", "-jar", "/app/toolize.jar"]
