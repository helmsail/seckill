# 前置：先在仓库根执行 mvn package -DskipTests（生成可执行 jar）
# 构建：docker build -t seckill-admin ./seckill-admin
# 运行：docker run --env-file .env -p 18082:18082 seckill-admin
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 18082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
