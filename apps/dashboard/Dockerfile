FROM node:20-alpine AS build
WORKDIR /app

# Vite inlines VITE_* vars at build time, so they must be present before `npm run build`
ARG VITE_TANSO_BACKEND_URL
ARG VITE_TANSO_SANDBOX_BACKEND_URL
ARG VITE_ENVIRONMENT
ENV VITE_TANSO_BACKEND_URL=$VITE_TANSO_BACKEND_URL
ENV VITE_TANSO_SANDBOX_BACKEND_URL=$VITE_TANSO_SANDBOX_BACKEND_URL
ENV VITE_ENVIRONMENT=$VITE_ENVIRONMENT

COPY package.json package-lock.json ./
RUN npm ci --silent

COPY . .
RUN npm run build

FROM node:20-alpine
RUN adduser -D -u 1001 app
WORKDIR /app

RUN npm install -g serve@14

COPY --from=build /app/dist ./dist

USER app
EXPOSE 3000

CMD ["serve", "-s", "dist", "-l", "3000"]
