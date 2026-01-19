# 国内平台部署指南

## 🇨🇳 国内可访问的免费托管平台对比

### 1. ⭐⭐⭐⭐⭐ Zeabur（最推荐）

**访问速度**: ⭐⭐⭐⭐⭐ 极快  
**免费额度**: 5$/月  
**部署难度**: ⭐ 简单  

[详细部署指南](./DEPLOY-ZEABUR.md)

---

### 2. ⭐⭐⭐⭐⭐ 4everland

**访问速度**: ⭐⭐⭐⭐⭐ 极快  
**免费额度**: 无限流量  
**部署难度**: ⭐ 简单  

#### 特点
- 基于 IPFS 的去中心化托管
- 全球 CDN 加速
- 支持静态网站和 Serverless 函数
- 国内访问速度快

#### 部署步骤

```bash
# 1. 访问 https://4everland.org
# 2. 使用 GitHub 登录
# 3. 导入仓库
# 4. 选择 patch-server/backend
# 5. 配置环境变量
# 6. 部署
```

#### 限制
- 不支持持久化文件存储（需要配合 IPFS）
- 适合静态文件托管

---

### 3. ⭐⭐⭐⭐⭐ 腾讯云 CloudBase

**访问速度**: ⭐⭐⭐⭐⭐ 极快  
**免费额度**: 每月免费额度  
**部署难度**: ⭐⭐ 中等  

#### 免费额度
- 云函数：40万 GBs/月
- 数据库：2GB 存储
- 云存储：5GB
- CDN：5GB/月

#### 部署步骤

```bash
# 1. 安装 CloudBase CLI
npm install -g @cloudbase/cli

# 2. 登录
tcb login

# 3. 初始化项目
tcb init

# 4. 部署
tcb functions:deploy patch-server

# 5. 配置数据库
tcb db:create patches

# 6. 配置存储
tcb storage:create patch-files
```

#### cloudbaserc.json 配置

```json
{
  "envId": "your-env-id",
  "functions": [
    {
      "name": "patch-server",
      "runtime": "Nodejs16.13",
      "handler": "index.main",
      "timeout": 60,
      "envVariables": {
        "JWT_SECRET": "your-secret"
      }
    }
  ],
  "databases": [
    {
      "name": "patches"
    }
  ]
}
```

---

### 4. ⭐⭐⭐⭐⭐ 阿里云函数计算

**访问速度**: ⭐⭐⭐⭐⭐ 极快  
**免费额度**: 100万次调用/月  
**部署难度**: ⭐⭐ 中等  

#### 免费额度
- 函数调用：100万次/月
- 执行时间：40万 CU-秒/月
- OSS 存储：5GB
- CDN：10GB/月

#### 部署步骤

```bash
# 1. 安装 Serverless Devs
npm install -g @serverless-devs/s

# 2. 配置密钥
s config add

# 3. 初始化项目
s init fc-http-nodejs16

# 4. 部署
s deploy
```

#### s.yaml 配置

```yaml
edition: 1.0.0
name: patch-server
access: default

services:
  patch-server:
    component: fc
    props:
      region: cn-hangzhou
      service:
        name: patch-service
      function:
        name: patch-server
        runtime: nodejs16
        codeUri: ./patch-server/backend
        handler: server.handler
        memorySize: 512
        timeout: 60
        environmentVariables:
          JWT_SECRET: your-secret
```

---

### 5. ⭐⭐⭐⭐ Cloudflare Workers + R2

**访问速度**: ⭐⭐⭐⭐ 快  
**免费额度**: 10万次请求/天  
**部署难度**: ⭐⭐⭐ 较难  

#### 免费额度
- Workers：10万次请求/天
- R2 存储：10GB
- D1 数据库：5GB

#### 部署步骤

```bash
# 1. 安装 Wrangler
npm install -g wrangler

# 2. 登录
wrangler login

# 3. 创建项目
wrangler init patch-server

# 4. 配置 wrangler.toml
# 5. 部署
wrangler publish
```

#### wrangler.toml 配置

