# syntax=docker/dockerfile:1

# Stage 1: build the app using an image that has Node.js.
FROM node:22-alpine AS build
# Work inside /app.
WORKDIR /app
# Copy the package list first, so installed packages are cached between builds.
COPY package.json package-lock.json ./
# Install exactly the package versions in package-lock.json.
RUN npm ci --no-audit --no-fund
# Copy the rest of the source code.
COPY . .
# Build the finished HTML, JS and CSS into /app/dist.
RUN npm run build

# Stage 2: a small web server that serves the built files and forwards /api to the backend.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine
# Our web server settings template; at start-up nginx fills in the ports and writes conf.d/default.conf.
# 755 (not 644) because the same mode is given to the new templates folder, which must be openable.
COPY --chmod=755 nginx.conf.template /etc/nginx/templates/default.conf.template
# Copy the built app from stage 1.
COPY --from=build /app/dist /usr/share/nginx/html
# The web server listens on FRONTEND_PORT (set in docker-compose.yml).
EXPOSE 3000
