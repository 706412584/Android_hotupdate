# 补丁管理后台前端

基于 Vue 3 + Element Plus 的现代化管理后台。

## 🎨 功能特性

- ✅ 仪表板 - 数据概览和趋势图表
- ✅ 补丁管理 - 列表、编辑、删除
- ✅ 上传补丁 - 拖拽上传、进度显示
- ✅ 统计分析 - 版本分布、设备分布
- ✅ 用户管理 - 用户列表、添加删除
- ✅ 响应式设计 - 适配各种屏幕
- ✅ 权限控制 - 登录认证、路由守卫

## 🚀 快速开始

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

访问：http://localhost:5173

### 生产构建

```bash
npm run build
```

构建产物在 `dist/` 目录。

## 📦 技术栈

- **Vue 3** - 渐进式 JavaScript 框架
- **Vue Router** - 官方路由管理器
- **Element Plus** - Vue 3 组件库
- **Axios** - HTTP 客户端
- **ECharts** - 数据可视化
- **Vite** - 下一代前端构建工具

## 📁 项目结构

```
frontend/
├── src/
│   ├── api/              # API 接口
│   ├── router/           # 路由配置
│   ├── views/            # 页面组件
│   │   ├── Login.vue     # 登录页
│   │   ├── Layout.vue    # 布局组件
│   │   ├── Dashboard.vue # 仪表板
│   │   ├── Patches.vue   # 补丁管理
│   │   ├── Upload.vue    # 上传补丁
│   │   ├── Stats.vue     # 统计分析
│   │   └── Users.vue     # 用户管理
│   ├── App.vue           # 根组件
│   └── main.js           # 入口文件
├── index.html            # HTML 模板
├── vite.config.js        # Vite 配置
└── package.json          # 依赖配置
```

## 🔧 配置

### API 地址

在 `vite.config.js` 中配置后端 API 地址：

```javascript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:3000',
      changeOrigin: true
    }
  }
}
```

### 环境变量

创建 `.env` 文件：

```env
VITE_API_BASE_URL=http://localhost:3000
```

## 📸 截图

### 登录页
- 简洁的登录界面
- 渐变背景设计

### 仪表板
- 数据统计卡片
- 下载趋势图表
- 最新补丁时间线

### 补丁管理
- 补丁列表展示
- 编辑、删除操作
- 灰度发布配置

### 上传补丁
- 拖拽上传
- 实时进度显示
- 表单验证

## 🎯 默认账号

```
用户名: admin
密码: admin123
```

⚠️ 首次登录后请立即修改密码！

## 📝 开发指南

### 添加新页面

1. 在 `src/views/` 创建页面组件
2. 在 `src/router/index.js` 添加路由
3. 在 `Layout.vue` 添加菜单项

### 添加新 API

在 `src/api/index.js` 添加接口：

```javascript
export const api = {
  // 新接口
  getExample: () => request.get('/example')
}
```

### 自定义主题

修改 Element Plus 主题变量：

```css
:root {
  --el-color-primary: #409eff;
  --el-color-success: #67c23a;
  --el-color-warning: #e6a23c;
  --el-color-danger: #f56c6c;
}
```

## 🚀 部署

### Vercel

```bash
npm run build
vercel --prod
```

### Nginx

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    root /path/to/dist;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://localhost:3000;
    }
}
```

## 📞 技术支持

- 📖 [Vue 3 文档](https://vuejs.org/)
- 📖 [Element Plus 文档](https://element-plus.org/)
- 🐛 [报告问题](https://github.com/706412584/Android_hotupdate/issues)
