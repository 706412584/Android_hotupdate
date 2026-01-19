const crypto = require('crypto');
const fs = require('fs').promises;
const path = require('path');

// AES-256-CBC 加密配置
const ALGORITHM = 'aes-256-cbc';
const KEY_LENGTH = 32; // 256 bits
const IV_LENGTH = 16; // 128 bits

/**
 * 生成随机密钥
 */
function generateKey() {
  return crypto.randomBytes(KEY_LENGTH).toString('hex');
}

/**
 * 生成随机 IV
 */
function generateIV() {
  return crypto.randomBytes(IV_LENGTH);
}

/**
 * 从十六进制字符串转换为 Buffer
 */
function keyFromHex(hexKey) {
  if (!hexKey || hexKey.length !== KEY_LENGTH * 2) {
    throw new Error('Invalid encryption key format');
  }
  return Buffer.from(hexKey, 'hex');
}

/**
 * 加密文件
 * @param {string} inputPath - 输入文件路径
 * @param {string} outputPath - 输出文件路径
 * @param {string} hexKey - 十六进制密钥
 * @returns {Promise<{iv: string, encryptedPath: string}>}
 */
async function encryptFile(inputPath, outputPath, hexKey) {
  try {
    const key = keyFromHex(hexKey);
    const iv = generateIV();
    
    // 创建加密器
    const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
    
    // 读取原文件
    const inputData = await fs.readFile(inputPath);
    
    // 加密数据
    const encrypted = Buffer.concat([
      cipher.update(inputData),
      cipher.final()
    ]);
    
    // 写入加密文件（IV + 加密数据）
    const output = Buffer.concat([iv, encrypted]);
    await fs.writeFile(outputPath, output);
    
    return {
      iv: iv.toString('hex'),
      encryptedPath: outputPath
    };
  } catch (error) {
    console.error('Encryption error:', error);
    throw new Error(`Failed to encrypt file: ${error.message}`);
  }
}

/**
 * 解密文件
 * @param {string} inputPath - 加密文件路径
 * @param {string} outputPath - 输出文件路径
 * @param {string} hexKey - 十六进制密钥
 * @returns {Promise<string>}
 */
async function decryptFile(inputPath, outputPath, hexKey) {
  try {
    const key = keyFromHex(hexKey);
    
    // 读取加密文件
    const encryptedData = await fs.readFile(inputPath);
    
    // 提取 IV（前 16 字节）
    const iv = encryptedData.slice(0, IV_LENGTH);
    const encrypted = encryptedData.slice(IV_LENGTH);
    
    // 创建解密器
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    
    // 解密数据
    const decrypted = Buffer.concat([
      decipher.update(encrypted),
      decipher.final()
    ]);
    
    // 写入解密文件
    await fs.writeFile(outputPath, decrypted);
    
    return outputPath;
  } catch (error) {
    console.error('Decryption error:', error);
    throw new Error(`Failed to decrypt file: ${error.message}`);
  }
}

/**
 * 加密字符串
 * @param {string} text - 要加密的文本
 * @param {string} hexKey - 十六进制密钥
 * @returns {string} - 加密后的文本（hex 格式）
 */
function encryptText(text, hexKey) {
  try {
    const key = keyFromHex(hexKey);
    const iv = generateIV();
    
    const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
    let encrypted = cipher.update(text, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    
    // 返回 IV + 加密数据
    return iv.toString('hex') + ':' + encrypted;
  } catch (error) {
    console.error('Text encryption error:', error);
    throw new Error(`Failed to encrypt text: ${error.message}`);
  }
}

/**
 * 解密字符串
 * @param {string} encryptedText - 加密的文本（hex 格式）
 * @param {string} hexKey - 十六进制密钥
 * @returns {string} - 解密后的文本
 */
function decryptText(encryptedText, hexKey) {
  try {
    const key = keyFromHex(hexKey);
    
    // 分离 IV 和加密数据
    const parts = encryptedText.split(':');
    if (parts.length !== 2) {
      throw new Error('Invalid encrypted text format');
    }
    
    const iv = Buffer.from(parts[0], 'hex');
    const encrypted = parts[1];
    
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    let decrypted = decipher.update(encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    
    return decrypted;
  } catch (error) {
    console.error('Text decryption error:', error);
    throw new Error(`Failed to decrypt text: ${error.message}`);
  }
}

/**
 * 验证密钥格式
 * @param {string} hexKey - 十六进制密钥
 * @returns {boolean}
 */
function validateKey(hexKey) {
  if (!hexKey || typeof hexKey !== 'string') {
    return false;
  }
  
  // 检查长度（64 个十六进制字符 = 32 字节 = 256 位）
  if (hexKey.length !== KEY_LENGTH * 2) {
    return false;
  }
  
  // 检查是否为有效的十六进制字符串
  return /^[0-9a-fA-F]+$/.test(hexKey);
}

module.exports = {
  generateKey,
  encryptFile,
  decryptFile,
  encryptText,
  decryptText,
  validateKey,
  KEY_LENGTH,
  IV_LENGTH
};
