const express = require('express');
const router = express.Router();
const path = require('path');
const db = require('../models/database');

// 检查更新
router.get('/check-update', async (req, res) => {
  try {
    const { version, deviceId, deviceModel, osVersion, appId, currentPatchVersion } = req.query;

    if (!version) {
      return res.status(400).json({ error: '版本号不能为空' });
    }

    // 获取应用配置（如果提供了 appId）
    let appConfig = null;
    if (appId) {
      appConfig = await db.get(`
        SELECT require_signature, require_encryption
        FROM apps
        WHERE app_id = ? AND status = 'active'
      `, [appId]);
    }

    // 确定当前版本（优先使用补丁版本，否则使用应用版本）
    const currentVersion = currentPatchVersion || version;

    // 查找最新的可用补丁
    let query = `
      SELECT p.* FROM patches p
      WHERE p.status = 'active'
        AND p.base_version = ?
    `;
    const params = [currentVersion];

    // 如果提供了 appId，只查找该应用的补丁
    if (appId && appConfig) {
      query += ` AND p.app_id = (SELECT id FROM apps WHERE app_id = ?)`;
      params.push(appId);
    }

    query += ` ORDER BY p.created_at DESC LIMIT 1`;

    const patch = await db.get(query, params);

    if (!patch) {
      return res.json({
        hasUpdate: false,
        message: '当前已是最新版本',
        securityConfig: appConfig ? {
          requireSignature: appConfig.require_signature === 1,
          requireEncryption: appConfig.require_encryption === 1
        } : null
      });
    }

    // 检查灰度发布
    if (patch.rollout_percentage < 100) {
      // 简单的灰度策略：基于设备 ID 的哈希值
      if (deviceId) {
        const hash = parseInt(deviceId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0));
        const percentage = hash % 100;
        if (percentage >= patch.rollout_percentage) {
          return res.json({
            hasUpdate: false,
            message: '当前已是最新版本',
            securityConfig: appConfig ? {
              requireSignature: appConfig.require_signature === 1,
              requireEncryption: appConfig.require_encryption === 1
            } : null
          });
        }
      }
    }

    // 构建下载 URL
    const downloadUrl = `${req.protocol}://${req.get('host')}/api/client/download/${patch.id}`;

    res.json({
      hasUpdate: true,
      patch: {
        version: patch.version,
        patchId: patch.patch_id,
        baseVersion: patch.base_version,
        downloadUrl,
        md5: patch.md5,
        size: patch.file_size,
        description: patch.description,
        forceUpdate: patch.force_update === 1
      },
      securityConfig: appConfig ? {
        requireSignature: appConfig.require_signature === 1,
        requireEncryption: appConfig.require_encryption === 1
      } : null
    });
  } catch (error) {
    console.error('检查更新失败:', error);
    res.status(500).json({ error: '检查更新失败' });
  }
});

// 下载补丁
router.get('/download/:id', async (req, res) => {
  try {
    const patch = await db.get(
      'SELECT * FROM patches WHERE id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    if (patch.status !== 'active') {
      return res.status(403).json({ error: '补丁不可用' });
    }

    // 记录下载
    const { deviceId, appVersion, deviceModel, osVersion } = req.query;
    const ipAddress = req.ip || req.connection.remoteAddress;

    await db.run(`
      INSERT INTO downloads (
        patch_id, device_id, app_version, device_model,
        os_version, ip_address, success
      ) VALUES (?, ?, ?, ?, ?, ?, 1)
    `, [
      patch.id,
      deviceId || null,
      appVersion || null,
      deviceModel || null,
      osVersion || null,
      ipAddress
    ]);

    // 更新下载计数
    await db.run(
      'UPDATE patches SET download_count = download_count + 1 WHERE id = ?',
      [patch.id]
    );

    // 发送文件
    res.download(patch.file_path, patch.file_name, (err) => {
      if (err) {
        console.error('下载失败:', err);
      }
    });
  } catch (error) {
    console.error('下载补丁失败:', error);
    res.status(500).json({ error: '下载补丁失败' });
  }
});

// 上报应用结果
router.post('/report', async (req, res) => {
  try {
    const { patchId, success, errorMessage, deviceId } = req.body;

    if (!patchId) {
      return res.status(400).json({ error: '补丁 ID 不能为空' });
    }

    // 查找补丁
    const patch = await db.get(
      'SELECT id FROM patches WHERE patch_id = ?',
      [patchId]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    // 更新统计
    if (success) {
      await db.run(
        'UPDATE patches SET success_count = success_count + 1 WHERE id = ?',
        [patch.id]
      );
    } else {
      await db.run(
        'UPDATE patches SET fail_count = fail_count + 1 WHERE id = ?',
        [patch.id]
      );
    }

    // 记录详细信息
    await db.run(`
      UPDATE downloads
      SET success = ?, error_message = ?
      WHERE patch_id = ? AND device_id = ?
      ORDER BY created_at DESC
      LIMIT 1
    `, [success ? 1 : 0, errorMessage || null, patch.id, deviceId]);

    res.json({ message: '上报成功' });
  } catch (error) {
    console.error('上报失败:', error);
    res.status(500).json({ error: '上报失败' });
  }
});

// 获取当前补丁信息
router.get('/current-patch', async (req, res) => {
  try {
    const { appId, deviceId } = req.query;

    if (!appId || !deviceId) {
      return res.status(400).json({ error: '应用 ID 和设备 ID 不能为空' });
    }

    // 查找该设备最近成功应用的补丁
    const download = await db.get(`
      SELECT 
        d.patch_id,
        d.app_version,
        d.created_at as applied_at,
        p.patch_id as patch_identifier,
        p.version as patch_version,
        p.base_version,
        p.description,
        p.file_size,
        p.md5
      FROM downloads d
      JOIN patches p ON d.patch_id = p.id
      WHERE d.device_id = ?
        AND p.app_id = (SELECT id FROM apps WHERE app_id = ?)
        AND d.success = 1
      ORDER BY d.created_at DESC
      LIMIT 1
    `, [deviceId, appId]);

    if (!download) {
      return res.json({
        hasAppliedPatch: false,
        message: '未找到已应用的补丁'
      });
    }

    res.json({
      hasAppliedPatch: true,
      patch: {
        patchId: download.patch_identifier,
        patchVersion: download.patch_version,
        baseVersion: download.base_version,
        appVersion: download.app_version,
        description: download.description,
        fileSize: download.file_size,
        md5: download.md5,
        appliedAt: download.applied_at
      }
    });
  } catch (error) {
    console.error('获取当前补丁信息失败:', error);
    res.status(500).json({ error: '获取当前补丁信息失败' });
  }
});

module.exports = router;
