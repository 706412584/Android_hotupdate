const nodemailer = require('nodemailer');
const axios = require('axios');

/**
 * 通知管理器
 */
class NotificationManager {
  constructor() {
    this.emailTransporter = null;
    this.webhookUrl = process.env.WEBHOOK_URL || null;
    this.emailEnabled = process.env.EMAIL_ENABLED === 'true';
    this.webhookEnabled = process.env.WEBHOOK_ENABLED === 'true';
    
    if (this.emailEnabled) {
      this.initEmailTransporter();
    }
  }

  /**
   * 初始化邮件发送器
   */
  initEmailTransporter() {
    try {
      this.emailTransporter = nodemailer.createTransport({
        host: process.env.SMTP_HOST,
        port: parseInt(process.env.SMTP_PORT) || 587,
        secure: process.env.SMTP_SECURE === 'true',
        auth: {
          user: process.env.SMTP_USER,
          pass: process.env.SMTP_PASS
        }
      });
      console.log('✅ 邮件通知已启用');
    } catch (error) {
      console.error('❌ 邮件通知初始化失败:', error);
      this.emailEnabled = false;
    }
  }

  /**
   * 发送邮件通知
   */
  async sendEmail(to, subject, html) {
    if (!this.emailEnabled || !this.emailTransporter) {
      console.warn('邮件通知未启用');
      return { success: false, error: '邮件通知未启用' };
    }

    try {
      const info = await this.emailTransporter.sendMail({
        from: process.env.SMTP_FROM || process.env.SMTP_USER,
        to,
        subject,
        html
      });

      console.log('✅ 邮件已发送:', info.messageId);
      return { success: true, messageId: info.messageId };
    } catch (error) {
      console.error('❌ 邮件发送失败:', error);
      return { success: false, error: error.message };
    }
  }

  /**
   * 发送 Webhook 通知
   */
  async sendWebhook(data) {
    if (!this.webhookEnabled || !this.webhookUrl) {
      console.warn('Webhook 通知未启用');
      return { success: false, error: 'Webhook 通知未启用' };
    }

    try {
      const response = await axios.post(this.webhookUrl, data, {
        headers: {
          'Content-Type': 'application/json',
          'User-Agent': 'Patch-Server-Notification'
        },
        timeout: 5000
      });

      console.log('✅ Webhook 已发送');
      return { success: true, response: response.data };
    } catch (error) {
      console.error('❌ Webhook 发送失败:', error.message);
      return { success: false, error: error.message };
    }
  }

  /**
   * 发送通知（邮件 + Webhook）
   */
  async notify(options) {
    const { type, title, message, data, recipients } = options;
    const results = {};

    // 发送邮件
    if (this.emailEnabled && recipients && recipients.length > 0) {
      const html = this.generateEmailHtml(type, title, message, data);
      
      for (const email of recipients) {
        const result = await this.sendEmail(email, title, html);
        results[`email_${email}`] = result;
      }
    }

    // 发送 Webhook
    if (this.webhookEnabled) {
      const webhookData = {
        type,
        title,
        message,
        data,
        timestamp: new Date().toISOString()
      };
      
      results.webhook = await this.sendWebhook(webhookData);
    }

    return results;
  }

  /**
   * 生成邮件 HTML
   */
  generateEmailHtml(type, title, message, data) {
    const typeColors = {
      success: '#67c23a',
      warning: '#e6a23c',
      error: '#f56c6c',
      info: '#409eff'
    };

    const color = typeColors[type] || typeColors.info;

    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
          .container { max-width: 600px; margin: 0 auto; padding: 20px; }
          .header { background: ${color}; color: white; padding: 20px; border-radius: 5px 5px 0 0; }
          .content { background: #f9f9f9; padding: 20px; border: 1px solid #ddd; border-top: none; }
          .footer { text-align: center; padding: 20px; color: #999; font-size: 12px; }
          .data { background: white; padding: 15px; border-radius: 5px; margin-top: 15px; }
          .data-item { margin: 8px 0; }
          .data-label { font-weight: bold; color: #666; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h2 style="margin: 0;">${title}</h2>
          </div>
          <div class="content">
            <p>${message}</p>
            ${data ? `
              <div class="data">
                ${Object.entries(data).map(([key, value]) => `
                  <div class="data-item">
                    <span class="data-label">${key}:</span> ${value}
                  </div>
                `).join('')}
              </div>
            ` : ''}
          </div>
          <div class="footer">
            <p>此邮件由补丁服务端自动发送，请勿回复</p>
            <p>发送时间: ${new Date().toLocaleString('zh-CN')}</p>
          </div>
        </div>
      </body>
      </html>
    `;
  }

  /**
   * 补丁上传通知
   */
  async notifyPatchUploaded(patch, app, user) {
    return this.notify({
      type: 'success',
      title: '补丁上传成功',
      message: `用户 ${user.username} 为应用 ${app.app_name} 上传了新补丁`,
      data: {
        '应用名称': app.app_name,
        '补丁版本': patch.version,
        '基础版本': patch.base_version,
        '文件大小': `${(patch.file_size / 1024 / 1024).toFixed(2)} MB`,
        '上传用户': user.username,
        '上传时间': new Date().toLocaleString('zh-CN')
      },
      recipients: this.getAdminEmails()
    });
  }

  /**
   * 补丁应用失败通知
   */
  async notifyPatchFailed(patch, app, errorCount) {
    if (errorCount < 10) return; // 失败次数少于 10 次不通知

    return this.notify({
      type: 'error',
      title: '补丁应用失败告警',
      message: `应用 ${app.app_name} 的补丁 ${patch.version} 已有 ${errorCount} 次应用失败`,
      data: {
        '应用名称': app.app_name,
        '补丁版本': patch.version,
        '失败次数': errorCount,
        '成功次数': patch.success_count || 0,
        '成功率': patch.success_count + patch.fail_count > 0 
          ? `${((patch.success_count / (patch.success_count + patch.fail_count)) * 100).toFixed(2)}%`
          : 'N/A'
      },
      recipients: this.getAdminEmails()
    });
  }

  /**
   * 备份完成通知
   */
  async notifyBackupCompleted(backupFile, size) {
    return this.notify({
      type: 'success',
      title: '数据备份完成',
      message: '系统已成功完成数据备份',
      data: {
        '备份文件': backupFile,
        '文件大小': `${(size / 1024 / 1024).toFixed(2)} MB`,
        '备份时间': new Date().toLocaleString('zh-CN')
      },
      recipients: this.getAdminEmails()
    });
  }

  /**
   * 备份失败通知
   */
  async notifyBackupFailed(error) {
    return this.notify({
      type: 'error',
      title: '数据备份失败',
      message: '系统备份过程中发生错误，请及时处理',
      data: {
        '错误信息': error.message,
        '发生时间': new Date().toLocaleString('zh-CN')
      },
      recipients: this.getAdminEmails()
    });
  }

  /**
   * 获取管理员邮箱列表
   */
  getAdminEmails() {
    const emails = process.env.ADMIN_EMAILS || '';
    return emails.split(',').map(e => e.trim()).filter(e => e);
  }
}

// 创建全局通知管理器实例
const notificationManager = new NotificationManager();

module.exports = {
  notificationManager,
  NotificationManager
};
