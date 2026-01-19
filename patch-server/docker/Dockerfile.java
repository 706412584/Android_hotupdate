# 支持 Java 的 Dockerfile（用于自动生成补丁）
FROM node:18-alpine

# 安装 OpenJDK 11
RUN apk add --no-cache openjdk11-jre

# 设置工作目录
WORKDIR /app

# 复制 package.json
COPY backend/package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制后端代码
COPY backend/ ./

# 复制 patch-cli JAR
COPY patch-cli/build/libs/patch-cli-1.3.2-all.jar /app/patch-cli.jar

# 创建数据目录
RUN mkdir -p /data/uploads

# 设置环境变量
ENV NODE_ENV=production
ENV PORT=3000
ENV UPLOAD_DIR=/data/uploads
ENV PATCH_CLI_JAR=/app/patch-cli.jar
ENV JAVA_PATH=java

# 暴露端口
EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health', (r) => {process.exit(r.statusCode === 200 ? 0 : 1)})"

# 启动应用
CMD ["node", "server.js"]
