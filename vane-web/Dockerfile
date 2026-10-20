# 前端镜像：构建 admin 与 portal，产物由 Nginx 托管并反代后端

FROM node:26-alpine AS build
WORKDIR /workspace

RUN corepack enable

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY packages ./packages
COPY apps ./apps

RUN pnpm install --frozen-lockfile && pnpm -r build

FROM nginx:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/portal/dist /usr/share/nginx/html
COPY --from=build /workspace/apps/admin/dist /usr/share/nginx/html/admin

EXPOSE 80
