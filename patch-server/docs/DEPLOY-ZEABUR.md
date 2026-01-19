# Zeabur 部署指南（国内访问最快）

## 🚀 为什么选择 Zeabur？

- ✅ **国内访问极快** - 有国内 CDN 节点
- ✅ **完全免费** - 每月 5$ 免费额度
- ✅ **一键部署** - 连接 GitHub 自动部署
- ✅ **支持 Docker** - 完美支持我们的项目
- ✅ **自动 HTTPS** - 免费 SSL 证书
- ✅ **中文界面** - 对国内用户友好

## 📦 部署步骤

### 1. 注册 Zeabur

访问：https://zeabur.com

- 使用 GitHub 账号登录
- 完全免费，无需信用卡

### 2. 创建项目

1. 点击 **Create Project**
2. 输入项目名称：`android-hotupdate`
3. 选择区域：**Hong Kong**（国内访问最快）

### 3. 部署服务

#### 方式 1: 从 GitHub 部署（推荐）

1. 点击 **Add Service** → **Git**
2. 选择你的仓库：`Android_hotupdate`
3. Zeabur 会自动检测到 Docker 配置
4. 选择 `patch-server/docker/Dockerfile`
5. 点击 **Deploy**

#### 方式 2: 使用 Zeabur CLI

```bash
# 安装 CLI
npm i -g @zeabur/cli

# 登录
zeabur auth login

# 部署
cd patch-server
zeabur deploy
```

### 4. 配置环境变量

在 Zeabur 控制台：

1. 选择你的服务
2. 点击 **Variables** 标签
3. 添加环境变量：

```
NODE_ENV=production
PORT=3000
JWT_SECRET=your-secret-key-change-this
DB_TYPE=sqlite
DB_PATH=/data/database.db
UPLOAD_DIR=/data/uploads
CORS_ORIGIN=*
```

### 5. 配置持久化存储

Zeabur 支持持久化卷：

1. 点击 **Volumes** 标签
2. 添加卷：
   - 挂载路径：`/data`
   - 大小：1GB（免费）

### 6. 获取访问地址

1. 点击 **Domains** 标签
2. Zeabur 会自动分配域名：`xxx.zeabur.app`
3. 可以绑定自定义域名

### 7. 初始化数据库

```bash
# 使用 Zeabur CLI 执行命令
zeabur exec -- npm run init-db

# 或者通过 API 调用
curl -X POST https://your-app.zeabur.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","role":"admin"}'
```

## 🎯 完整配置文件

创建 `zeabur.json`：

```json
{
  "name": "patch-server",
  "services": [
    {
      "name": "backend",
      "dockerfile": "patch-server/docker/Dockerfile",
      "ports": [3000],
      "env": {
        "NODE_ENV": "production",
        "PORT": "3000"
      },
      "volumes": [
        {
          "name": "data",
          "mountPath": "/data"
        }
      ]
    }
  ]
}
```

## 📊 监控和日志

### 查看日志

在 Zeabur 控制台：
1. 选择服务
2. 点击 **Logs** 标签
3. 实时查看日志

### 性能监控

Zeabur 自动提供：
- CPU 使用率
- 内存使用率
- 网络流量
- 请求统计

## 💰 费用说明

### 免费额度（每月）

- **计算时间**: 5$ 额度
- **流量**: 无限制
- **存储**: 1GB
- **请求数**: 无限制

### 预估使用

对于小型项目（< 1000 用户）：
- 每月费用：**0$**（完全免费）
- 可支持：约 10万次 API 请求
- 存储：1GB（约 1000 个补丁文件）

## 🔧 优化建议

### 1. 启用 CDN

Zeabur 自动提供 CDN 加速，无需额外配置。

### 2. 配置缓存

在代码中添加缓存头：

```javascript
// 补丁文件缓存 7 天
res.setHeader('Cache-Control', 'public, max-age=604800');
```

### 3. 压缩响应

已在代码中启用 gzip 压缩。

### 4. 数据库优化

```javascript
// 定期清理旧数据
DELETE FROM downloads WHERE created_at < datetime('now', '-30 days');
```

## 🌐 自定义域名

### 1. 在 Zeabur 添加域名

1. 点击 **Domains** 标签
2. 点击 **Add Domain**
3. 输入你的域名：`api.your-domain.com`

### 2. 配置 DNS

在你的域名提供商添加 CNAME 记录：

```
类型: CNAME
名称: api
值: your-app.zeabur.app
```

### 3. 等待生效

通常 5-10 分钟生效，Zeabur 会自动配置 SSL 证书。

## 🔒 安全配置

### 1. 限制 CORS

```env
CORS_ORIGIN=https://your-domain.com
```

### 2. 配置 API 密钥

```env
API_KEY=your-secret-api-key
```

### 3. 启用 IP 白名单

在代码中添加：

```javascript
const allowedIPs = ['1.2.3.4', '5.6.7.8'];
app.use((req, res, next) => {
  if (!allowedIPs.includes(req.ip)) {
    return res.status(403).json({ error: 'Forbidden' });
  }
  next();
});
```

## 📱 客户端配置

更新客户端 API 地址：

```kotlin
// UpdateChecker.kt
private const val API_BASE_URL = "https://your-app.zeabur.app"
private const val VERSION_URL = "$API_BASE_URL/api/client/check-update"
```

## 🐛 故障排查

### 部署失败

1. 检查 Dockerfile 路径是否正确
2. 查看构建日志
3. 确认环境变量配置

### 无法访问

1. 检查服务状态（应该是 Running）
2. 查看日志是否有错误
3. 确认端口配置（3000）

### 数据丢失

1. 确认已配置持久化卷
2. 检查卷挂载路径
3. 定期备份数据

## 🔄 更新部署

### 自动部署

推送到 GitHub 后，Zeabur 会自动重新部署：

```bash
git add .
git commit -m "update"
git push
```

### 手动部署

在 Zeabur 控制台点击 **Redeploy**。

## 📞 技术支持

- 📖 [Zeabur 文档](https://zeabur.com/docs)
- 💬 [Zeabur Discord](https://discord.gg/zeabur)
- 🐛 [报告问题](https://github.com/706412584/Android_hotupdate/issues)

## 🎉 部署完成！

现在你的补丁服务已经部署在 Zeabur 上，国内用户可以快速访问！

测试 API：
```bash
curl https://your-app.zeabur.app/health
```

预期响应：
```json
{
  "status": "ok",
  "timestamp": "2025-01-19T10:00:00.000Z",
  "uptime": 123.456
}
```
