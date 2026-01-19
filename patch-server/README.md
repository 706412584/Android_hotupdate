# 🚀 Android 热更新补丁管理系统

完整的补丁管理服务端，支持补丁上传、版本管理、灰度发布、统计分析等功能。提供现代化的 Web 管理后台，让补丁管理变得简单高效。

## ✨ 核心功能

### 📦 补丁管理
- **补丁上传** - 支持拖拽上传，自动 MD5 校验
- **自动生成** - 集成 patch-cli，一键生成差分补丁
- **版本控制** - 完整的版本管理和回滚机制
- **状态管理** - 测试中/已发布/已停用三种状态
- **批量操作** - 批量启用、停用、删除补丁

### 🎯 灰度发布
- **百分比控制** - 支持 0-100% 灰度发布
- **设备 ID 哈希** - 基于设备 ID 的稳定灰度策略
- **强制更新** - 支持强制更新标记
- **实时调整** - 随时调整灰度比例

### 🔐 安全功能
- **补丁签名** - 支持 JKS/BKS 签名验证
- **补丁加密** - AES-256 加密保护
- **JWT 认证** - 安全的身份验证机制
- **权限控制** - 管理员/普通用户角色管理
- **频率限制** - API 访问频率限制

### 📊 统计分析
- **仪表板** - 应用总数、补丁总数、下载量、成功率
- **下载趋势** - 7/30/90 天趋势图表
- **版本分布** - 饼图展示版本占比
- **设备分布** - 柱状图展示设备型号分布
- **热门应用** - Top 5 应用排行
- **最近活动** - 实时操作日志

### 👥 用户管理
- **多用户支持** - 支持多个开发者账号
- **权限分级** - 管理员/普通用户权限
- **应用审核** - 管理员审核应用创建（可选）
- **用户封禁** - 封禁/解封用户及其应用

### 🔔 通知系统
- **站内通知** - 应用审核、补丁更新通知
- **实时更新** - 每 30 秒自动刷新
- **消息管理** - 标记已读、删除、清空

### 🔍 全局搜索
- **快速搜索** - 搜索应用、补丁、用户
- **关键词高亮** - 搜索结果高亮显示
- **权限过滤** - 根据用户权限过滤结果

### ⚙️ 系统管理
- **定时任务** - 自动备份、日志清理、记录清理
- **数据备份** - 一键备份数据库和文件
- **备份恢复** - 快速恢复历史备份
- **操作日志** - 完整的操作审计日志

## 🛠️ 技术栈

### 后端
- **Node.js + Express** - RESTful API 服务
- **SQLite** - 轻量级数据库（可切换 MySQL）
- **JWT** - 身份认证
- **Multer** - 文件上传处理

### 前端
- **Vue 3** - 渐进式 JavaScript 框架
- **Element Plus** - 企业级 UI 组件库
- **ECharts** - 数据可视化图表
- **Vite** - 快速构建工具

## 📦 快速开始

### 环境要求

- **Node.js** 16+
- **npm** 或 **yarn**

### 本地开发

#### 1. 克隆项目

```bash
git clone https://github.com/706412584/Android_hotupdate.git
cd Android_hotupdate/patch-server
```

#### 2. 启动后端

```bash
cd backend

# 安装依赖
npm install

# 配置环境变量（可选）
cp .env.example .env
# 编辑 .env 文件，配置数据库、JWT 密钥等

# 启动开发服务器
npm run dev
```

后端服务运行在 `http://localhost:3000`

#### 3. 启动前端

```bash
cd ../frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务运行在 `http://localhost:5173`

#### 4. 访问管理后台

打开浏览器访问 `http://localhost:5173`

**默认管理员账号**：
- 用户名：`admin`
- 密码：`admin123`

### 生产部署

#### 方式一：Docker 部署（推荐）

```bash
# 构建并启动
docker-compose up -d

# 访问
# 管理后台: http://localhost:8080
# API: http://localhost:3000
```

#### 方式二：手动部署

##### 后端部署

```bash
cd backend

# 安装依赖
npm install --production

# 配置环境变量
cp .env.example .env
# 编辑 .env，设置生产环境配置

# 启动服务（使用 PM2）
npm install -g pm2
pm2 start server.js --name patch-server

# 查看日志
pm2 logs patch-server
```

##### 前端部署

```bash
cd frontend

# 安装依赖
npm install

# 构建生产版本
npm run build

# 部署 dist 目录到 Web 服务器（Nginx/Apache）
```

##### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    # 补丁下载
    location /downloads {
        proxy_pass http://localhost:3000;
    }
}
```

#### 方式三：云平台部署

##### Vercel（前端）

```bash
# 安装 Vercel CLI
npm i -g vercel

