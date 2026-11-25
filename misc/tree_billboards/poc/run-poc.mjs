// POC driver: serve SeedThree over HTTP, drive headless Chrome over CDP, bake a
// species billboard to raw RGBA, write PNGs. Nothing is written into the SeedThree repo.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

const SEEDTHREE = 'D:/src/SeedThree';
const POC = path.dirname(fileURLToPath(import.meta.url)).replace(/\\/g, '/');
const OUT = process.env.OUT_DIR || `${POC}/out`;
const PORT = Number(process.env.PROBE_PORT || 9455);
const species = process.argv[2] || 'redMaple';
const seed = Number(process.argv[3] || 7);
const size = Number(process.argv[4] || 512);
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';

fs.mkdirSync(OUT, { recursive: true });

const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.json': 'application/json', '.png': 'image/png', '.css': 'text/css', '.glb': 'model/gltf-binary',
};
const serve = (req, res) => {
  const url = decodeURIComponent((req.url || '/').split('?')[0]);
  let root = SEEDTHREE, rel = url;
  if (url.startsWith('/poc/')) { root = POC; rel = url.slice(4); }
  const file = path.posix.normalize(path.posix.join(root, rel));
  if (!file.startsWith(root)) { res.writeHead(403).end('nope'); return; }
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404).end('404 ' + file); return; }
    res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream' });
    res.end(buf);
  });
};
const server = http.createServer(serve);
await new Promise((r) => server.listen(PORT, '127.0.0.1', r));

const profile = `${POC}/profile_poc`;
fs.rmSync(profile, { recursive: true, force: true });
const chrome = spawn(CHROME, [
  '--headless=new', `--remote-debugging-port=${PORT + 1}`, `--user-data-dir=${profile}`,
  '--enable-unsafe-webgpu', '--hide-scrollbars', '--disable-extensions', '--no-first-run',
], { stdio: 'ignore' });

const port = PORT + 1;
let pages = null;
for (let i = 0; i < 40 && !pages; i++) {
  await new Promise((r) => setTimeout(r, 500));
  try { pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); } catch { /* not up yet */ }
}
if (!pages) throw new Error('chrome debug port never came up');

const target = pages.find((e) => e.type === 'page') || pages.find((e) => e.type === 'browser');
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((res, rej) => { socket.addEventListener('open', res, { once: true }); socket.addEventListener('error', rej, { once: true }); });
let nextId = 0;
const pending = new Map();
socket.addEventListener('message', ({ data }) => {
  const m = JSON.parse(data);
  if (!m.id || !pending.has(m.id)) return;
  const { resolve, reject } = pending.get(m.id);
  pending.delete(m.id);
  if (m.error) reject(new Error(m.error.message)); else resolve(m.result);
});
const command = (method, params = {}) => {
  const id = ++nextId;
  socket.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
};

let sessionId = null;
const send = (method, params = {}) => command(method, sessionId ? { ...params, sessionId } : params);
const evaluate = async (expression) => {
  const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  return r.result.value;
};

let page = pages.find((e) => e.type === 'page');
if (!page) {
  const t = await command('Target.createTarget', { url: 'about:blank' });
  sessionId = (await command('Target.attachToTarget', { targetId: t.targetId, flatten: true })).sessionId;
}
await send('Runtime.enable');
await send('Page.enable');
await send('Page.navigate', { url: `http://127.0.0.1:${PORT}/poc/poc.html` });
for (let i = 0; i < 60; i++) {
  if (await evaluate('document.title') === 'ready') break;
  await new Promise((r) => setTimeout(r, 250));
}

let info;
try {
  info = await evaluate(`window.__bake(${JSON.stringify({ species, seed, size })})`);
} catch (err) {
  const diag = await evaluate('JSON.stringify({title: document.title, hasBake: typeof window.__bake, errors: window.__errors, logs: (window.__logs||[]).slice(-20)})');
  console.error('BAKE FAILED:', String(err).slice(0, 400));
  console.error('DIAG:', diag);
  chrome.kill();
  server.close();
  process.exit(1);
}
console.log(JSON.stringify(info, null, 2));

const require = createRequire(`${SEEDTHREE}/package.json`);
const sharp = require('sharp');
for (const view of info.views) {
  const b64 = await evaluate(`window.__done.${view}`);
  const raw = Buffer.from(b64, 'base64');
  const m = info.meta;
  let a0 = 0, aMid = 0, aHi = 0, a255 = 0;
  for (let i = 3; i < raw.length; i += 4) {
    const a = raw[i];
    if (a === 0) a0++; else if (a <= 128) aMid++; else if (a < 255) aHi++; else a255++;
  }
  console.log(`alpha: transparent=${a0} mid(1..128)=${aMid} high(129..254)=${aHi} opaque=${a255}`);
  const base = sharp(raw, { raw: { width: m.size, height: m.size, channels: 4 } });
  const crop = base.extract({ left: m.cropX, top: m.cropY, width: m.cropW, height: m.cropH });
  const slug = `${species}-seed${seed}-${view}`;
  await crop.clone().png().toFile(`${OUT}/${slug}.png`);
  await sharp(raw, { raw: { width: m.size, height: m.size, channels: 4 } })
    .extract({ left: m.cropX, top: m.cropY, width: m.cropW, height: m.cropH })
    .flatten({ background: '#8a8a8a' }).png().toFile(`${OUT}/${slug}-ongray.png`);
  console.log(`${OUT}/${slug}.png  ${m.cropW}x${m.cropH}`);
}

socket.close();
chrome.kill();
server.close();
process.exit(0);
