import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';

const REPO = 'drducqy95/DrDucBook';
const TAG_NAME = 'v1.0.0';
const RELEASE_NAME = 'DrDucBook v1.0.0';
const RELEASE_DIR = path.resolve('release/signed-apks-20260727-phase03-translation-final');

// Get token
let token = process.env.GITHUB_TOKEN;
if (!token) {
  try {
    const cred = execSync('"C:\\Program Files\\Git\\mingw64\\bin\\git-credential-manager.exe" get', {
      input: 'protocol=https\nhost=github.com\n',
      encoding: 'utf8'
    });
    const match = cred.match(/^password=(.+)$/m);
    if (match) {
      token = match[1].trim();
    }
  } catch (e) {
    console.error('Failed to get credential from GCM:', e.message);
  }
}

if (!token) {
  console.error('No GitHub token found.');
  process.exit(1);
}

const headers = {
  'Authorization': `Bearer ${token}`,
  'Accept': 'application/vnd.github+json',
  'User-Agent': 'DrDucBook-Release-Uploader',
  'X-GitHub-Api-Version': '2022-11-28'
};

async function main() {
  console.log(`Checking release for tag: ${TAG_NAME}...`);
  
  let release = null;
  const getRes = await fetch(`https://api.github.com/repos/${REPO}/releases/tags/${TAG_NAME}`, { headers });
  if (getRes.ok) {
    release = await getRes.json();
    console.log(`Found release: ${release.name} (ID: ${release.id})`);
  } else {
    console.log('Release does not exist, creating new release...');
    const shaPath = path.join(RELEASE_DIR, 'SHA256SUMS.txt');
    const shaContent = fs.existsSync(shaPath) ? fs.readFileSync(shaPath, 'utf8') : '';

    const body = [
      '## DrDucBook v1.0.0 (Material Design 3 Edition)',
      '',
      'Bản phát hành chính thức DrDucBook hỗ trợ đọc sách, dịch tự động và giao diện Material Design 3 Expressive.',
      '',
      '### 📦 Các bản cài đặt (APK):',
      '- **arm64-v8a**: Dành cho điện thoại Android 64-bit hiện đại.',
      '- **armeabi-v7a**: Dành cho thiết bị Android 32-bit cũ.',
      '- **x86_64**: Dành cho giả lập Android trên PC / ChromeOS.',
      '- **universal**: Bản cài đặt tổng hợp tất cả kiến trúc.',
      '',
      '### 🔒 Checksums (SHA256):',
      '```',
      shaContent.trim(),
      '```'
    ].join('\n');

    const createRes = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tag_name: TAG_NAME,
        target_commitish: 'main',
        name: RELEASE_NAME,
        body: body,
        draft: false,
        prerelease: false
      })
    });

    if (!createRes.ok) {
      const errText = await createRes.text();
      throw new Error(`Failed to create release: ${createRes.status} ${errText}`);
    }

    release = await createRes.json();
    console.log(`Created release: ${release.name} (ID: ${release.id})`);
  }

  const uploadUrlTemplate = release.upload_url.replace(/\{(\?name,label|.*)\}/, '');
  const existingAssets = new Map();
  if (Array.isArray(release.assets)) {
    for (const asset of release.assets) {
      existingAssets.set(asset.name, asset.id);
    }
  }

  const files = fs.readdirSync(RELEASE_DIR)
    .filter(f => f.endsWith('.apk') || f === 'SHA256SUMS.txt')
    .map(f => path.join(RELEASE_DIR, f));

  for (const filePath of files) {
    const fileName = path.basename(filePath);
    const stat = fs.statSync(filePath);
    const sizeMB = (stat.size / (1024 * 1024)).toFixed(2);
    console.log(`\n========================================`);
    console.log(`Processing ${fileName} (${sizeMB} MB)...`);

    if (existingAssets.has(fileName)) {
      const assetId = existingAssets.get(fileName);
      console.log(`Deleting existing asset ${fileName} (ID: ${assetId})...`);
      await fetch(`https://api.github.com/repos/${REPO}/releases/assets/${assetId}`, {
        method: 'DELETE',
        headers
      });
    }

    const contentType = fileName.endsWith('.apk') ? 'application/vnd.android.package-archive' : 'text/plain';
    const uploadUrl = `${uploadUrlTemplate}?name=${encodeURIComponent(fileName)}`;

    console.log(`Uploading ${fileName} via curl...`);
    const curlCmd = `curl.exe -f -s -S -X POST ` +
      `-H "Authorization: Bearer ${token}" ` +
      `-H "Content-Type: ${contentType}" ` +
      `-H "Accept: application/vnd.github+json" ` +
      `--data-binary @"${filePath.replace(/\\/g, '/')}" ` +
      `"${uploadUrl}"`;

    try {
      const output = execSync(curlCmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
      const resp = JSON.parse(output);
      console.log(`✓ Uploaded ${fileName} successfully! (Asset ID: ${resp.id}, Size: ${resp.size} bytes)`);
    } catch (e) {
      console.error(`✗ Failed to upload ${fileName}:`, e.message);
      if (e.stdout) console.error(e.stdout);
      if (e.stderr) console.error(e.stderr);
      throw e;
    }
  }

  console.log(`\n========================================`);
  console.log(`🎉 All release assets uploaded successfully!`);
  console.log(`Release URL: ${release.html_url}`);
  console.log(`========================================`);
}

main().catch(err => {
  console.error('Fatal error:', err.message);
  process.exit(1);
});
