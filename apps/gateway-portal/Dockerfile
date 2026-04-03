FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/common-core/pom.xml common/common-core/
COPY apps/gateway-portal/pom.xml apps/gateway-portal/
COPY apps/blog-service/pom.xml apps/blog-service/
RUN mvn dependency:go-offline -B -pl apps/gateway-portal -am

COPY common/common-core/ common/common-core/
COPY apps/gateway-portal/ apps/gateway-portal/
RUN mvn package -pl apps/gateway-portal -am -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
COPY --from=build /workspace/apps/gateway-portal/target/*.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
