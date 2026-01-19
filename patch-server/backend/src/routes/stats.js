const express = require('express');
const router = express.Router();
const db = require('../models/database');
const { authenticateToken } = require('../middleware/auth');

// 获取概览统计（增强版）
router.get('/overview', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    
    let appsQuery, patchesQuery, downloadsQuery, statsQuery;
    let todayDownloadsQuery, yesterdayDownloadsQuery;
    let todayPatchesQuery, yesterdayPatchesQuery;
    
    const params = user.role === 'admin' ? [] : [user.id];
    
    if (user.role === 'admin') {
      // 管理员看所有数据
      appsQuery = 'SELECT COUNT(*) as total FROM apps';
      patchesQuery = 'SELECT COUNT(*) as total FROM patches';
      downloadsQuery = 'SELECT SUM(download_count) as total FROM patches';
      statsQuery = `
        SELECT
          SUM(success_count) as successCount,
          SUM(fail_count) as failCount
        FROM patches
      `;
      todayDownloadsQuery = `
        SELECT COUNT(*) as count FROM downloads 
        WHERE DATE(created_at) = DATE('now')
      `;
      yesterdayDownloadsQuery = `
        SELECT COUNT(*) as count FROM downloads 
        WHERE DATE(created_at) = DATE('now', '-1 day')
      `;
      todayPatchesQuery = `
        SELECT COUNT(*) as count FROM patches 
        WHERE DATE(created_at) = DATE('now')
      `;
      yesterdayPatchesQuery = `
        SELECT COUNT(*) as count FROM patches 
        WHERE DATE(created_at) = DATE('now', '-1 day')
      `;
    } else {
      // 普通用户只看自己应用的数据
      appsQuery = 'SELECT COUNT(*) as total FROM apps WHERE owner_id = ?';
      patchesQuery = `
        SELECT COUNT(*) as total 
        FROM patches p 
        LEFT JOIN apps a ON p.app_id = a.id 
        WHERE a.owner_id = ?
      `;
      downloadsQuery = `
        SELECT SUM(p.download_count) as total 
        FROM patches p 
        LEFT JOIN apps a ON p.app_id = a.id 
        WHERE a.owner_id = ?
      `;
      statsQuery = `
        SELECT
          SUM(p.success_count) as successCount,
          SUM(p.fail_count) as failCount
        FROM patches p
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ?
      `;
      todayDownloadsQuery = `
        SELECT COUNT(*) as count FROM downloads d
        LEFT JOIN patches p ON d.patch_id = p.id
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ? AND DATE(d.created_at) = DATE('now')
      `;
      yesterdayDownloadsQuery = `
        SELECT COUNT(*) as count FROM downloads d
        LEFT JOIN patches p ON d.patch_id = p.id
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ? AND DATE(d.created_at) = DATE('now', '-1 day')
      `;
      todayPatchesQuery = `
        SELECT COUNT(*) as count FROM patches p
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ? AND DATE(p.created_at) = DATE('now')
      `;
      yesterdayPatchesQuery = `
        SELECT COUNT(*) as count FROM patches p
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ? AND DATE(p.created_at) = DATE('now', '-1 day')
      `;
    }
    
    const { total: totalApps } = await db.get(appsQuery, params);
    const { total: totalPatches } = await db.get(patchesQuery, params);
    const { total: totalDownloads } = await db.get(downloadsQuery, params);
    const stats = await db.get(statsQuery, params);
    
    const { count: todayDownloads } = await db.get(todayDownloadsQuery, user.role === 'admin' ? [] : [user.id]);
    const { count: yesterdayDownloads } = await db.get(yesterdayDownloadsQuery, user.role === 'admin' ? [] : [user.id]);
    const { count: todayPatches } = await db.get(todayPatchesQuery, user.role === 'admin' ? [] : [user.id]);
    const { count: yesterdayPatches } = await db.get(yesterdayPatchesQuery, user.role === 'admin' ? [] : [user.id]);

    const successRate = stats.successCount + stats.failCount > 0
      ? (stats.successCount / (stats.successCount + stats.failCount) * 100)
      : 0;
    
    // 计算趋势
    const downloadsTrend = yesterdayDownloads > 0 
      ? ((todayDownloads - yesterdayDownloads) / yesterdayDownloads * 100).toFixed(1)
      : 0;
    
    const patchesTrend = yesterdayPatches > 0
      ? ((todayPatches - yesterdayPatches) / yesterdayPatches * 100).toFixed(1)
      : 0;

    res.json({
      totalApps: totalApps || 0,
      totalPatches: totalPatches || 0,
      todayDownloads: todayDownloads || 0,
      successRate: parseFloat(successRate.toFixed(1)),
      appsTrend: 0, // 应用趋势暂时为0
      patchesTrend: parseFloat(patchesTrend),
      downloadsTrend: parseFloat(downloadsTrend),
      successRateTrend: 0 // 成功率趋势暂时为0
    });
  } catch (error) {
    console.error('获取统计失败:', error);
    res.status(500).json({ error: '获取统计失败' });
  }
});

