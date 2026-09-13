# 前置：先在仓库根执行 mvn package -DskipTests（生成可执行 jar）
# 构建：docker build -t seckill-job ./seckill-job
# 运行：docker run --env-file .env -p 18083:18083 -p 19999:19999 seckill-job
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
