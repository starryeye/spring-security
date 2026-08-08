# 세 서비스가 전부 Spring Boot fat jar 이라 모양이 같다. jar 경로만 ARG 로 받는다.
FROM eclipse-temurin:21-jre
ARG JAR
COPY ${JAR} /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
