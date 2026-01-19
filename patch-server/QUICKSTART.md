# 🚀 快速开始 - 自托管服务端

## 📦 方案对比

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **GitHub Releases** | 完全免费、零配置、CDN 加速 | 功能简单、无统计 | 个人项目、开源项目 |
| **自托管服务端** | 功能完整、数据自主、可定制 | 需要服务器、维护成本 | 企业项目、商业应用 |

## 🎯 5 分钟快速部署

### 方式 1: Docker（最简单）

```bash
# 1. 克隆仓库
git clone https://github.com/706412584/Android_hotupdate.git
cd Android_hotupdate/patch-server

# 2. 启动服务
cd docker
docker-compose up -d

# 3. 初始化数据库
docker-compose exec backend npm run init-db

# 完成！
# API: http://localhost:3000
# 管理后台: http://localhost:8080
# 默认账号: admin / admin123
```

### 方式 2: 本地开发

```bash
# 1. 安装依赖
cd patch-server/backend
npm install

# 2. 配置环境
cp .env.example .env

# 3. 初始化数据库
npm run init-db

# 4. 启动服务
npm run dev

# 完成！
# API: http://localhost:3000
```

## 📱 客户端集成

### 1. 修改 UpdateChecker.kt

```kotlin
// 将 GitHub API 改为自己的服务端
class UpdateChecker(private val context: Context) {
    companion object {
        // 修改为你的服务端地址
        private const val API_BASE_URL = "http://your-domain.com/api"
    }
    
    suspend fun checkUpdate(currentVersion: String): UpdateResult {
        val url = "$API_BASE_URL/client/check-update?version=$currentVersion"
        // ... 其他代码保持不变
    }
}
```

### 2. 测试

```kotlin
// 检查更新
val checker = UpdateChecker(context)
val result = checker.checkUpdate("1.4.0")

when (result) {
    is UpdateResult.HasUpdate -> {
        // 下载补丁
        val downloadResult = checker.downloadPatch(result.patchInfo) { progress ->
            println("下载进度: $progress%")
        }
    }
    // ...
}
```

## 🔧 管理补丁

### 使用 API

```bash
# 1. 登录获取 token
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 2. 上传补丁
curl -X POST http://localhost:3000/api/patches/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@patch-v1.4.1.zip" \
  -F "version=1.4.1" \
  -F "baseVersion=1.4.0" \
  -F "description=修复 SIGBUS 崩溃"

# 3. 查看补丁列表
curl http://localhost:3000/api/patches \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 使用 Postman

1. 导入 API 集合（见 `docs/postman_collection.json`）
2. 设置环境变量 `base_url` 和 `token`
3. 开始测试

## 📊 功能演示

### 1. 补丁管理
- ✅ 上传补丁文件
- ✅ 查看补丁列表
- ✅ 编辑补丁信息
- ✅ 删除补丁
- ✅ 启用/禁用补丁

### 2. 灰度发布
```bash
# 设置灰度发布（只对 50% 用户推送）
curl -X PUT http://localhost:3000/api/patches/1 \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"rolloutPercentage":50}'
```

### 3. 统计分析
```bash
# 获取概览统计
curl http://localhost:3000/api/stats/overview \
  -H "Authorization: Bearer YOUR_TOKEN"

# 获取下载趋势
curl http://localhost:3000/api/stats/downloads-trend?days=7 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 🌐 部署到生产环境

### 推荐配置

**小型项目（< 1000 用户）**
- Railway/Render 免费版
- SQLite 数据库
- 成本：$0

**中型项目（1000-10000 用户）**
- VPS (2核4G)
- MySQL 数据库
- 成本：$5-10/月

**大型项目（> 10000 用户）**
- 负载均衡 + 多实例
- MySQL 集群
- Redis 缓存
- CDN 加速
- 成本：$50+/月

### 一键部署

#### Railway
```bash
# 1. 访问 https://railway.app
# 2. 连接 GitHub 仓库
# 3. 选择 patch-server/backend
# 4. 自动部署
```

#### Render
```bash
# 1. 访问 https://render.com
# 2. 创建 Web Service
# 3. 连接 GitHub 仓库
# 4. 配置环境变量
# 5. 部署
```

## 🔒 安全配置

### 1. 修改默认密码

```bash
# 首次登录后立即修改
POST /api/auth/change-password
{
  "oldPassword": "admin123",
  "newPassword": "your-secure-password"
}
```

### 2. 配置 HTTPS

```bash
# 使用 Let's Encrypt
sudo certbot --nginx -d your-domain.com
```

### 3. 配置防火墙

```bash
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

## 📈 监控和维护

### 健康检查

```bash
curl http://your-domain.com/health
```

### 查看日志

```bash
# Docker
docker-compose logs -f backend

# PM2
pm2 logs patch-server
```

### 备份数据

```bash
# 备份数据库
cp database.db database.db.backup

# 备份上传文件
tar -czf uploads-backup.tar.gz uploads/
```

## 🎉 完成！

现在你有了一个功能完整的补丁服务端：

- ✅ RESTful API
- ✅ 补丁管理
- ✅ 版本控制
- ✅ 灰度发布
- ✅ 统计分析
- ✅ 用户权限
- ✅ Docker 部署

## 📞 需要帮助？

- 📖 [完整文档](./README.md)
- 🚀 [部署指南](./docs/DEPLOY.md)
- 🐛 [报告问题](https://github.com/706412584/Android_hotupdate/issues)
- 💬 [讨论区](https://github.com/706412584/Android_hotupdate/discussions)

## 🔄 方案切换

如果你之前使用 GitHub Releases，可以轻松切换到自托管：

```kotlin
// 只需修改 API 地址
// 从：
private const val VERSION_URL = "https://raw.githubusercontent.com/.../version.json"

// 改为：
private const val API_BASE_URL = "http://your-domain.com/api"
```

所有其他代码保持不变！