# 部署前端
cd frontend
vercel
```

##### Railway/Render（后端）

1. 连接 GitHub 仓库
2. 选择 `patch-server/backend` 目录
3. 设置环境变量
4. 自动部署

## 🔧 配置说明

### 环境变量

创建 `backend/.env` 文件：

```env
# 服务器配置
PORT=3000
NODE_ENV=production

# 数据库配置
DB_TYPE=sqlite
DB_PATH=./database.db

# JWT 配置
JWT_SECRET=your-secret-key-change-this
JWT_EXPIRES_IN=7d

# 文件存储
UPLOAD_DIR=./uploads
MAX_FILE_SIZE=100

# CORS 配置
CORS_ORIGIN=*

# patch-cli 路径（可选，用于自动生成补丁）
PATCH_CLI_JAR=/path/to/patch-cli-all.jar
```

### 数据库配置

#### SQLite（默认）
```env
DB_TYPE=sqlite
DB_PATH=./database.db
```

#### MySQL
```env
DB_TYPE=mysql
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=password
DB_NAME=patch_server
```

## 📱 客户端集成

### 检查更新

```http
GET /api/client/check-update?appId=your-app-id&version=1.0.0&deviceId=xxx

Response:
{
  "hasUpdate": true,
  "patch": {
    "version": "1.0.1",
    "downloadUrl": "http://your-domain.com/downloads/patch-xxx.zip",
    "md5": "abc123...",
    "size": 1024000,
    "description": "修复说明",
    "forceUpdate": false
  }
}
```

### 下载补丁

```http
GET /api/client/download/:patchId
```

### 上报下载结果

```http
POST /api/client/report
Content-Type: application/json

{
  "patchId": "xxx",
  "deviceId": "xxx",
  "success": true,
  "errorMessage": ""
}
```

## 📖 API 文档

### 认证接口

#### 登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

#### 注册
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "user",
  "password": "password",
  "email": "user@example.com"
}
```

### 应用管理

#### 创建应用
```http
POST /api/apps
Authorization: Bearer <token>
Content-Type: application/json

{
  "appName": "我的应用",
  "packageName": "com.example.app",
  "description": "应用描述"
}
```

#### 获取应用列表
```http
GET /api/apps
Authorization: Bearer <token>
```

### 补丁管理

#### 上传补丁
```http
POST /api/patches/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
  "file": <patch.zip>,
  "appId": 1,
  "version": "1.0.1",
  "baseVersion": "1.0.0",
  "description": "修复说明"
}
```

#### 获取补丁列表
```http
GET /api/patches?page=1&limit=10
Authorization: Bearer <token>
```

#### 更新补丁状态
```http
PUT /api/patches/:id/status
Authorization: Bearer <token>
Content-Type: application/json

{
  "status": "active"  // active/inactive/testing
}
```

## 🔒 安全建议

### 生产环境配置

1. **修改默认密码** - 首次登录后立即修改 admin 密码
2. **设置强密钥** - 修改 JWT_SECRET 为复杂随机字符串
3. **启用 HTTPS** - 使用 SSL 证书加密传输
4. **配置 CORS** - 限制允许的域名
5. **定期备份** - 启用自动备份功能
6. **监控日志** - 定期查看操作日志

### 文件安全

- 补丁文件存储在 `backend/uploads` 目录
- 建议配置文件访问权限
- 定期清理过期补丁文件

## 📊 系统要求

### 最低配置
- **CPU**: 1 核
- **内存**: 512MB
- **磁盘**: 10GB
- **适用**: < 100 用户

### 推荐配置
- **CPU**: 2 核
- **内存**: 2GB
- **磁盘**: 50GB
- **适用**: 100-1000 用户

### 高性能配置
- **CPU**: 4 核+
- **内存**: 4GB+
- **磁盘**: 100GB+
- **适用**: > 1000 用户

## 🐛 故障排查

### 后端无法启动

1. 检查端口是否被占用：`lsof -i :3000`
2. 检查 Node.js 版本：`node -v`（需要 16+）
3. 检查环境变量配置
4. 查看日志：`npm run dev`

### 前端无法访问

1. 检查前端服务是否启动
2. 检查浏览器控制台错误
3. 检查 API 代理配置（vite.config.js）
4. 清除浏览器缓存

### 数据库错误

1. 检查数据库文件权限
2. 检查数据库连接配置
3. 重新初始化数据库：`npm run init-db`

### 文件上传失败

1. 检查 uploads 目录权限
2. 检查文件大小限制（MAX_FILE_SIZE）
3. 检查磁盘空间

## 📞 技术支持

- **GitHub Issues**: [报告问题](https://github.com/706412584/Android_hotupdate/issues)
- **GitHub Discussions**: [讨论区](https://github.com/706412584/Android_hotupdate/discussions)
- **文档**: 查看 `docs/` 目录下的详细文档

## 📄 许可证

MIT License

---

**开发者**: Android 热更新团队  
**版本**: 1.0.0  
**最后更新**: 2026-01-19
