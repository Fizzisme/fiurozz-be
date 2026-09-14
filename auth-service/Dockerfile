FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npx prisma generate
RUN npm run build

FROM node:24-alpine

WORKDIR /app

ENV NODE_ENV=production

RUN addgroup -S nest && adduser -S nest -G nest

COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY --from=build /app/package.json ./package.json

USER nest

EXPOSE 8081

CMD ["node", "dist/src/main.js"]
