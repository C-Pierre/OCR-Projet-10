# Étape 1 : build Angular
FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci --silent

COPY . .
RUN npm run build -- --configuration production

# Étape 2 : servir avec Nginx
FROM nginx:alpine

COPY --from=builder /app/dist/ycyw-chat-frontend/browser /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80