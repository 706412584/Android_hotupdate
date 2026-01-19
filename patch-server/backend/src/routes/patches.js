const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const db = require('../models/database');
const { authenticateToken } = require('../middleware/auth');

// 配置文件上传
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = process.env.UPLOAD_DIR || './uploads';
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'patch-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage,
  limits: {
    fileSize: parseInt(process.env.MAX_FILE_SIZE) || 100 * 1024 * 1024 // 100MB
  },
  fileFilter: (req, file, cb) => {
    if (file.mimetype === 'application/zip' || file.originalname.endsWith('.zip')) {
      cb(null, true);
    } else {
      cb(new Error('只支持 ZIP 文件'));
    }
  }
});

// 计算文件 MD5
function calculateMD5(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('md5');
    const stream = fs.createReadStream(filePath);
    stream.on('data', data => hash.update(data));
    stream.on('end', () => resolve(hash.digest('hex')));
    stream.on('error', reject);
  });
}

// 上传补丁
router.post('/upload', authenticateToken, upload.single('file'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '请上传文件' });
    }

    const { 
      app_id, 
      version, 
      base_version, 
      baseVersion,
      description, 
      force_update,
      forceUpdate 
    } = req.body;

    // 兼容两种命名方式
    const finalBaseVersion = base_version || baseVersion;
    const finalForceUpdate = force_update || forceUpdate || false;

    if (!version || !finalBaseVersion) {
      // 删除已上传的文件
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: '版本号和基础版本号不能为空' });
    }

    if (!app_id) {
      // 删除已上传的文件
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: '应用 ID 不能为空' });
    }

    // 获取应用信息和审核状态
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [app_id]);
    
    if (!app) {
      fs.unlinkSync(req.file.path);
      return res.status(404).json({ error: '应用不存在' });
    }

    // 检查应用所有权
    if (req.user.role !== 'admin' && app.owner_id !== req.user.id) {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: '无权操作此应用' });
    }

    // 检查应用审核状态（普通用户）
    if (req.user.role !== 'admin' && app.review_status !== 'approved') {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ 
        error: app.review_status === 'pending' ? '应用正在审核中，无法上传补丁' : '应用已被拒绝，无法上传补丁'
      });
    }

    // 检查应用是否停用（普通用户）
    if (req.user.role !== 'admin' && app.status === 'inactive') {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: '应用已停用，无法上传补丁' });
    }
    
    let finalFilePath = req.file.path;
    let isEncrypted = false;
    
    // 如果启用了加密，则加密补丁文件
    if (app && app.encryption_enabled && app.encryption_key) {
      try {
        const { encryptFile } = require('../utils/encryption');
        const encryptedPath = req.file.path + '.encrypted';
        
        await encryptFile(req.file.path, encryptedPath, app.encryption_key);
        
        // 删除原文件，使用加密文件
        fs.unlinkSync(req.file.path);
        fs.renameSync(encryptedPath, req.file.path);
        
        isEncrypted = true;
        console.log('✅ 补丁已加密');
      } catch (encError) {
        console.error('加密失败:', encError);
        // 加密失败不影响上传，继续使用未加密文件
      }
    }

    // 计算 MD5（加密后的文件）
    const md5 = await calculateMD5(req.file.path);

    // 生成补丁 ID
    const patchId = `patch_${Date.now()}`;

    // 保存到数据库
    const result = await db.run(`
      INSERT INTO patches (
        app_id, version, patch_id, base_version, file_path, file_name,
        file_size, md5, description, force_update
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      app_id,
      version,
      patchId,
      finalBaseVersion,
      req.file.path,
      req.file.filename,
      req.file.size,
      md5,
      description || '',
      finalForceUpdate ? 1 : 0
    ]);

    res.json({
      message: '补丁上传成功' + (isEncrypted ? '（已加密）' : ''),
      patch: {
        id: result.id,
        app_id,
        version,
        patchId,
        baseVersion: finalBaseVersion,
        fileName: req.file.filename,
        fileSize: req.file.size,
        md5,
        description,
        forceUpdate: finalForceUpdate,
        encrypted: isEncrypted
      }
    });
  } catch (error) {
    console.error('上传补丁失败:', error);
    // 删除已上传的文件
    if (req.file) {
      try {
        fs.unlinkSync(req.file.path);
      } catch (e) {
        // 忽略删除错误
      }
    }
    res.status(500).json({ error: '上传补丁失败: ' + error.message });
  }
});

// 获取补丁列表
router.get('/', authenticateToken, async (req, res) => {
  try {
    const { page = 1, limit = 10, status, version, app_id } = req.query;
    const { user } = req;
    const offset = (page - 1) * limit;

    let sql = 'SELECT p.*, a.app_name FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE 1=1';
    const params = [];

    // 普通用户只能看到自己应用的补丁
    if (user.role !== 'admin') {
      sql += ' AND a.owner_id = ?';
      params.push(user.id);
    }

    if (app_id) {
      sql += ' AND p.app_id = ?';
      params.push(app_id);
    }

    if (status) {
      sql += ' AND p.status = ?';
      params.push(status);
    }

    if (version) {
      sql += ' AND p.version LIKE ?';
      params.push(`%${version}%`);
    }

    sql += ' ORDER BY p.created_at DESC LIMIT ? OFFSET ?';
    params.push(parseInt(limit), offset);

    const patches = await db.query(sql, params);

    // 获取总数
    let countSql = 'SELECT COUNT(*) as total FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE 1=1';
    const countParams = [];

    // 普通用户只能看到自己应用的补丁
    if (user.role !== 'admin') {
      countSql += ' AND a.owner_id = ?';
      countParams.push(user.id);
    }

    if (app_id) {
      countSql += ' AND p.app_id = ?';
      countParams.push(app_id);
    }

    if (status) {
      countSql += ' AND p.status = ?';
      countParams.push(status);
    }

    if (version) {
      countSql += ' AND p.version LIKE ?';
      countParams.push(`%${version}%`);
    }

    const { total } = await db.get(countSql, countParams);

    res.json({
      patches,
      pagination: {
        total,
        page: parseInt(page),
        limit: parseInt(limit),
        pages: Math.ceil(total / limit)
      }
    });
  } catch (error) {
    console.error('获取补丁列表失败:', error);
    res.status(500).json({ error: '获取补丁列表失败' });
  }
});

// 获取单个补丁详情
router.get('/:id', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    
    const patch = await db.get(
      'SELECT p.*, a.owner_id FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE p.id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && patch.owner_id !== user.id) {
      return res.status(403).json({ error: '无权访问此补丁' });
    }

    res.json(patch);
  } catch (error) {
    console.error('获取补丁详情失败:', error);
    res.status(500).json({ error: '获取补丁详情失败' });
  }
});

// 更新补丁信息
router.put('/:id', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { description, forceUpdate, rolloutPercentage, status } = req.body;

    // 检查补丁是否存在并验证权限
    const patch = await db.get(
      'SELECT p.*, a.owner_id FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE p.id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && patch.owner_id !== user.id) {
      return res.status(403).json({ error: '无权修改此补丁' });
    }

    const updates = [];
    const params = [];

    if (description !== undefined) {
      updates.push('description = ?');
      params.push(description);
    }

    if (forceUpdate !== undefined) {
      updates.push('force_update = ?');
      params.push(forceUpdate ? 1 : 0);
    }

    if (rolloutPercentage !== undefined) {
      updates.push('rollout_percentage = ?');
      params.push(rolloutPercentage);
    }

    if (status !== undefined) {
      updates.push('status = ?');
      params.push(status);
    }

    if (updates.length === 0) {
      return res.status(400).json({ error: '没有要更新的字段' });
    }

    updates.push('updated_at = CURRENT_TIMESTAMP');
    params.push(req.params.id);

    await db.run(
      `UPDATE patches SET ${updates.join(', ')} WHERE id = ?`,
      params
    );

    res.json({ message: '更新成功' });
  } catch (error) {
    console.error('更新补丁失败:', error);
    res.status(500).json({ error: '更新补丁失败' });
  }
});

// 删除补丁
router.delete('/:id', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    
    const patch = await db.get(
      'SELECT p.file_path, a.owner_id FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE p.id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && patch.owner_id !== user.id) {
      return res.status(403).json({ error: '无权删除此补丁' });
    }

    // 删除文件
    if (fs.existsSync(patch.file_path)) {
      fs.unlinkSync(patch.file_path);
    }

    // 删除数据库记录
    await db.run('DELETE FROM patches WHERE id = ?', [req.params.id]);

    res.json({ message: '删除成功' });
  } catch (error) {
    console.error('删除补丁失败:', error);
    res.status(500).json({ error: '删除补丁失败' });
  }
});

// 封禁/解封单个补丁（管理员）
router.put('/:id/status', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { status } = req.body;

    if (user.role !== 'admin') {
      return res.status(403).json({ error: '需要管理员权限' });
    }

    if (!['active', 'inactive'].includes(status)) {
      return res.status(400).json({ error: '无效的状态值' });
    }

    const patch = await db.get(
      'SELECT p.*, a.owner_id FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE p.id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    await db.run(
      'UPDATE patches SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?',
      [status, req.params.id]
    );

    res.json({ 
      message: status === 'inactive' ? '补丁已封禁' : '补丁已解封',
      status 
    });
  } catch (error) {
    console.error('更新补丁状态失败:', error);
    res.status(500).json({ error: '更新补丁状态失败' });
  }
});

// 批量更新补丁状态
router.post('/batch-update-status', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { ids, status } = req.body;
    
    if (!ids || !Array.isArray(ids) || ids.length === 0) {
      return res.status(400).json({ error: '请选择要操作的补丁' });
    }
    
    if (!['active', 'inactive'].includes(status)) {
      return res.status(400).json({ error: '无效的状态值' });
    }
    
    // 检查权限
    for (const id of ids) {
      const patch = await db.get(
        'SELECT p.*, a.owner_id FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE p.id = ?',
        [id]
      );
      
      if (!patch) {
        continue;
      }
      
      if (user.role !== 'admin' && patch.owner_id !== user.id) {
        return res.status(403).json({ error: '无权操作部分补丁' });
      }
    }
    
    // 批量更新
    const placeholders = ids.map(() => '?').join(',');
    await db.run(
      `UPDATE patches SET status = ? WHERE id IN (${placeholders})`,
      [status, ...ids]
    );
    
    res.json({ 
      message: `已${status === 'active' ? '启用' : '停用'} ${ids.length} 个补丁`,
      count: ids.length
    });
  } catch (error) {
    console.error('批量更新补丁状态失败:', error);
    res.status(500).json({ error: '批量更新失败' });
  }
});

// 批量删除补丁
router.post('/batch-delete', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { ids } = req.body;
    
    if (!ids || !Array.isArray(ids) || ids.length === 0) {
      return res.status(400).json({ error: '请选择要删除的补丁' });
    }
    
    let deletedCount = 0;
    
    // 逐个检查权限并删除
    for (const id of ids) {
      const patch = await db.get(
        'SELECT p.file_path, a.owner_id FROM patches p LEFT JOIN apps a ON p.app_id = a.id WHERE p.id = ?',
        [id]
      );
      
      if (!patch) {
        continue;
      }
      
      // 检查权限
      if (user.role !== 'admin' && patch.owner_id !== user.id) {
        return res.status(403).json({ error: '无权删除部分补丁' });
      }
      
      // 删除文件
      if (fs.existsSync(patch.file_path)) {
        try {
          fs.unlinkSync(patch.file_path);
        } catch (err) {
          console.error('删除文件失败:', err);
        }
      }
      
      // 删除数据库记录
      await db.run('DELETE FROM patches WHERE id = ?', [id]);
      deletedCount++;
    }
    
    res.json({ 
      message: `已删除 ${deletedCount} 个补丁`,
      count: deletedCount
    });
  } catch (error) {
    console.error('批量删除补丁失败:', error);
    res.status(500).json({ error: '批量删除失败' });
  }
});

module.exports = router;
