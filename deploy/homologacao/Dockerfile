# Runtime only: prepare.py supplies the already tested backend.jar.
ARG JAVA_IMAGE
FROM ${JAVA_IMAGE}
USER 0
WORKDIR /app
ARG SOURCE_SHA
ARG SOURCE_TREE
ARG JAR_SHA256
LABEL org.opencontainers.image.source="https://github.com/mathwidu/Vanrides-Projeto-Inovacao" \
      org.opencontainers.image.revision="${SOURCE_SHA}" \
      dev.vanrides.source-tree="${SOURCE_TREE}" \
      dev.vanrides.jar-sha256="${JAR_SHA256}" \
      dev.vanrides.environment="vanrides-team-hml"
COPY backend.jar /app/app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN test -n "${SOURCE_SHA}" && test -n "${SOURCE_TREE}" && test -n "${JAR_SHA256}" \
    && printf '%s  /app/app.jar\n' "${JAR_SHA256}" | sha256sum -c - \
    && chmod 0444 /app/app.jar && chmod 0555 /app/entrypoint.sh
ENV VANRIDES_JAR_SHA256=${JAR_SHA256}
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
