# 服务端增强功能

## 📋 当前状态

### ✅ 已实现的功能
- Web 管理后台
- RESTful API
- 补丁上传和管理
- 灰度发布
- 统计分析
- 用户管理

### 🚧 待实现的功能
- 推送通知
- CDN 集成
- 高级灰度策略

---

## 1️⃣ 推送通知功能

### 功能说明

当有新补丁发布时，自动通知用户更新。

### 实现方案

#### 方案 A：Firebase Cloud Messaging (FCM)

**优点**:
- Google 官方支持
- 免费且可靠
- 支持 Android 和 iOS

**实现步骤**:

1. **后端集成 FCM**

```javascript
// patch-server/backend/src/services/fcm.js
const admin = require('firebase-admin');

// 初始化 Firebase Admin SDK
admin.initializeApp({
  credential: admin.credential.cert({
    projectId: process.env.FIREBASE_PROJECT_ID,
    clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
    privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n')
  })
});

// 发送推送通知
async function sendPushNotification(deviceToken, patchInfo) {
  const message = {
    notification: {
      title: '新补丁可用',
      body: `${patchInfo.appName} v${patchInfo.version} 已发布`
    },
    data: {
      patchId: patchInfo.id,
      version: patchInfo.version,
      downloadUrl: patchInfo.downloadUrl
    },
    token: deviceToken
  };

  try {
    const response = await admin.messaging().send(message);
    console.log('Successfully sent message:', response);
    return true;
  } catch (error) {
    console.error('Error sending message:', error);
    return false;
  }
}

// 批量发送
async function sendBatchNotifications(deviceTokens, patchInfo) {
  const messages = deviceTokens.map(token => ({
    notification: {
      title: '新补丁可用',
      body: `${patchInfo.appName} v${patchInfo.version} 已发布`
    },
    data: {
      patchId: patchInfo.id,
      version: patchInfo.version
    },
    token: token
  }));

  try {
    const response = await admin.messaging().sendAll(messages);
    console.log(`${response.successCount} messages sent successfully`);
    return response;
  } catch (error) {
    console.error('Error sending batch messages:', error);
    return null;
  }
}

module.exports = {
  sendPushNotification,
  sendBatchNotifications
};
```

2. **API 端点**

```javascript
// patch-server/backend/src/routes/notifications.js
const express = require('express');
const router = express.Router();
const { sendPushNotification, sendBatchNotifications } = require('../services/fcm');
const { Device } = require('../models');

// 注册设备 Token
router.post('/register-device', async (req, res) => {
  try {
    const { deviceId, fcmToken, appId } = req.body;
    
    await Device.upsert({
      deviceId,
      fcmToken,
      appId,
      lastSeen: new Date()
    });
    
    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 发送补丁通知
router.post('/send-patch-notification', async (req, res) => {
  try {
    const { patchId } = req.body;
    
    // 获取补丁信息
    const patch = await Patch.findByPk(patchId);
    if (!patch) {
      return res.status(404).json({ error: 'Patch not found' });
    }
    
    // 获取所有设备
    const devices = await Device.findAll({
      where: { appId: patch.appId }
    });
    
    const tokens = devices.map(d => d.fcmToken).filter(t => t);
    
    // 批量发送通知
    const result = await sendBatchNotifications(tokens, {
      id: patch.id,
      appName: patch.app.name,
      version: patch.version,
      downloadUrl: `/api/download/${patch.id}`
    });
    
    res.json({
      success: true,
      sent: result.successCount,
      failed: result.failureCount
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
```

3. **Android 客户端集成**

```java
// app/src/main/java/com/orange/update/fcm/MyFirebaseMessagingService.java
public class MyFirebaseMessagingService extends FirebaseMessagingService {
    
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // 处理推送消息
        if (remoteMessage.getData().size() > 0) {
            String patchId = remoteMessage.getData().get("patchId");
            String version = remoteMessage.getData().get("version");
            
            // 显示通知
            showNotification(
                remoteMessage.getNotification().getTitle(),
                remoteMessage.getNotification().getBody(),
                patchId
            );
        }
    }
    
    @Override
    public void onNewToken(String token) {
        // 上传新 Token 到服务器
        uploadTokenToServer(token);
    }
    
    private void showNotification(String title, String body, String patchId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("patchId", patchId);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "updates")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
        
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }
    
    private void uploadTokenToServer(String token) {
        // 上传到服务器
        UpdateManager updateManager = new UpdateManager(this, SERVER_URL);
        updateManager.registerDevice(token, new Callback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Token uploaded successfully");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to upload token: " + error);
            }
        });
    }
}
```

