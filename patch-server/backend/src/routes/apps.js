const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const db = require('../models/database');
const jwt = require('jsonwebtoken');
const { createNotification, notifyAdmins } = require('./notifications');

// 配置 keystore 文件上传
const keystoreStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = process.env.UPLOAD_DIR || './uploads';
    const keystoreDir = path.join(uploadDir, 'keystores');
    if (!fs.existsSync(keystoreDir)) {
      fs.mkdirSync(keystoreDir, { recursive: true });
    }
    cb(null, keystoreDir);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'keystore-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const keystoreUpload = multer({
  storage: keystoreStorage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB
  },
  fileFilter: (req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    if (ext === '.jks' || ext === '.keystore' || ext === '.bks') {
      cb(null, true);
    } else {
      cb(new Error('只支持 .jks、.keystore 或 .bks 文件'));
    }
  }
});

// 认证中间件
const auth = (req, res, next) => {
  try {
    const token = req.headers.authorization?.replace('Bearer ', '');
    if (!token) {
      return res.status(401).json({ error: '未授权' });
    }
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = decoded;
    next();
  } catch (error) {
    res.status(401).json({ error: '无效的 token' });
  }
};

// 获取用户的应用列表
router.get('/', auth, async (req, res) => {
  try {
    const { user } = req;
    
    // 所有用户（包括管理员）只看到自己的应用
    const apps = await db.query(`
      SELECT a.*, u.username as owner_name,
        (SELECT COUNT(*) FROM patches WHERE app_id = a.id) as patch_count
      FROM apps a
      LEFT JOIN users u ON a.owner_id = u.id
      WHERE a.owner_id = ?
      ORDER BY a.created_at DESC
    `, [user.id]);

    res.json(apps);
  } catch (error) {
    console.error('获取应用列表失败:', error);
    res.status(500).json({ error: '获取应用列表失败' });
  }
});

// 获取所有应用列表（管理员专用，带筛选和排序）
router.get('/admin/all', auth, async (req, res) => {
  try {
    const { user } = req;
    
    if (user.role !== 'admin') {
      return res.status(403).json({ error: '需要管理员权限' });
    }
    
    const {
      page = 1,
      limit = 20,
      app_name,
      package_name,
      owner_name,
      status,
      review_status,
      sort_by = 'created_at_desc'
    } = req.query;
    
    // 构建查询条件
    let whereConditions = [];
    let params = [];
    
    if (app_name) {
      whereConditions.push('a.app_name LIKE ?');
      params.push(`%${app_name}%`);
    }
    
    if (package_name) {
      whereConditions.push('a.package_name LIKE ?');
      params.push(`%${package_name}%`);
    }
    
    if (owner_name) {
      whereConditions.push('u.username LIKE ?');
      params.push(`%${owner_name}%`);
    }
    
    if (status) {
      whereConditions.push('a.status = ?');
      params.push(status);
    }
    
    if (review_status) {
      whereConditions.push('a.review_status = ?');
      params.push(review_status);
    }
    
    const whereClause = whereConditions.length > 0 
      ? 'WHERE ' + whereConditions.join(' AND ')
      : '';
    
    // 构建排序
    let orderBy = 'a.created_at DESC';
    switch (sort_by) {
      case 'created_at_asc':
        orderBy = 'a.created_at ASC';
        break;
      case 'app_name_asc':
        orderBy = 'a.app_name ASC';
        break;
      case 'app_name_desc':
        orderBy = 'a.app_name DESC';
        break;
      case 'patch_count_desc':
        orderBy = 'patch_count DESC';
        break;
      default:
        orderBy = 'a.created_at DESC';
    }
    
    // 获取总数
    const countQuery = `
      SELECT COUNT(*) as total
      FROM apps a
      LEFT JOIN users u ON a.owner_id = u.id
      ${whereClause}
    `;
    const countResult = await db.get(countQuery, params);
    const total = countResult.total;
    
    // 获取分页数据
    const offset = (page - 1) * limit;
    const dataQuery = `
      SELECT a.*, u.username as owner_name,
        (SELECT COUNT(*) FROM patches WHERE app_id = a.id) as patch_count
      FROM apps a
      LEFT JOIN users u ON a.owner_id = u.id
      ${whereClause}
      ORDER BY ${orderBy}
      LIMIT ? OFFSET ?
    `;
    const apps = await db.query(dataQuery, [...params, parseInt(limit), offset]);
    
    res.json({
      apps,
      pagination: {
        page: parseInt(page),
        limit: parseInt(limit),
        total
      }
    });
  } catch (error) {
    console.error('获取所有应用列表失败:', error);
    res.status(500).json({ error: '获取所有应用列表失败' });
  }
});

// 获取待审核应用列表（管理员）- 必须在 /:id 之前定义
router.get('/pending-review', auth, async (req, res) => {
  try {
    const { user } = req;
    
    if (user.role !== 'admin') {
      return res.status(403).json({ error: '需要管理员权限' });
    }
    
    const apps = await db.query(`
      SELECT a.*, u.username as owner_name
      FROM apps a
      LEFT JOIN users u ON a.owner_id = u.id
      WHERE a.review_status = 'pending'
      ORDER BY a.created_at DESC
    `);
    
    res.json(apps);
  } catch (error) {
    console.error('获取待审核应用失败:', error);
    res.status(500).json({ error: '获取待审核应用失败' });
  }
});

