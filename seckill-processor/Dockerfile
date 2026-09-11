# 前置：先在仓库根执行 mvn package -DskipTests（生成可执行 jar）
# 构建：docker build -t seckill-processor ./seckill-processor
# 运行：docker run --env-file .env -p 18085:18085 seckill-processor
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 18085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