---

#### 方案 B：极光推送 (JPush)

**优点**:
- 国内网络环境友好
- 支持 Android 和 iOS
- 免费版足够使用

**实现步骤**:

1. **后端集成 JPush**

```javascript
// patch-server/backend/src/services/jpush.js
const JPush = require('jpush-sdk');

const client = JPush.buildClient({
  appKey: process.env.JPUSH_APP_KEY,
  masterSecret: process.env.JPUSH_MASTER_SECRET
});

async function sendPushNotification(registrationId, patchInfo) {
  try {
    const result = await client.push().setPlatform('android')
      .setAudience(JPush.registration_id(registrationId))
      .setNotification('新补丁可用', JPush.android(
        `${patchInfo.appName} v${patchInfo.version} 已发布`,
        null,
        1,
        {
          patchId: patchInfo.id,
          version: patchInfo.version
        }
      ))
      .send();
    
    console.log('Push sent:', result);
    return true;
  } catch (error) {
    console.error('Push error:', error);
    return false;
  }
}

module.exports = {
  sendPushNotification
};
```

2. **Android 客户端集成**

```java
// app/src/main/java/com/orange/update/jpush/MyJPushReceiver.java
public class MyJPushReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        
        if (JPushInterface.ACTION_NOTIFICATION_RECEIVED.equals(intent.getAction())) {
            // 收到通知
            String extras = bundle.getString(JPushInterface.EXTRA_EXTRA);
            try {
                JSONObject json = new JSONObject(extras);
                String patchId = json.getString("patchId");
                String version = json.getString("version");
                
                // 处理通知
                handlePatchNotification(context, patchId, version);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
```

---

## 2️⃣ CDN 集成

### 功能说明

使用 CDN 加速补丁文件下载，提升用户体验。

### 实现方案

#### 方案 A：阿里云 OSS

**优点**:
- 国内访问速度快
- 价格便宜
- 稳定可靠

**实现步骤**:

1. **后端集成 OSS**

```javascript
// patch-server/backend/src/services/oss.js
const OSS = require('ali-oss');

const client = new OSS({
  region: process.env.OSS_REGION,
  accessKeyId: process.env.OSS_ACCESS_KEY_ID,
  accessKeySecret: process.env.OSS_ACCESS_KEY_SECRET,
  bucket: process.env.OSS_BUCKET
});

// 上传文件到 OSS
async function uploadToOSS(localPath, remotePath) {
  try {
    const result = await client.put(remotePath, localPath);
    console.log('Upload success:', result.url);
    return result.url;
  } catch (error) {
    console.error('Upload error:', error);
    throw error;
  }
}

// 生成签名 URL（有效期 1 小时）
async function getSignedUrl(remotePath) {
  try {
    const url = client.signatureUrl(remotePath, {
      expires: 3600  // 1 小时
    });
    return url;
  } catch (error) {
    console.error('Get signed URL error:', error);
    throw error;
  }
}

// 删除文件
async function deleteFromOSS(remotePath) {
  try {
    await client.delete(remotePath);
    console.log('Delete success');
    return true;
  } catch (error) {
    console.error('Delete error:', error);
    return false;
  }
}

module.exports = {
  uploadToOSS,
  getSignedUrl,
  deleteFromOSS
};
```

2. **修改补丁上传 API**

```javascript
// patch-server/backend/src/routes/patches.js
const { uploadToOSS, getSignedUrl } = require('../services/oss');

router.post('/patches', upload.single('file'), async (req, res) => {
  try {
    const file = req.file;
    const { appId, version, baseVersion } = req.body;
    
    // 上传到 OSS
    const remotePath = `patches/${appId}/${version}/${file.filename}`;
    const ossUrl = await uploadToOSS(file.path, remotePath);
    
    // 保存到数据库
    const patch = await Patch.create({
      appId,
      version,
      baseVersion,
      fileName: file.originalname,
      fileSize: file.size,
      filePath: remotePath,  // OSS 路径
      ossUrl: ossUrl,        // OSS URL
      md5: calculateMd5(file.path)
    });
    
    // 删除本地文件
    fs.unlinkSync(file.path);
    
    res.json({ success: true, patch });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 下载补丁（返回签名 URL）
router.get('/download/:id', async (req, res) => {
  try {
    const patch = await Patch.findByPk(req.params.id);
    if (!patch) {
      return res.status(404).json({ error: 'Patch not found' });
    }
    
    // 生成签名 URL
    const signedUrl = await getSignedUrl(patch.filePath);
    
    // 重定向到 OSS
    res.redirect(signedUrl);
    
    // 更新下载计数
    patch.downloadCount += 1;
    await patch.save();
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});
```

