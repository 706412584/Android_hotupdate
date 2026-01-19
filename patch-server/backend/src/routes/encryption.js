const express = require('express');
const router = express.Router();
const db = require('../models/database');
const { authenticateToken, requireAdmin } = require('../middleware/auth');
const { generateKey, validateKey, encryptText, decryptText } = require('../utils/encryption');

// 生成新的加密密钥
router.post('/generate-key', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const key = generateKey();
    res.json({ key });
  } catch (error) {
    console.error('生成密钥失败:', error);
    res.status(500).json({ error: '生成密钥失败' });
  }
});

// 获取应用的加密配置
router.get('/config/:appId', authenticateToken, async (req, res) => {
  try {
    const { appId } = req.params;
    
    // 检查权限
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [appId]);
    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }
    
    if (app.user_id !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({ error: '无权访问' });
    }
    
    // 获取加密配置
    const config = await db.get(
      'SELECT encryption_enabled, encryption_key FROM apps WHERE id = ?',
      [appId]
    );
    
    res.json({
      enabled: config.encryption_enabled === 1,
      hasKey: !!config.encryption_key
    });
  } catch (error) {
    console.error('获取加密配置失败:', error);
    res.status(500).json({ error: '获取加密配置失败' });
  }
});

// 更新应用的加密配置
router.put('/config/:appId', authenticateToken, async (req, res) => {
  try {
    const { appId } = req.params;
    const { enabled, key } = req.body;
    
    // 检查权限
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [appId]);
    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }
    
    if (app.user_id !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({ error: '无权访问' });
    }
    
    // 验证密钥格式
    if (enabled && key && !validateKey(key)) {
      return res.status(400).json({ error: '密钥格式无效，必须是 64 位十六进制字符串' });
    }
    
    // 更新配置
    await db.run(
      `UPDATE apps 
       SET encryption_enabled = ?, 
           encryption_key = ?,
           updated_at = CURRENT_TIMESTAMP
       WHERE id = ?`,
      [enabled ? 1 : 0, key || null, appId]
    );
    
    res.json({ 
      message: '加密配置已更新',
      enabled: enabled,
      hasKey: !!key
    });
  } catch (error) {
    console.error('更新加密配置失败:', error);
    res.status(500).json({ error: '更新加密配置失败' });
  }
});

// 验证密钥格式
router.post('/validate-key', authenticateToken, (req, res) => {
  try {
    const { key } = req.body;
    
    if (!key) {
      return res.status(400).json({ error: '密钥不能为空' });
    }
    
    const isValid = validateKey(key);
    
    res.json({ 
      valid: isValid,
      message: isValid ? '密钥格式正确' : '密钥格式无效，必须是 64 位十六进制字符串'
    });
  } catch (error) {
    console.error('验证密钥失败:', error);
    res.status(500).json({ error: '验证密钥失败' });
  }
});

// 测试加密/解密
router.post('/test', authenticateToken, requireAdmin, (req, res) => {
  try {
    const { key, text } = req.body;
    
    if (!key || !text) {
      return res.status(400).json({ error: '密钥和文本不能为空' });
    }
    
    if (!validateKey(key)) {
      return res.status(400).json({ error: '密钥格式无效' });
    }
    
    // 加密
    const encrypted = encryptText(text, key);
    
    // 解密
    const decrypted = decryptText(encrypted, key);
    
    res.json({
      success: true,
      original: text,
      encrypted: encrypted,
      decrypted: decrypted,
      match: text === decrypted
    });
  } catch (error) {
    console.error('测试加密失败:', error);
    res.status(500).json({ error: '测试加密失败: ' + error.message });
  }
});

module.exports = router;
