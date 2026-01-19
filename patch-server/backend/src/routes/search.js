const express = require('express');
const router = express.Router();
const db = require('../models/database');
const { authenticateToken } = require('../middleware/auth');

/**
 * 全局搜索
 * GET /api/search?q=关键词
 */
router.get('/', authenticateToken, async (req, res) => {
  try {
    const { q } = req.query;
    const userId = req.user.id;
    const isAdmin = req.user.role === 'admin';

    if (!q || q.trim().length === 0) {
      return res.json({
        apps: [],
        patches: [],
        users: []
      });
    }

    const searchTerm = `%${q.trim()}%`;

    // 搜索应用
    const appsParams = [searchTerm, searchTerm, searchTerm];
    let appsQuery = `SELECT a.id, a.app_id, a.app_name, a.package_name, a.icon, a.status, a.created_at FROM apps a WHERE (a.app_name LIKE ? OR a.package_name LIKE ? OR a.app_id LIKE ?)`;
    
    // 普通用户只能搜索自己的应用
    if (!isAdmin) {
      appsQuery += ' AND a.owner_id = ?';
      appsParams.push(userId);
    }
    
    appsQuery += ' ORDER BY a.created_at DESC LIMIT 50';
    
    const apps = await db.query(appsQuery, appsParams);

    // 搜索补丁
    const patchesParams = [searchTerm, searchTerm];
    let patchesQuery = `SELECT p.id, p.app_id, p.version, p.description, p.created_at, a.app_name FROM patches p JOIN apps a ON p.app_id = a.id WHERE (p.version LIKE ? OR p.description LIKE ?)`;
    
    // 普通用户只能搜索自己应用的补丁
    if (!isAdmin) {
      patchesQuery += ' AND a.owner_id = ?';
      patchesParams.push(userId);
    }
    
    patchesQuery += ' ORDER BY p.created_at DESC LIMIT 50';
    
    const patches = await db.query(patchesQuery, patchesParams);

    // 搜索用户（仅管理员）
    let users = [];
    if (isAdmin) {
      const usersQuery = `SELECT id, username, email, role, created_at FROM users WHERE (username LIKE ? OR email LIKE ?) ORDER BY created_at DESC LIMIT 50`;
      users = await db.query(usersQuery, [searchTerm, searchTerm]);
    }

    res.json({
      apps,
      patches,
      users
    });
  } catch (error) {
    console.error('搜索失败:', error);
    res.status(500).json({ error: '搜索失败' });
  }
});

module.exports = router;
