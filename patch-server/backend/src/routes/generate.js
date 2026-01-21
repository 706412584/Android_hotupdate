const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const db = require('../models/database');
const patchGenerator = require('../services/patchGenerator');

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

// 配置文件上传
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = process.env.UPLOAD_DIR || './uploads';
    const tempDir = path.join(uploadDir, 'temp');
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }
    cb(null, tempDir);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage,
  limits: {
    fileSize: 200 * 1024 * 1024 // 200MB
  },
  fileFilter: (req, file, cb) => {
    if (file.mimetype === 'application/vnd.android.package-archive' || 
        file.originalname.endsWith('.apk')) {
      cb(null, true);
    } else {
      cb(new Error('只支持 APK 文件'));
    }
  }
});

// 检查 patch-cli 是否可用
router.get('/check', auth, async (req, res) => {
  try {
    const result = await patchGenerator.checkAvailable();
    res.json(result);
  } catch (error) {
    console.error('检查失败:', error);
    res.status(500).json({ error: '检查失败' });
  }
});

// 上传 APK 并生成补丁
router.post('/patch', auth, upload.fields([
  { name: 'baseApk', maxCount: 1 },
  { name: 'newApk', maxCount: 1 }
]), async (req, res) => {
  try {
    const { app_id, version, base_version, description, force_update, package_name, app_id_string } = req.body;
    const { user } = req;

    console.log('🔍 生成补丁请求参数:');
    console.log('  - app_id:', app_id, '(类型:', typeof app_id, ')');
    console.log('  - version:', version);
    console.log('  - base_version:', base_version);
    console.log('  - package_name:', package_name);
    console.log('  - app_id_string:', app_id_string);
    console.log('  - 用户ID:', user.id);
    console.log('  - 用户角色:', user.role);

    if (!req.files.baseApk || !req.files.newApk) {
      return res.status(400).json({ error: '请上传基准 APK 和新版本 APK' });
    }

    if (!app_id || !version || !base_version) {
      return res.status(400).json({ error: '应用 ID、版本号和基础版本号不能为空' });
    }

    // 确保 app_id 是数字类型
    const numericAppId = parseInt(app_id);
    if (isNaN(numericAppId)) {
      return res.status(400).json({ error: '应用 ID 格式错误' });
    }

    // 获取应用配置
    const app = await db.get('SELECT * FROM apps WHERE id = ?', [numericAppId]);
    
    console.log('  - 查询到的应用:', app ? `ID=${app.id}, app_id=${app.app_id}, name=${app.app_name}, package=${app.package_name}` : '未找到');
    
    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    // 检查权限
    if (user.role !== 'admin' && app.owner_id !== user.id) {
      return res.status(403).json({ error: '无权操作此应用' });
    }

    // 🔒 强制验证：确保补丁的包名和 app_id 与应用匹配
    if (package_name && package_name !== app.package_name) {
      console.log('  ❌ 包名不匹配！请求:', package_name, '应用:', app.package_name);
      return res.status(400).json({ 
        error: '包名不匹配',
        message: `补丁包名 "${package_name}" 与应用包名 "${app.package_name}" 不一致，请确认选择了正确的应用`
      });
    }
    
    if (app_id_string && app_id_string !== app.app_id) {
      console.log('  ❌ app_id 不匹配！请求:', app_id_string, '应用:', app.app_id);
      return res.status(400).json({ 
        error: 'app_id 不匹配',
        message: `补丁 app_id "${app_id_string}" 与应用 app_id "${app.app_id}" 不一致，请确认选择了正确的应用`
      });
    }
    
    console.log('  ✅ 包名和 app_id 验证通过');
    console.log('     包名:', app.package_name);
    console.log('     app_id:', app.app_id);

    const baseApkPath = req.files.baseApk[0].path;
    const newApkPath = req.files.newApk[0].path;
    
    // 生成输出文件名
    const uploadDir = process.env.UPLOAD_DIR || './uploads';
    const patchFileName = `patch-${app.app_id}-${version}-${Date.now()}.zip`;
    const patchPath = path.join(uploadDir, patchFileName);

    // 准备生成选项
    const generateOptions = {
      baseApk: baseApkPath,
      newApk: newApkPath,
      output: patchPath,
      verbose: true
    };

    // 如果应用配置了签名，添加签名参数
    if (app.require_signature && app.keystore_password && app.key_alias && app.key_password) {
      // 注意：这里需要 keystore 文件路径
      // 实际使用时需要上传或配置 keystore 文件
      const keystorePath = app.keystore_path;
      if (keystorePath && fs.existsSync(keystorePath)) {
        generateOptions.keystore = keystorePath;
        generateOptions.keystorePassword = app.keystore_password;
        generateOptions.keyAlias = app.key_alias;
        generateOptions.keyPassword = app.key_password;
      }
    }

    // 生成补丁
    const result = await patchGenerator.generate(generateOptions, (progress) => {
      console.log(`生成进度: ${progress}%`);
    });

    // 检查是否生成了补丁文件（两个版本内容一样时不会生成）
    if (!fs.existsSync(patchPath)) {
      // 清理临时文件
      fs.unlinkSync(baseApkPath);
      fs.unlinkSync(newApkPath);
      
      return res.status(400).json({ 
        error: '无需生成补丁', 
        details: '两个 APK 版本的内容完全相同，没有差异需要打补丁。请检查是否上传了正确的版本。'
      });
    }

    // 计算 MD5
    const md5 = await calculateMD5(patchPath);
    const fileSize = fs.statSync(patchPath).size;

    // 生成补丁 ID
    const patchId = `patch_${Date.now()}`;

    // 保存到数据库
    const dbResult = await db.run(`
      INSERT INTO patches (
        app_id, version, patch_id, base_version, file_path, file_name,
        file_size, md5, description, force_update, created_by
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      numericAppId,  // 使用转换后的数字 ID
      version,
      patchId,
      base_version,
      patchPath,
      patchFileName,
      fileSize,
      md5,
      description || '',
      force_update ? 1 : 0,
      user.id
    ]);

    console.log('✅ 补丁生成并保存成功:');
    console.log('  - 补丁数据库 ID:', dbResult.id);
    console.log('  - 关联的应用 ID:', numericAppId);
    console.log('  - patch_id:', patchId);

    // 清理临时文件
    fs.unlinkSync(baseApkPath);
    fs.unlinkSync(newApkPath);

    res.json({
      message: '补丁生成成功',
      patch: {
        id: dbResult.id,
        version,
        patchId,
        baseVersion: base_version,
        fileName: patchFileName,
        fileSize,
        md5,
        generationInfo: {
          size: result.size,
          compression: result.compression,
          time: result.time
        }
      }
    });
  } catch (error) {
    console.error('生成补丁失败:', error);
    
    // 清理临时文件
    if (req.files) {
      if (req.files.baseApk) fs.unlinkSync(req.files.baseApk[0].path);
      if (req.files.newApk) fs.unlinkSync(req.files.newApk[0].path);
    }
    
    res.status(500).json({ 
      error: '生成补丁失败', 
      details: error.error || error.message 
    });
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

module.exports = router;
