const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
const path = require('path');
const { loggerMiddleware } = require('./src/middleware/logger');
require('dotenv').config();

const app = express();
// 调试：打印所有端口相关的环境变量
console.log('🔍 环境变量调试:');
console.log('  WEB_PORT:', process.env.WEB_PORT);
console.log('  PORT:', process.env.PORT);
console.log('  所有环境变量:', Object.keys(process.env).filter(k => k.includes('PORT')));

const PORT = process.env.WEB_PORT || process.env.PORT || 3000;
console.log('✅ 最终使用端口:', PORT);

// 中间件
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN || '*',
  credentials: true
}));
app.use(compression());
app.use(morgan('combined'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 频率限制
const limiter = rateLimit({
  windowMs: 60 * 1000, // 1 分钟
  max: 60, // 最多 60 次请求
  message: '请求过于频繁，请稍后再试'
});
app.use('/api/', limiter);

// 日志中间件
app.use(loggerMiddleware);

// 静态文件服务（补丁下载）
app.use('/downloads', express.static(path.join(__dirname, 'uploads')));

// 路由
app.use('/api/auth', require('./src/routes/auth'));
app.use('/api/apps', require('./src/routes/apps'));
app.use('/api/patches', require('./src/routes/patches'));
app.use('/api/generate', require('./src/routes/generate'));
app.use('/api/client', require('./src/routes/client'));
app.use('/api/stats', require('./src/routes/stats'));
app.use('/api/users', require('./src/routes/users'));
app.use('/api/logs', require('./src/routes/logs'));
app.use('/api/encryption', require('./src/routes/encryption'));
app.use('/api/scheduler', require('./src/routes/scheduler'));
app.use('/api/notifications', require('./src/routes/notifications'));
app.use('/api/system-config', require('./src/routes/system-config'));
app.use('/api/search', require('./src/routes/search'));

// 健康检查
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// 404 处理
app.use((req, res) => {
  res.status(404).json({
    error: 'Not Found',
    message: `路径 ${req.path} 不存在`
  });
});

// 错误处理
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(err.status || 500).json({
    error: err.message || 'Internal Server Error',
    ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
  });
});

// 启动服务器
const server = app.listen(PORT, '0.0.0.0', () => {
  const address = server.address();
  console.log(`🚀 补丁服务端运行在 http://0.0.0.0:${address.port}`);
  console.log(`📊 环境: ${process.env.NODE_ENV || 'development'}`);
  console.log(`📁 上传目录: ${process.env.UPLOAD_DIR || './uploads'}`);
  console.log(`🔌 实际监听端口: ${address.port}`);
  console.log(`🌐 监听地址: ${address.address}`);
  
  // 初始化定时任务
  const { initScheduler } = require('./src/utils/scheduler');
  initScheduler();
});

// 优雅关闭
process.on('SIGTERM', () => {
  console.log('收到 SIGTERM 信号，正在关闭服务器...');
  server.close(() => {
    console.log('服务器已关闭');
    const { scheduler } = require('./src/utils/scheduler');
    scheduler.stopAll();
    process.exit(0);
  });
});
