ARG NODE_IMAGE=node:22.23.2-bookworm-slim@sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9
FROM $NODE_IMAGE
ENV NODE_ENV=production
WORKDIR /app/live-ingress
COPY --chown=node:node config.mjs hml-config.mjs minute-limit.mjs policy.mjs post.mjs server.mjs strict-json.mjs main.mjs ./
USER node
EXPOSE 7180
CMD ["node", "--max-old-space-size=64", "main.mjs"]
