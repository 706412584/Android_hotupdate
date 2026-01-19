# 多阶段构建 - 前端
FROM node:18-alpine AS frontend-builder

WORKDIR /app/frontend

# 复制前端 package.json
COPY patch-server/frontend/package*.json ./

# 安装前端依赖
RUN npm install

# 复制前端源代码
COPY patch-server/frontend/ ./

# 构建前端
RUN npm run build

# 多阶段构建 - 后端
FROM node:18-alpine

WORKDIR /app

# 安装 OpenJDK 17、bash 和 wget（用于补丁生成和下载工具）
RUN apk add --no-cache openjdk17 bash wget

# 设置 JAVA_HOME 环境变量
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk
ENV PATH="$JAVA_HOME/bin:$PATH"

# 复制后端 package.json
COPY patch-server/backend/package*.json ./

# 安装后端依赖
RUN npm install --production

# 复制后端源代码
COPY patch-server/backend/ ./

# 创建必要的目录
RUN mkdir -p tools

# 下载 patch-cli 工具
RUN wget -O ./tools/patch-cli.jar https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.3/patch-cli-1.3.3-all.jar

# 从前端构建阶段复制构建产物
COPY --from=frontend-builder /app/frontend/dist ./public

# 创建其他必要的目录
RUN mkdir -p uploads backups

# 暴露端口（Zeabur 会通过环境变量 PORT 指定实际端口）
EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "const port = process.env.PORT || 3000; require('http').get('http://localhost:' + port + '/health', (r) => {process.exit(r.statusCode === 200 ? 0 : 1)})"

# 启动命令
CMD ["node", "server.js"]