// 获取单个应用详情
router.get('/:id', auth, async (req, res) => {
  try {
    const { id } = req.params;
    const { user } = req;

    const app = await db.get(`
      SELECT a.*, u.username as owner_name
      FROM apps a
      LEFT JOIN users u ON a.owner_id = u.id
      WHERE a.id = ?
    `, [id]);

    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && app.owner_id !== user.id) {
      return res.status(403).json({ error: '无权访问此应用' });
    }

    // 普通用户不能访问待审核或被拒绝的应用
    if (user.role !== 'admin' && app.review_status !== 'approved') {
      return res.status(403).json({ 
        error: app.review_status === 'pending' ? '应用正在审核中' : '应用已被拒绝'
      });
    }

    // 普通用户不能访问已停用的应用
    if (user.role !== 'admin' && app.status === 'inactive') {
      return res.status(403).json({ error: '应用已停用' });
    }

    // 获取应用的补丁列表
    const patches = await db.query(`
      SELECT * FROM patches
      WHERE app_id = ?
      ORDER BY created_at DESC
    `, [id]);

    res.json({ ...app, patches });
  } catch (error) {
    console.error('获取应用详情失败:', error);
    res.status(500).json({ error: '获取应用详情失败' });
  }
});

// 创建应用
router.post('/', auth, async (req, res) => {
  try {
    const { app_name, package_name, description, icon } = req.body;
    const { user } = req;

    if (!app_name || !package_name) {
      return res.status(400).json({ error: 'app_name 和 package_name 不能为空' });
    }

    // 生成唯一的 app_id
    // 格式: app_时间戳_随机数
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8);
    const app_id = `app_${timestamp}_${random}`;

    // 检查是否启用审核
    const reviewConfig = await db.get(
      'SELECT value FROM system_config WHERE key = ?',
      ['app_review_enabled']
    );
    
    const autoApproveAdmin = await db.get(
      'SELECT value FROM system_config WHERE key = ?',
      ['auto_approve_admin']
    );
    
    const reviewEnabled = reviewConfig && reviewConfig.value === 'true';
    const autoApprove = autoApproveAdmin && autoApproveAdmin.value === 'true';
    
    // 确定审核状态
    let reviewStatus = 'approved'; // 默认通过
    if (reviewEnabled) {
      if (user.role === 'admin' && autoApprove) {
        reviewStatus = 'approved'; // 管理员自动通过
      } else if (user.role !== 'admin') {
        reviewStatus = 'pending'; // 普通用户需要审核
      }
    }

    // 创建应用
    const result = await db.run(`
      INSERT INTO apps (app_id, app_name, package_name, description, icon, owner_id, status, review_status)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `, [app_id, app_name, package_name, description, icon, user.id, 'active', reviewStatus]);

    // 发送通知
    if (reviewStatus === 'pending') {
      // 通知管理员有新应用待审核
      await notifyAdmins(
        'app_review',
        '新应用待审核',
        `用户 ${user.username} 提交了应用 "${app_name}" 待审核`,
        '/system',
        { app_id: result.id, app_name }
      );
      
      // 通知用户应用已提交
      await createNotification(
        user.id,
        'app_submitted',
        '应用已提交审核',
        `您的应用 "${app_name}" 已提交，请等待管理员审核`,
        `/apps/${result.id}`,
        { app_id: result.id }
      );
    } else {
      // 通知用户应用创建成功
      await createNotification(
        user.id,
        'app_created',
        '应用创建成功',
        `您的应用 "${app_name}" 已创建成功`,
        `/apps/${result.id}`,
        { app_id: result.id }
      );
    }

    res.json({
      message: reviewStatus === 'pending' ? '应用已提交，等待审核' : '应用创建成功',
      app: {
        id: result.id,
        app_id,
        app_name,
        package_name,
        description,
        icon,
        owner_id: user.id,
        review_status: reviewStatus
      }
    });
  } catch (error) {
    console.error('创建应用失败:', error);
    res.status(500).json({ error: '创建应用失败' });
  }
});

// 更新应用
router.put('/:id', auth, async (req, res) => {
  try {
    const { id } = req.params;
    const { 
      app_name, 
      package_name, 
      description, 
      icon, 
      status,
      require_signature,
      require_encryption,
      keystore_password,
      key_alias,
      key_password
    } = req.body;
    const { user } = req;

    // 检查应用是否存在
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [id]);
    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && app.owner_id !== user.id) {
      return res.status(403).json({ error: '无权修改此应用' });
    }

    // 更新应用
    await db.run(`
      UPDATE apps
      SET app_name = ?, 
          package_name = ?, 
          description = ?, 
          icon = ?, 
          status = ?,
          require_signature = ?,
          require_encryption = ?,
          keystore_path = ?,
          keystore_password = ?,
          key_alias = ?,
          key_password = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
    `, [
      app_name, 
      package_name, 
      description, 
      icon, 
      status,
      require_signature ? 1 : 0,
      require_encryption ? 1 : 0,
      req.body.keystore_path || app.keystore_path,
      keystore_password,
      key_alias,
      key_password,
      id
    ]);

    res.json({ message: '应用更新成功' });
  } catch (error) {
    console.error('更新应用失败:', error);
    res.status(500).json({ error: '更新应用失败' });
  }
});

