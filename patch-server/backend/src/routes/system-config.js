const express = require('express');
const router = express.Router();
const db = require('../models/database');
const { authenticateToken, requireAdmin } = require('../middleware/auth');

// 获取所有系统配置
router.get('/', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const configs = await db.query('SELECT * FROM system_config ORDER BY key');
    
    // 转换为键值对对象
    const configObj = {};
    configs.forEach(config => {
      configObj[config.key] = {
        value: config.value,
        description: config.description
      };
    });
    
    res.json(configObj);
  } catch (error) {
    console.error('获取系统配置失败:', error);
    res.status(500).json({ error: '获取系统配置失败' });
  }
});

// 获取单个配置
router.get('/:key', authenticateToken, async (req, res) => {
  try {
    const config = await db.get(
      'SELECT * FROM system_config WHERE key = ?',
      [req.params.key]
    );
    
    if (!config) {
      return res.status(404).json({ error: '配置不存在' });
    }
    
    res.json(config);
  } catch (error) {
    console.error('获取配置失败:', error);
    res.status(500).json({ error: '获取配置失败' });
  }
});

// 更新配置
router.put('/:key', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { value } = req.body;
    
    if (value === undefined) {
      return res.status(400).json({ error: '配置值不能为空' });
    }
    
    // 检查配置是否存在
    const existing = await db.get(
      'SELECT * FROM system_config WHERE key = ?',
      [req.params.key]
    );
    
    if (!existing) {
      return res.status(404).json({ error: '配置不存在' });
    }
    
    // 更新配置
    await db.run(
      'UPDATE system_config SET value = ?, updated_at = CURRENT_TIMESTAMP WHERE key = ?',
      [value, req.params.key]
    );
    
    res.json({ 
      message: '配置更新成功',
      key: req.params.key,
      value
    });
  } catch (error) {
    console.error('更新配置失败:', error);
    res.status(500).json({ error: '更新配置失败' });
  }
});

// 批量更新配置
router.post('/batch', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const { configs } = req.body;
    
    if (!configs || typeof configs !== 'object') {
      return res.status(400).json({ error: '无效的配置数据' });
    }
    
    // 批量更新
    for (const [key, value] of Object.entries(configs)) {
      await db.run(
        'UPDATE system_config SET value = ?, updated_at = CURRENT_TIMESTAMP WHERE key = ?',
        [value, key]
      );
    }
    
    res.json({ message: '配置批量更新成功' });
  } catch (error) {
    console.error('批量更新配置失败:', error);
    res.status(500).json({ error: '批量更新配置失败' });
  }
});

module.exports = router;
