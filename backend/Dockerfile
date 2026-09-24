# 多阶段构建：第一阶段用 Maven 打包，第二阶段用 JRE 运行
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
# BuildKit 缓存 Maven 仓库；直接打包只解析实际需要的插件，避免 go-offline 拉取整套站点插件。
RUN --mount=type=cache,target=/root/.m2 mvn package -Dmaven.test.skip=true -B

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/target/movie-seckill-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