// 获取告警信息
router.get('/alerts', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const alerts = [];
    
    // 检查失败率高的补丁
    let failedPatchesQuery;
    const params = [];
    
    if (user.role === 'admin') {
      failedPatchesQuery = `
        SELECT p.*, a.app_name
        FROM patches p
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE p.fail_count > 0 
          AND (p.fail_count * 1.0 / (p.success_count + p.fail_count)) > 0.3
        ORDER BY (p.fail_count * 1.0 / (p.success_count + p.fail_count)) DESC
        LIMIT 3
      `;
    } else {
      failedPatchesQuery = `
        SELECT p.*, a.app_name
        FROM patches p
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ?
          AND p.fail_count > 0 
          AND (p.fail_count * 1.0 / (p.success_count + p.fail_count)) > 0.3
        ORDER BY (p.fail_count * 1.0 / (p.success_count + p.fail_count)) DESC
        LIMIT 3
      `;
      params.push(user.id);
    }
    
    const failedPatches = await db.query(failedPatchesQuery, params);
    
    failedPatches.forEach(patch => {
      const failRate = (patch.fail_count / (patch.success_count + patch.fail_count) * 100).toFixed(1);
      alerts.push({
        id: `patch-fail-${patch.id}`,
        type: 'error',
        title: `补丁失败率过高`,
        message: `${patch.app_name} v${patch.version} 失败率 ${failRate}%`,
        link: `/apps/${patch.app_id}`
      });
    });
    
    res.json(alerts);
  } catch (error) {
    console.error('获取告警失败:', error);
    res.status(500).json({ error: '获取告警失败' });
  }
});

// 获取热门应用
router.get('/top-apps', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    const { limit = 5 } = req.query;
    
    let query;
    const params = [parseInt(limit)];
    
    if (user.role === 'admin') {
      query = `
        SELECT 
          a.*,
          SUM(p.download_count) as download_count
        FROM apps a
        LEFT JOIN patches p ON a.id = p.app_id
        GROUP BY a.id
        ORDER BY download_count DESC
        LIMIT ?
      `;
    } else {
      query = `
        SELECT 
          a.*,
          SUM(p.download_count) as download_count
        FROM apps a
        LEFT JOIN patches p ON a.id = p.app_id
        WHERE a.owner_id = ?
        GROUP BY a.id
        ORDER BY download_count DESC
        LIMIT ?
      `;
      params.unshift(user.id);
    }
    
    const apps = await db.query(query, params);
    res.json(apps);
  } catch (error) {
    console.error('获取热门应用失败:', error);
    res.status(500).json({ error: '获取热门应用失败' });
  }
});

// 获取下载趋势
router.get('/downloads-trend', authenticateToken, async (req, res) => {
  try {
    const { days = 7 } = req.query;
    const { user } = req;

    let query;
    const params = [parseInt(days)];
    
    if (user.role === 'admin') {
      query = `
        SELECT
          DATE(created_at) as date,
          COUNT(*) as count
        FROM downloads
        WHERE created_at >= datetime('now', '-' || ? || ' days')
        GROUP BY DATE(created_at)
        ORDER BY date ASC
      `;
    } else {
      query = `
        SELECT
          DATE(d.created_at) as date,
          COUNT(*) as count
        FROM downloads d
        LEFT JOIN patches p ON d.patch_id = p.id
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ? AND d.created_at >= datetime('now', '-' || ? || ' days')
        GROUP BY DATE(d.created_at)
        ORDER BY date ASC
      `;
      params.unshift(user.id);
    }

    const trend = await db.query(query, params);
    res.json(trend);
  } catch (error) {
    console.error('获取下载趋势失败:', error);
    res.status(500).json({ error: '获取下载趋势失败' });
  }
});

// 获取版本分布
router.get('/version-distribution', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    
    let query;
    const params = [];
    
    if (user.role === 'admin') {
      query = `
        SELECT
          app_version as version,
          COUNT(*) as count
        FROM downloads
        WHERE app_version IS NOT NULL
        GROUP BY app_version
        ORDER BY count DESC
        LIMIT 10
      `;
    } else {
      query = `
        SELECT
          d.app_version as version,
          COUNT(*) as count
        FROM downloads d
        LEFT JOIN patches p ON d.patch_id = p.id
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ? AND d.app_version IS NOT NULL
        GROUP BY d.app_version
        ORDER BY count DESC
        LIMIT 10
      `;
      params.push(user.id);
    }

    const distribution = await db.query(query, params);
    res.json(distribution);
  } catch (error) {
    console.error('获取版本分布失败:', error);
    res.status(500).json({ error: '获取版本分布失败' });
  }
});

// 获取设备分布
router.get('/device-distribution', authenticateToken, async (req, res) => {
  try {
    const { user } = req;
    
    let query;
    const params = [];
    
    if (user.role === 'admin') {
      query = `
        SELECT
          device_model as model,
          COUNT(*) as count
        FROM downloads
        WHERE device_model IS NOT NULL
        GROUP BY device_model
        ORDER BY count DESC
        LIMIT 10
      `;
    } else {
      query = `
        SELECT
          d.device_model as model,
          COUNT(*) as count
        FROM downloads d
        LEFT JOIN patches p ON d.patch_id = p.id
        LEFT JOIN apps a ON p.app_id = a.id
        WHERE a.owner_id = ? AND d.device_model IS NOT NULL
        GROUP BY d.device_model
        ORDER BY count DESC
        LIMIT 10
      `;
      params.push(user.id);
    }

    const distribution = await db.query(query, params);
    res.json(distribution);
  } catch (error) {
    console.error('获取设备分布失败:', error);
    res.status(500).json({ error: '获取设备分布失败' });
  }
});

// 获取补丁详细统计
router.get('/patch/:id', authenticateToken, async (req, res) => {
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
      return res.status(403).json({ error: '无权访问此补丁统计' });
    }

    // 下载记录
    const downloads = await db.query(`
      SELECT
        DATE(created_at) as date,
        COUNT(*) as count,
        SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as successCount,
        SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) as failCount
      FROM downloads
      WHERE patch_id = ?
      GROUP BY DATE(created_at)
      ORDER BY date DESC
      LIMIT 30
    `, [patch.id]);

    res.json({
      patch,
      downloads
    });
  } catch (error) {
    console.error('获取补丁统计失败:', error);
    res.status(500).json({ error: '获取补丁统计失败' });
  }
});

module.exports = router;