---

#### 方案 B：腾讯云 COS

**实现类似，只需替换 SDK**:

```javascript
const COS = require('cos-nodejs-sdk-v5');

const cos = new COS({
  SecretId: process.env.COS_SECRET_ID,
  SecretKey: process.env.COS_SECRET_KEY
});

async function uploadToCOS(localPath, remotePath) {
  return new Promise((resolve, reject) => {
    cos.putObject({
      Bucket: process.env.COS_BUCKET,
      Region: process.env.COS_REGION,
      Key: remotePath,
      Body: fs.createReadStream(localPath)
    }, (err, data) => {
      if (err) reject(err);
      else resolve(data.Location);
    });
  });
}
```

---

## 3️⃣ 高级灰度策略

### 功能说明

支持更精细的灰度发布控制。

### 实现方案

#### 1. 按地区灰度

```javascript
// patch-server/backend/src/services/rollout.js
function shouldRollout(patch, device) {
  // 按地区灰度
  if (patch.rolloutRegions && patch.rolloutRegions.length > 0) {
    if (!patch.rolloutRegions.includes(device.region)) {
      return false;
    }
  }
  
  // 按百分比灰度
  if (patch.rolloutPercentage < 100) {
    const hash = hashDeviceId(device.deviceId);
    if (hash % 100 >= patch.rolloutPercentage) {
      return false;
    }
  }
  
  return true;
}
```

#### 2. 按设备型号灰度

```javascript
function shouldRollout(patch, device) {
  // 按设备型号灰度
  if (patch.rolloutModels && patch.rolloutModels.length > 0) {
    if (!patch.rolloutModels.includes(device.model)) {
      return false;
    }
  }
  
  return true;
}
```

#### 3. 按用户标签灰度

```javascript
function shouldRollout(patch, device, user) {
  // 按用户标签灰度
  if (patch.rolloutTags && patch.rolloutTags.length > 0) {
    const userTags = user.tags || [];
    const hasTag = patch.rolloutTags.some(tag => userTags.includes(tag));
    if (!hasTag) {
      return false;
    }
  }
  
  return true;
}
```

---

## 📋 实施计划

### 阶段 1：推送通知（1周）

**Day 1-2**: 选择推送方案（FCM 或 JPush）
**Day 3-4**: 后端集成推送服务
**Day 5-6**: Android 客户端集成
**Day 7**: 测试和调试

### 阶段 2：CDN 集成（1周）

**Day 1-2**: 选择 CDN 方案（阿里云 OSS 或腾讯云 COS）
**Day 3-4**: 后端集成 CDN 服务
**Day 5-6**: 修改上传和下载逻辑
**Day 7**: 测试和性能优化

### 阶段 3：高级灰度策略（3-5天）

**Day 1-2**: 设计灰度策略数据模型
**Day 3-4**: 实现灰度逻辑
**Day 5**: 测试和验证

---

## 🎯 优先级建议

### 高优先级
1. **推送通知** - 提升用户体验，及时通知更新
2. **CDN 集成** - 加速下载，降低服务器压力

### 中优先级
3. **高级灰度策略** - 更精细的发布控制

### 实施建议

如果资源有限，建议：
1. 先实现推送通知（用户体验提升明显）
2. 再实现 CDN 集成（性能提升明显）
3. 最后实现高级灰度策略（锦上添花）

---

## 📚 参考资料

- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)
- [极光推送文档](https://docs.jiguang.cn/jpush/guideline/intro/)
- [阿里云 OSS 文档](https://help.aliyun.com/product/31815.html)
- [腾讯云 COS 文档](https://cloud.tencent.com/document/product/436)
