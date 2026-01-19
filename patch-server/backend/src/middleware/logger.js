const db = require('../models/database');

/**
 * 记录操作日志
 */
async function logAction(user, action, resourceType, resourceId, details, req) {
  try {
    const ipAddress = req.ip || req.connection.remoteAddress;
    const userAgent = req.get('user-agent');
    
    await db.run(`
      INSERT INTO logs (
        user_id, username, action, resource_type, resource_id,
        details, ip_address, user_agent
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      user?.id || null,
      user?.username || 'anonymous',
      action,
      resourceType || null,
      resourceId || null,
      JSON.stringify(details) || null,
      ipAddress,
      userAgent
    ]);
  } catch (error) {
    console.error('记录日志失败:', error);
  }
}

/**
 * 日志中间件 - 自动记录关键操作
 */
function loggerMiddleware(req, res, next) {
  // 保存原始的 json 方法
  const originalJson = res.json.bind(res);
  
  // 重写 json 方法以记录成功的操作
  res.json = function(data) {
    // 只记录成功的修改操作
    if (res.statusCode >= 200 && res.statusCode < 300) {
      const method = req.method;
      const path = req.path;
      
      // 判断是否需要记录
      let action = null;
      let resourceType = null;
      let resourceId = null;
      
      // 应用相关操作
      if (method === 'POST' && path === '/api/apps') {
        action = 'create_app';
        resourceType = 'app';
        resourceId = data.app?.id;
      } else if (method === 'PUT' && path.match(/^\/api\/apps\/\d+$/)) {
        action = 'update_app';
        resourceType = 'app';
        resourceId = path.split('/')[3];
      } else if (method === 'PUT' && path.match(/^\/api\/apps\/\d+\/status$/)) {
        action = 'toggle_app_status';
        resourceType = 'app';
        resourceId = path.split('/')[3];
      } else if (method === 'DELETE' && path.match(/^\/api\/apps\/\d+$/)) {
        action = 'delete_app';
        resourceType = 'app';
        resourceId = path.split('/')[3];
      } 
      // 补丁相关操作
      else if (method === 'POST' && path === '/api/patches/upload') {
        action = 'upload_patch';
        resourceType = 'patch';
        resourceId = data.patch?.id;
      } else if (method === 'POST' && path === '/api/generate/patch') {
        action = 'generate_patch';
        resourceType = 'patch';
        resourceId = data.patch?.id;
      } else if (method === 'DELETE' && path.match(/^\/api\/patches\/\d+$/)) {
        action = 'delete_patch';
        resourceType = 'patch';
        resourceId = path.split('/')[3];
      } else if (method === 'PUT' && path.match(/^\/api\/patches\/\d+$/)) {
        action = 'update_patch';
        resourceType = 'patch';
        resourceId = path.split('/')[3];
      } else if (method === 'PUT' && path.match(/^\/api\/patches\/\d+\/status$/)) {
        action = 'toggle_patch_status';
        resourceType = 'patch';
        resourceId = path.split('/')[3];
      }
      // 用户相关操作
      else if (method === 'POST' && path === '/api/auth/register') {
        action = 'register_user';
        resourceType = 'user';
        resourceId = data.user?.id;
      } else if (method === 'POST' && path === '/api/auth/login') {
        action = 'login';
        resourceType = 'user';
        resourceId = data.user?.id;
      } else if (method === 'PUT' && path.match(/^\/api\/users\/\d+$/)) {
        action = 'update_user';
        resourceType = 'user';
        resourceId = path.split('/')[3];
      } else if (method === 'DELETE' && path.match(/^\/api\/users\/\d+$/)) {
        action = 'delete_user';
        resourceType = 'user';
        resourceId = path.split('/')[3];
      } else if (method === 'PUT' && path.match(/^\/api\/users\/\d+\/role$/)) {
        action = 'change_user_role';
        resourceType = 'user';
        resourceId = path.split('/')[3];
      }
      // 应用审核操作
      else if (method === 'POST' && path.match(/^\/api\/apps\/\d+\/review$/)) {
        action = 'review_app';
        resourceType = 'app';
        resourceId = path.split('/')[3];
      }
      // 系统配置操作
      else if (method === 'PUT' && path === '/api/system-config') {
        action = 'update_system_config';
        resourceType = 'system';
        resourceId = null;
      }
      
      // 记录日志
      if (action && req.user) {
        // 异步记录日志，不阻塞响应
        setImmediate(() => {
          logAction(req.user, action, resourceType, resourceId, {
            method,
            path,
            body: req.body,
            message: data.message
          }, req).catch(err => {
            console.error('记录日志失败:', err);
          });
        });
      }
    }
    
    return originalJson(data);
  };
  
  next();
}

module.exports = { logAction, loggerMiddleware };
