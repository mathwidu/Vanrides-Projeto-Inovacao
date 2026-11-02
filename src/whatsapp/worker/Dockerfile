ARG NODE_IMAGE=node:22.23.2-bookworm-slim@sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9
FROM ${NODE_IMAGE}
ENV NODE_ENV=production
WORKDIR /app/worker
COPY --chown=node:node worker/ /app/worker/
COPY --chown=node:node transport/ /app/transport/
COPY --chown=node:node agent/ /app/agent/
USER node
EXPOSE 7080
CMD ["node", "main.mjs"]