```toml
name = "patch-server"
main = "src/index.js"
compatibility_date = "2024-01-01"

[[r2_buckets]]
binding = "PATCHES"
bucket_name = "patch-files"

[[d1_databases]]
binding = "DB"
database_name = "patch-db"
database_id = "your-database-id"
```

---

### 6. ⭐⭐⭐ Vercel + 国内 CDN

**访问速度**: ⭐⭐⭐ 中等（需配置 CDN）  
**免费额度**: 100GB/月  
**部署难度**: ⭐ 简单  

#### 优化国内访问

1. **使用 Vercel 部署**
```bash
vercel --prod
```

2. **配置国内 CDN**

使用七牛云、又拍云等国内 CDN 加速：

```javascript
// 在 Vercel 部署后，配置 CDN 回源
// CDN 源站：your-app.vercel.app
// 加速域名：cdn.your-domain.com
```

3. **客户端配置**

```kotlin
// 优先使用 CDN 地址
private const val API_BASE_URL = "https://cdn.your-domain.com"
private const val API_FALLBACK_URL = "https://your-app.vercel.app"
```

---

## 📊 平台对比总结

| 平台 | 国内速度 | 免费额度 | 文件存储 | 数据库 | 推荐度 |
|------|---------|---------|---------|--------|--------|
| Zeabur | ⭐⭐⭐⭐⭐ | 5$/月 | ✅ | ✅ | ⭐⭐⭐⭐⭐ |
| 4everland | ⭐⭐⭐⭐⭐ | 无限 | ✅ IPFS | ❌ | ⭐⭐⭐⭐ |
| 腾讯云 CloudBase | ⭐⭐⭐⭐⭐ | 丰富 | ✅ | ✅ | ⭐⭐⭐⭐⭐ |
| 阿里云函数计算 | ⭐⭐⭐⭐⭐ | 丰富 | ✅ OSS | ✅ | ⭐⭐⭐⭐⭐ |
| Cloudflare | ⭐⭐⭐⭐ | 10万/天 | ✅ R2 | ✅ D1 | ⭐⭐⭐⭐ |
| Vercel + CDN | ⭐⭐⭐ | 100GB/月 | ❌ | ❌ | ⭐⭐⭐ |

## 🎯 选择建议

### 小型项目（< 1000 用户）
**推荐**: Zeabur 或 4everland
- 完全免费
- 部署简单
- 国内访问快

### 中型项目（1000-10000 用户）
**推荐**: 腾讯云 CloudBase 或阿里云函数计算
- 免费额度充足
- 国内访问极快
- 功能完整

### 大型项目（> 10000 用户）
**推荐**: 自建服务器 + CDN
- 完全可控
- 成本可预测
- 性能最优

## 🚀 快速开始

### 1. 选择平台

根据你的需求选择合适的平台。

### 2. 部署服务

按照对应平台的部署指南操作。

### 3. 配置客户端

```kotlin
// UpdateChecker.kt
private const val API_BASE_URL = "https://your-domain.com"
```

### 4. 测试

```bash
# 检查健康状态
curl https://your-domain.com/health

# 测试更新检查
curl "https://your-domain.com/api/client/check-update?version=1.0.0"
```

## 💡 优化建议

### 1. 使用 CDN

所有平台都建议配置 CDN 加速：
- 腾讯云 CDN
- 阿里云 CDN
- 七牛云 CDN
- 又拍云 CDN

### 2. 启用缓存

```javascript
// 补丁文件缓存 7 天
res.setHeader('Cache-Control', 'public, max-age=604800');

// API 响应缓存 5 分钟
res.setHeader('Cache-Control', 'public, max-age=300');
```

### 3. 压缩传输

```javascript
// 已在代码中启用 gzip
app.use(compression());
```

### 4. 数据库优化

```sql
-- 定期清理旧数据
DELETE FROM downloads WHERE created_at < datetime('now', '-30 days');

-- 添加索引
CREATE INDEX idx_patch_version ON patches(version);
CREATE INDEX idx_downloads_created ON downloads(created_at);
```

## 📞 技术支持

- 📖 [完整文档](../README.md)
- 🐛 [报告问题](https://github.com/706412584/Android_hotupdate/issues)
- 💬 [讨论区](https://github.com/706412584/Android_hotupdate/discussions)
