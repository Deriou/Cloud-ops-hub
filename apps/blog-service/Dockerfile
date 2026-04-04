FROM crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/common-core/pom.xml common/common-core/
COPY apps/gateway-portal/pom.xml apps/gateway-portal/
COPY apps/blog-service/pom.xml apps/blog-service/
RUN mvn dependency:go-offline -B -pl apps/blog-service -am

COPY common/common-core/ common/common-core/
COPY apps/blog-service/ apps/blog-service/
RUN mvn package -pl apps/blog-service -am -DskipTests -B

FROM crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/eclipse-temurin:21-jre-jammy
COPY --from=build /workspace/apps/blog-service/target/*.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
