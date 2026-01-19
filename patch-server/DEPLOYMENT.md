# 部署指南

## Zeabur 部署配置

### 数据持久化设置

为了防止每次重新部署时数据丢失，需要配置持久化存储。

#### 方法 1：使用 Zeabur Volumes（推荐）

1. 进入 Zeabur 项目设置
2. 找到你的服务
3. 点击"Volumes"或"存储"选项
4. 添加以下挂载点：

| 挂载路径 | 说明 | 大小建议 |
|---------|------|---------|
| `/app/data` | 数据库文件目录 | 500MB |
| `/app/uploads` | 补丁文件存储 | 5GB+ |
| `/app/backups` | 数据库备份 | 1GB |

5. 保存配置并重新部署

#### 方法 2：使用环境变量配置外部存储

在 Zeabur 环境变量中配置：

```bash
# 数据库路径（可以指向持久化卷）
DB_PATH=/data/database.db

# 上传目录
UPLOAD_DIR=/data/uploads

# 备份目录
BACKUP_DIR=/data/backups
```

然后在 Volumes 中挂载 `/data` 目录。

### 环境变量配置

必需的环境变量：

```bash
# JWT 密钥（必须设置，用于用户认证）
JWT_SECRET=your-secret-key-here

# 管理员账号（首次部署时创建）
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your-secure-password
ADMIN_EMAIL=admin@example.com

# Node 环境
NODE_ENV=production
```

可选的环境变量：

```bash
# 端口（Zeabur 会自动设置）
PORT=3000

# CORS 配置
CORS_ORIGIN=*

# 文件上传大小限制（字节）
MAX_FILE_SIZE=104857600

# 数据库路径
DB_PATH=/app/data/database.db

# 上传目录
UPLOAD_DIR=/app/uploads

# 备份目录
BACKUP_DIR=/app/backups
```

### 部署流程

1. **首次部署**
   - 配置环境变量
   - 设置持久化存储
   - 推送代码到 `server` 分支
   - Zeabur 自动构建和部署

2. **更新部署**
   - 修改代码后推送到 `server` 分支
   - Zeabur 自动重新构建
   - 数据会保留在持久化卷中

3. **数据备份**
   - 系统每天凌晨 2 点自动备份
   - 备份文件保存在 `/app/backups` 目录
   - 可以在管理后台的"系统管理"中手动备份和恢复

### 注意事项

⚠️ **重要**：
- 首次部署前必须配置持久化存储，否则数据会在重新部署时丢失
- JWT_SECRET 必须设置为强密码，不要使用默认值
- 定期下载备份文件到本地，防止数据丢失
- 生产环境建议使用外部数据库（如 PostgreSQL）而不是 SQLite

### 迁移到外部数据库（可选）

如果需要更好的性能和可靠性，可以迁移到 PostgreSQL：

1. 在 Zeabur 创建 PostgreSQL 服务
2. 修改代码使用 PostgreSQL 驱动
3. 运行数据迁移脚本
4. 更新环境变量

详细步骤请参考 [数据库迁移指南](./DATABASE_MIGRATION.md)

## 其他平台部署

### Docker Compose

```yaml
version: '3.8'
services:
  patch-server:
    build: .
    ports:
      - "3000:3000"
    environment:
      - JWT_SECRET=your-secret-key
      - ADMIN_USERNAME=admin
      - ADMIN_PASSWORD=admin123
      - NODE_ENV=production
    volumes:
      - ./data:/app/data
      - ./uploads:/app/uploads
      - ./backups:/app/backups
    restart: unless-stopped
```

### Kubernetes

参考 `k8s/` 目录下的配置文件。

## 故障排查

### 数据丢失问题

如果发现数据在重新部署后丢失：

1. 检查 Volumes 配置是否正确
2. 确认挂载路径与代码中的路径一致
3. 查看容器日志确认数据库文件位置
4. 从备份恢复数据

### 性能问题

如果遇到性能问题：

1. 增加持久化卷的 IOPS
2. 考虑迁移到外部数据库
3. 启用 CDN 加速补丁文件下载
4. 优化数据库索引

## 监控和维护

### 健康检查

服务提供健康检查端点：`GET /health`

### 日志查看

在 Zeabur 控制台查看实时日志，或使用：

```bash
zeabur logs <service-name>
```

### 数据库维护

定期执行：
- 清理旧日志（系统自动，每周日凌晨 3 点）
- 清理旧下载记录（系统自动，每月 1 号凌晨 4 点）
- 数据库备份（系统自动，每天凌晨 2 点）