// 审核应用（管理员）- 必须在 /:id/status 之前定义
router.post('/:id/review', auth, async (req, res) => {
  try {
    const { id } = req.params;
    const { action, note } = req.body; // action: 'approve' or 'reject'
    const { user } = req;
    
    if (user.role !== 'admin') {
      return res.status(403).json({ error: '需要管理员权限' });
    }
    
    if (!['approve', 'reject'].includes(action)) {
      return res.status(400).json({ error: '无效的审核操作' });
    }
    
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [id]);
    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }
    
    const reviewStatus = action === 'approve' ? 'approved' : 'rejected';
    const appStatus = action === 'approve' ? 'active' : 'inactive';
    
    await db.run(`
      UPDATE apps 
      SET review_status = ?,
          status = ?,
          review_note = ?,
          reviewed_at = CURRENT_TIMESTAMP,
          reviewed_by = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
    `, [reviewStatus, appStatus, note || null, user.id, id]);
    
    // 通知应用所有者审核结果
    if (action === 'approve') {
      await createNotification(
        app.owner_id,
        'app_approved',
        '应用审核通过',
        `您的应用 "${app.app_name}" 已通过审核`,
        `/apps/${id}`,
        { app_id: id, app_name: app.app_name }
      );
    } else {
      await createNotification(
        app.owner_id,
        'app_rejected',
        '应用审核未通过',
        `您的应用 "${app.app_name}" 未通过审核${note ? '：' + note : ''}`,
        `/apps/${id}`,
        { app_id: id, app_name: app.app_name, note }
      );
    }
    
    res.json({ 
      message: action === 'approve' ? '应用已通过审核' : '应用已拒绝',
      review_status: reviewStatus
    });
  } catch (error) {
    console.error('审核应用失败:', error);
    res.status(500).json({ error: '审核应用失败' });
  }
});

// 封禁/解封单个应用（管理员）
router.put('/:id/status', auth, async (req, res) => {
  try {
    const { id } = req.params;
    const { status } = req.body;
    const { user } = req;

    if (user.role !== 'admin') {
      return res.status(403).json({ error: '需要管理员权限' });
    }

    if (!['active', 'inactive'].includes(status)) {
      return res.status(400).json({ error: '无效的状态值' });
    }

    const app = await db.get('SELECT * FROM apps WHERE id = ?', [id]);
    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    await db.run(
      'UPDATE apps SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?',
      [status, id]
    );

    res.json({ 
      message: status === 'inactive' ? '应用已封禁' : '应用已解封',
      status 
    });
  } catch (error) {
    console.error('更新应用状态失败:', error);
    res.status(500).json({ error: '更新应用状态失败' });
  }
});

// 删除应用
router.delete('/:id', auth, async (req, res) => {
  try {
    const { id } = req.params;
    const { user } = req;

    // 检查应用是否存在
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [id]);
    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && app.owner_id !== user.id) {
      return res.status(403).json({ error: '无权删除此应用' });
    }

    // 删除应用（注意：这会级联删除相关的补丁）
    await db.run('DELETE FROM apps WHERE id = ?', [id]);

    res.json({ message: '应用删除成功' });
  } catch (error) {
    console.error('删除应用失败:', error);
    res.status(500).json({ error: '删除应用失败' });
  }
});

// 上传 keystore 文件
router.post('/upload-keystore', auth, keystoreUpload.single('keystore'), async (req, res) => {
  try {
    const { app_id } = req.body;
    const { user } = req;

    if (!req.file) {
      return res.status(400).json({ error: '请上传 keystore 文件' });
    }

    if (!app_id) {
      return res.status(400).json({ error: '应用 ID 不能为空' });
    }

    // 检查应用是否存在
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [app_id]);
    if (!app) {
      // 清理上传的文件
      fs.unlinkSync(req.file.path);
      return res.status(404).json({ error: '应用不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && app.owner_id !== user.id) {
      // 清理上传的文件
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: '无权操作此应用' });
    }

    // 删除旧的 keystore 文件
    if (app.keystore_path && fs.existsSync(app.keystore_path)) {
      fs.unlinkSync(app.keystore_path);
    }

    // 更新数据库中的 keystore 路径
    await db.run(`
      UPDATE apps
      SET keystore_path = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
    `, [req.file.path, app_id]);

    res.json({
      message: 'Keystore 文件上传成功',
      keystore_path: req.file.path,
      filename: req.file.filename
    });
  } catch (error) {
    console.error('上传 keystore 失败:', error);
    // 清理上传的文件
    if (req.file) {
      fs.unlinkSync(req.file.path);
    }
    res.status(500).json({ error: '上传 keystore 失败' });
  }
});

module.exports = router;
