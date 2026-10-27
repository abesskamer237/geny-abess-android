FROM node:22-bookworm-slim
ENV NODE_ENV=production
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ ca-certificates && rm -rf /var/lib/apt/lists/*
COPY package*.json ./
RUN npm install --omit=dev
COPY server.js ./
RUN mkdir -p /data && chown -R node:node /app /data
USER node
ENV PORT=8080 DB_PATH=/data/activation.sqlite
EXPOSE 8080
CMD ["node", "server.js"]
