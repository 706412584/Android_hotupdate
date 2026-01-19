const express = require('express');
const router = express.Router();
const db = require('../models/database');
const jwt = require('jsonwebtoken');

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

// 管理员权限检查
const adminOnly = (req, res, next) => {
  if (req.user.role !== 'admin') {
    return res.status(403).json({ error: '需要管理员权限' });
  }
  next();
};

// 获取操作日志列表
router.get('/', auth, adminOnly, async (req, res) => {
  try {
    const { page = 1, limit = 50, action, username, startDate, endDate } = req.query;
    const offset = (page - 1) * limit;

    let sql = 'SELECT * FROM logs WHERE 1=1';
    const params = [];

    if (action) {
      sql += ' AND action = ?';
      params.push(action);
    }

    if (username) {
      sql += ' AND username LIKE ?';
      params.push(`%${username}%`);
    }

    if (startDate) {
      sql += ' AND created_at >= ?';
      params.push(startDate);
    }

    if (endDate) {
      sql += ' AND created_at <= ?';
      params.push(endDate);
    }

    sql += ' ORDER BY created_at DESC LIMIT ? OFFSET ?';
    params.push(parseInt(limit), offset);

    const logs = await db.query(sql, params);

    // 获取总数
    let countSql = 'SELECT COUNT(*) as total FROM logs WHERE 1=1';
    const countParams = [];

    if (action) {
      countSql += ' AND action = ?';
      countParams.push(action);
    }

    if (username) {
      countSql += ' AND username LIKE ?';
      countParams.push(`%${username}%`);
    }

    if (startDate) {
      countSql += ' AND created_at >= ?';
      countParams.push(startDate);
    }

    if (endDate) {
      countSql += ' AND created_at <= ?';
      countParams.push(endDate);
    }

    const { total } = await db.get(countSql, countParams);

    res.json({
      logs,
      pagination: {
        total,
        page: parseInt(page),
        limit: parseInt(limit),
        pages: Math.ceil(total / limit)
      }
    });
  } catch (error) {
    console.error('获取日志失败:', error);
    res.status(500).json({ error: '获取日志失败' });
  }
});

// 获取操作类型统计
router.get('/stats', auth, adminOnly, async (req, res) => {
  try {
    const stats = await db.query(`
      SELECT action, COUNT(*) as count
      FROM logs
      GROUP BY action
      ORDER BY count DESC
    `);

    res.json(stats);
  } catch (error) {
    console.error('获取统计失败:', error);
    res.status(500).json({ error: '获取统计失败' });
  }
});

// 清理旧日志
router.delete('/cleanup', auth, adminOnly, async (req, res) => {
  try {
    const { days = 30 } = req.body;
    
    const result = await db.run(`
      DELETE FROM logs
      WHERE created_at < datetime('now', '-${parseInt(days)} days')
    `);

    res.json({
      message: '清理完成',
      deletedCount: result.changes
    });
  } catch (error) {
    console.error('清理日志失败:', error);
    res.status(500).json({ error: '清理日志失败' });
  }
});

module.exports = router;
