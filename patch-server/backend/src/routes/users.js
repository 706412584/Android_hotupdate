const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const db = require('../models/database');
const { authenticateToken, requireAdmin } = require('../middleware/auth');

// 获取用户列表（带统计信息）
router.get('/', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const users = await db.query(`
      SELECT 
        u.id, 
        u.username, 
        u.email, 
        u.avatar, 
        u.role, 
        u.status, 
        u.created_at,
        COUNT(DISTINCT a.id) as app_count,
        COUNT(DISTINCT p.id) as patch_count
      FROM users u
      LEFT JOIN apps a ON u.id = a.owner_id
      LEFT JOIN patches p ON a.id = p.app_id
      GROUP BY u.id
      ORDER BY u.created_at DESC
    `);

    res.json(users);
  } catch (error) {
    console.error('获取用户列表失败:', error);
    res.status(500).json({ error: '获取用户列表失败' });
  }
});

// 获取用户详情（包含应用和补丁列表）
router.get('/:id/detail', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    
    // 获取用户基本信息
    const user = await db.get(`
      SELECT id, username, email, avatar, role, status, created_at
      FROM users
      WHERE id = ?
    `, [id]);
    
    if (!user) {
      return res.status(404).json({ error: '用户不存在' });
    }
    
    // 获取用户的应用列表
    const apps = await db.query(`
      SELECT 
        a.*,
        COUNT(p.id) as patch_count
      FROM apps a
      LEFT JOIN patches p ON a.id = p.app_id
      WHERE a.owner_id = ?
      GROUP BY a.id
      ORDER BY a.created_at DESC
    `, [id]);
    
    // 获取用户的补丁列表
    const patches = await db.query(`
      SELECT 
        p.*,
        a.app_name
      FROM patches p
      LEFT JOIN apps a ON p.app_id = a.id
      WHERE a.owner_id = ?
      ORDER BY p.created_at DESC
    `, [id]);
    
    res.json({
      user,
      apps,
      patches
    });
  } catch (error) {
    console.error('获取用户详情失败:', error);
    res.status(500).json({ error: '获取用户详情失败' });
  }
});

// 创建用户
router.post('/', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { username, password, email, role = 'user' } = req.body;

    if (!username || !password) {
      return res.status(400).json({ error: '用户名和密码不能为空' });
    }

    // 检查用户是否已存在
    const existing = await db.get(
      'SELECT id FROM users WHERE username = ?',
      [username]
    );

    if (existing) {
      return res.status(400).json({ error: '用户名已存在' });
    }

    // 加密密码
    const hashedPassword = await bcrypt.hash(password, 10);

    // 创建用户
    const result = await db.run(
      'INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)',
      [username, hashedPassword, email, role]
    );

    res.json({
      message: '创建成功',
      user: {
        id: result.id,
        username,
        email,
        role
      }
    });
  } catch (error) {
    console.error('创建用户失败:', error);
    res.status(500).json({ error: '创建用户失败' });
  }
});

// 删除用户
router.delete('/:id', authenticateToken, requireAdmin, async (req, res) => {
  try {
    await db.run('DELETE FROM users WHERE id = ?', [req.params.id]);
    res.json({ message: '删除成功' });
  } catch (error) {
    console.error('删除用户失败:', error);
    res.status(500).json({ error: '删除用户失败' });
  }
});

// 封禁/解封用户
router.put('/:id/status', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { status } = req.body;
    
    if (!['active', 'banned'].includes(status)) {
      return res.status(400).json({ error: '无效的状态值' });
    }

    await db.run(
      'UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?',
      [status, req.params.id]
    );

    res.json({ 
      message: status === 'banned' ? '用户已封禁' : '用户已解封',
      status 
    });
  } catch (error) {
    console.error('更新用户状态失败:', error);
    res.status(500).json({ error: '更新用户状态失败' });
  }
});

// 封禁用户的应用
router.put('/:id/ban-apps', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    
    // 将用户的所有应用设置为 inactive
    await db.run(`
      UPDATE apps 
      SET status = 'inactive', updated_at = CURRENT_TIMESTAMP 
      WHERE owner_id = ?
    `, [id]);
    
    res.json({ message: '用户应用已全部封禁' });
  } catch (error) {
    console.error('封禁应用失败:', error);
    res.status(500).json({ error: '封禁应用失败' });
  }
});

// 删除用户的所有补丁
router.delete('/:id/patches', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const fs = require('fs');
    
    // 获取用户的所有补丁
    const patches = await db.query(`
      SELECT p.id, p.file_path
      FROM patches p
      LEFT JOIN apps a ON p.app_id = a.id
      WHERE a.owner_id = ?
    `, [id]);
    
    // 删除补丁文件
    for (const patch of patches) {
      if (fs.existsSync(patch.file_path)) {
        fs.unlinkSync(patch.file_path);
      }
    }
    
    // 删除数据库记录
    await db.run(`
      DELETE FROM patches 
      WHERE app_id IN (
        SELECT id FROM apps WHERE owner_id = ?
      )
    `, [id]);
    
    res.json({ 
      message: '用户补丁已全部删除',
      count: patches.length
    });
  } catch (error) {
    console.error('删除补丁失败:', error);
    res.status(500).json({ error: '删除补丁失败' });
  }
});

// 更新用户信息
router.put('/:id', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { email, role } = req.body;
    const updates = [];
    const params = [];

    if (email !== undefined) {
      updates.push('email = ?');
      params.push(email);
    }

    if (role !== undefined) {
      updates.push('role = ?');
      params.push(role);
    }

    if (updates.length === 0) {
      return res.status(400).json({ error: '没有要更新的字段' });
    }

    updates.push('updated_at = CURRENT_TIMESTAMP');
    params.push(req.params.id);

    await db.run(
      `UPDATE users SET ${updates.join(', ')} WHERE id = ?`,
      params
    );

    res.json({ message: '更新成功' });
  } catch (error) {
    console.error('更新用户失败:', error);
    res.status(500).json({ error: '更新用户失败' });
  }
});

module.exports = router;
