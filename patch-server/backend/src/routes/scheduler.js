const express = require('express');
const router = express.Router();
const { authenticateToken, requireAdmin } = require('../middleware/auth');
const { scheduler, backupTask, cleanLogsTask, cleanDownloadsTask } = require('../utils/scheduler');

// 获取所有任务
router.get('/tasks', authenticateToken, requireAdmin, (req, res) => {
  try {
    const tasks = scheduler.getTasks();
    res.json({ tasks });
  } catch (error) {
    console.error('获取任务列表失败:', error);
    res.status(500).json({ error: '获取任务列表失败' });
  }
});

// 启动任务
router.post('/tasks/:name/start', authenticateToken, requireAdmin, (req, res) => {
  try {
    const { name } = req.params;
    scheduler.startTask(name);
    res.json({ message: `任务 ${name} 已启动` });
  } catch (error) {
    console.error('启动任务失败:', error);
    res.status(500).json({ error: error.message });
  }
});

// 停止任务
router.post('/tasks/:name/stop', authenticateToken, requireAdmin, (req, res) => {
  try {
    const { name } = req.params;
    scheduler.stopTask(name);
    res.json({ message: `任务 ${name} 已停止` });
  } catch (error) {
    console.error('停止任务失败:', error);
    res.status(500).json({ error: error.message });
  }
});

// 手动执行备份
router.post('/backup/run', authenticateToken, requireAdmin, async (req, res) => {
  try {
    await backupTask();
    res.json({ message: '备份完成' });
  } catch (error) {
    console.error('备份失败:', error);
    res.status(500).json({ error: '备份失败: ' + error.message });
  }
});

// 手动清理日志
router.post('/clean-logs/run', authenticateToken, requireAdmin, async (req, res) => {
  try {
    await cleanLogsTask();
    res.json({ message: '日志清理完成' });
  } catch (error) {
    console.error('清理日志失败:', error);
    res.status(500).json({ error: '清理日志失败: ' + error.message });
  }
});

// 手动清理下载记录
router.post('/clean-downloads/run', authenticateToken, requireAdmin, async (req, res) => {
  try {
    await cleanDownloadsTask();
    res.json({ message: '下载记录清理完成' });
  } catch (error) {
    console.error('清理下载记录失败:', error);
    res.status(500).json({ error: '清理下载记录失败: ' + error.message });
  }
});

// 获取备份列表
router.get('/backups', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const fs = require('fs').promises;
    const path = require('path');
    const backupDir = path.join(__dirname, '../../backups');
    
    try {
      const files = await fs.readdir(backupDir);
      const backups = [];
      
      for (const file of files) {
        const filePath = path.join(backupDir, file);
        const stats = await fs.stat(filePath);
        
        backups.push({
          name: file,
          size: stats.size,
          created: stats.birthtime,
          path: filePath
        });
      }
      
      // 按创建时间倒序排列
      backups.sort((a, b) => b.created - a.created);
      
      res.json({ backups });
    } catch (error) {
      if (error.code === 'ENOENT') {
        res.json({ backups: [] });
      } else {
        throw error;
      }
    }
  } catch (error) {
    console.error('获取备份列表失败:', error);
    res.status(500).json({ error: '获取备份列表失败' });
  }
});

// 删除备份
router.delete('/backups/:filename', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const fs = require('fs').promises;
    const path = require('path');
    const { filename } = req.params;
    
    // 安全检查：防止路径遍历攻击
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
      return res.status(400).json({ error: '无效的文件名' });
    }
    
    const backupDir = path.join(__dirname, '../../backups');
    const filePath = path.join(backupDir, filename);
    
    await fs.unlink(filePath);
    res.json({ message: '备份已删除' });
  } catch (error) {
    console.error('删除备份失败:', error);
    res.status(500).json({ error: '删除备份失败' });
  }
});

// 恢复数据
router.post('/restore', authenticateToken, requireAdmin, async (req, res) => {
  try {
    const path = require('path');
    const { filename } = req.body;
    
    if (!filename) {
      return res.status(400).json({ error: '请指定备份文件' });
    }
    
    // 安全检查
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
      return res.status(400).json({ error: '无效的文件名' });
    }
    
    const backupDir = path.join(__dirname, '../../backups');
    const backupFile = path.join(backupDir, filename);
    
    // 导入恢复函数
    const { restore } = require('../scripts/restore');
    
    // 执行恢复（异步）
    restore(backupFile)
      .then((result) => {
        console.log('恢复完成:', result);
      })
      .catch((error) => {
        console.error('恢复失败:', error);
      });
    
    res.json({ 
      message: '恢复任务已启动，请等待完成后重启服务',
      warning: '恢复过程中请勿操作系统'
    });
  } catch (error) {
    console.error('启动恢复失败:', error);
    res.status(500).json({ error: '启动恢复失败: ' + error.message });
  }
});

module.exports = router;
