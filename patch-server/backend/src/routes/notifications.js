const express = require('express');
const router = express.Router();
const db = require('../models/database');
const { authenticateToken } = require('../middleware/auth');

// 获取用户通知列表
router.get('/', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { page = 1, limit = 20, unread_only = false } = req.query;
    const offset = (page - 1) * limit;
    
    let query = `
      SELECT * FROM notifications
      WHERE user_id = ?
    `;
    
    const params = [user.id];
    
    if (unread_only === 'true') {
      query += ' AND is_read = 0';
    }
    
    query += ' ORDER BY created_at DESC LIMIT ? OFFSET ?';
    params.push(parseInt(limit), offset);
    
    const notifications = await db.query(query, params);
    
    // 获取总数
    let countQuery = 'SELECT COUNT(*) as total FROM notifications WHERE user_id = ?';
    const countParams = [user.id];
    
    if (unread_only === 'true') {
      countQuery += ' AND is_read = 0';
    }
    
    const { total } = await db.get(countQuery, countParams);
    
    // 获取未读数量
    const { total: unreadCount } = await db.get(
      'SELECT COUNT(*) as total FROM notifications WHERE user_id = ? AND is_read = 0',
      [user.id]
    );
    
    res.json({
      notifications,
      pagination: {
        page: parseInt(page),
        limit: parseInt(limit),
        total
      },
      unreadCount
    });
  } catch (error) {
    console.error('获取通知失败:', error);
    res.status(500).json({ error: '获取通知失败' });
  }
});

// 标记通知为已读
router.put('/:id/read', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { id } = req.params;
    
    // 检查通知是否属于当前用户
    const notification = await db.get(
      'SELECT * FROM notifications WHERE id = ? AND user_id = ?',
      [id, user.id]
    );
    
    if (!notification) {
      return res.status(404).json({ error: '通知不存在' });
    }
    
    await db.run(
      'UPDATE notifications SET is_read = 1, read_at = CURRENT_TIMESTAMP WHERE id = ?',
      [id]
    );
    
    res.json({ message: '已标记为已读' });
  } catch (error) {
    console.error('标记通知失败:', error);
    res.status(500).json({ error: '标记通知失败' });
  }
});

// 标记所有通知为已读
router.put('/read-all', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    
    await db.run(
      'UPDATE notifications SET is_read = 1, read_at = CURRENT_TIMESTAMP WHERE user_id = ? AND is_read = 0',
      [user.id]
    );
    
    res.json({ message: '已全部标记为已读' });
  } catch (error) {
    console.error('标记所有通知失败:', error);
    res.status(500).json({ error: '标记所有通知失败' });
  }
});

// 删除通知
router.delete('/:id', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { id } = req.params;
    
    // 检查通知是否属于当前用户
    const notification = await db.get(
      'SELECT * FROM notifications WHERE id = ? AND user_id = ?',
      [id, user.id]
    );
    
    if (!notification) {
      return res.status(404).json({ error: '通知不存在' });
    }
    
    await db.run('DELETE FROM notifications WHERE id = ?', [id]);
    
    res.json({ message: '通知已删除' });
  } catch (error) {
    console.error('删除通知失败:', error);
    res.status(500).json({ error: '删除通知失败' });
  }
});

// 清空所有已读通知
router.delete('/clear-read', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    
    const result = await db.run(
      'DELETE FROM notifications WHERE user_id = ? AND is_read = 1',
      [user.id]
    );
    
    res.json({ 
      message: '已清空已读通知',
      deletedCount: result.changes
    });
  } catch (error) {
    console.error('清空通知失败:', error);
    res.status(500).json({ error: '清空通知失败' });
  }
});

// 创建通知（内部使用）
async function createNotification(userId, type, title, message, link = null, data = null) {
  try {
    await db.run(`
      INSERT INTO notifications (user_id, type, title, message, link, data)
      VALUES (?, ?, ?, ?, ?, ?)
    `, [userId, type, title, message, link, data ? JSON.stringify(data) : null]);
  } catch (error) {
    console.error('创建通知失败:', error);
  }
}

// 批量创建通知（给所有管理员）
async function notifyAdmins(type, title, message, link = null, data = null) {
  try {
    const admins = await db.query('SELECT id FROM users WHERE role = ?', ['admin']);
    
    for (const admin of admins) {
      await createNotification(admin.id, type, title, message, link, data);
    }
  } catch (error) {
    console.error('通知管理员失败:', error);
  }
}

module.exports = router;
module.exports.createNotification = createNotification;
module.exports.notifyAdmins = notifyAdmins;
