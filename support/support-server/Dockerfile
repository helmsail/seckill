# 前置：先在仓库根执行 mvn package -DskipTests（生成可执行 jar）
# 构建：docker build -t support-server ./support/support-server
# 运行：docker run --env-file .env -p 18086:18086 -p 28080:28080 support-server
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
