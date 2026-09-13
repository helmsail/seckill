# 前置：先在仓库根执行 mvn package -DskipTests（生成可执行 jar）
# 构建：docker build -t seckill-base-server ./seckill-base/seckill-base-server
# 运行：docker run --env-file .env -p 18081:18081 -p 28081:28081 seckill-base-server
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
