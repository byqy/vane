# 应用镜像：多阶段构建，运行镜像只含 JRE 与 jar
# 构建上下文必须是仓库根（需要 vane-server/ 与 db/migration/）

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY vane-server/ ./vane-server/
COPY db/ ./db/

RUN cd vane-server && chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd -r vane && useradd -r -g vane -d /app -s /sbin/nologin vane \
    && mkdir -p /data/files && chown -R vane:vane /app /data/files

COPY --from=build /workspace/vane-server/vane-bootstrap/target/vane-bootstrap-*.jar app.jar

USER vane
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]
