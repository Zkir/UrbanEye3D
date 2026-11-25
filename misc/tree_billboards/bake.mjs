// UrbanEye3D billboard bake driver (production successor of poc/run-poc.mjs).
//
//   node bake.mjs --jobs jobs.json --out OUT [--manifest FILE] [--flatten "#8a8a8a"]
//   node bake.mjs --list-species FILE     # SeedThree's own species registry (key/name/latin/biome)
//
// jobs.json: { "dilate": 4, "jobs": [ { "id", "species", "seed", "size", "views",
//              "controls", "lod", "dilate" } ] }   (views = azimuth degrees; 0 = front)
//
// For every job: bakes the albedo channel at the requested size in one long-lived
// headless Chrome page, crops the isotropic-frustum square RT to the tight
// aspect-correct box (sharp), writes <id>[-azN].png (transparent RGBA) and, with
// --flatten, <id>[-azN]-flat.png composited over the given background. A manifest
// (default OUT/bake-manifest.json, merged across runs by job id) records provenance:
// SeedThree git rev, seed, controls, real metres, crop box, alpha histogram.
//
// Requirements: SeedThree checkout (env SEEDTHREE_DIR, default D:/src/SeedThree) with
// `npm install` done (its sharp is reused), Chrome/Edge with working WebGPU
// (env CHROME_PATH to override). See docs/dev/SEEDTHREE_TREE_TEXTURES_PLAN.md.
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HARNESS = path.dirname(fileURLToPath(import.meta.url)).replace(/\\/g, '/');
const SEEDTHREE = (process.env.SEEDTHREE_DIR || 'D:/src/SeedThree').replace(/\\/g, '/').replace(/\/$/, '');
const PORT = Number(process.env.PROBE_PORT || 9455);

const argOf = (name, dflt = null) => {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 && process.argv[i + 1] !== undefined ? process.argv[i + 1] : dflt;
};
const jobsFile = argOf('jobs');
const listFile = argOf('list-species'); // dump SeedThree's own species registry and exit
const OUT = (argOf('out', `${HARNESS}/out`) || '').replace(/\\/g, '/');
const MANIFEST = argOf('manifest', `${OUT}/bake-manifest.json`).replace(/\\/g, '/');
const FLAT_BG = argOf('flatten', null); // hex color => also write flattened previews
if (!jobsFile && !listFile) {
  console.error('usage: node bake.mjs --jobs jobs.json --out DIR [--manifest FILE] [--flatten "#8a8a8a"]');
  console.error('   or: node bake.mjs --list-species FILE  (writes SeedThree\'s species registry JSON, no baking)');
  process.exit(2);
}
fs.mkdirSync(OUT, { recursive: true });

const require = createRequire(`${SEEDTHREE}/package.json`);
const sharp = require('sharp');

// SeedThree git rev without spawning git (read .git HEAD/refs directly; "unknown" if odd).
const seedthreeRev = (() => {
  try {
    const head = fs.readFileSync(`${SEEDTHREE}/.git/HEAD`, 'utf8').trim();
    if (head.startsWith('ref: ')) {
      const ref = head.slice(5);
      try { return fs.readFileSync(`${SEEDTHREE}/.git/${ref.replace(/\//g, path.sep)}`, 'utf8').trim(); } catch { /* fall through */ }
      const packed = fs.readFileSync(`${SEEDTHREE}/.git/packed-refs`, 'utf8');
      for (const line of packed.split('\n')) {
        const [sha, name] = line.trim().split(' ');
        if (name === ref) return sha;
      }
    }
    return /^[0-9a-f]{40}$/.test(head) ? head : 'unknown';
  } catch { return 'unknown'; }
})();

const findChrome = () => {
  const candidates = [
    process.env.CHROME_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  ].filter(Boolean);
  for (const c of candidates) if (fs.existsSync(c)) return c;
  throw new Error('no Chrome/Edge found — set CHROME_PATH');
};

// ---- static server: SeedThree at /, harness at /harness/ --------------------
const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.json': 'application/json', '.png': 'image/png', '.css': 'text/css', '.glb': 'model/gltf-binary',
};
const server = http.createServer((req, res) => {
  const url = decodeURIComponent((req.url || '/').split('?')[0]);
  let root = SEEDTHREE, rel = url;
  if (url.startsWith('/harness/')) { root = HARNESS; rel = url.slice(8); }
  const file = path.posix.normalize(path.posix.join(root, rel));
  if (!file.startsWith(root)) { res.writeHead(403).end('nope'); return; }
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404).end(`404 ${file}`); return; }
    res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream' });
    res.end(buf);
  });
});
await new Promise((r) => server.listen(PORT, '127.0.0.1', r));

// ---- headless Chrome over raw CDP (same transport the POC validated) --------
const profile = `${OUT}/intermediates/chrome-profile`;
fs.rmSync(profile, { recursive: true, force: true });
const chrome = spawn(findChrome(), [
  '--headless=new', `--remote-debugging-port=${PORT + 1}`, `--user-data-dir=${profile}`,
  '--enable-unsafe-webgpu', '--hide-scrollbars', '--disable-extensions', '--no-first-run',
], { stdio: 'ignore' });

const cleanup = (code) => {
  try { chrome.kill(); } catch { /* already gone */ }
  server.close();
  process.exit(code);
};

const cdpPort = PORT + 1;
let pages = null;
for (let i = 0; i < 40 && !pages; i++) {
  await new Promise((r) => setTimeout(r, 500));
  try { pages = await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json(); } catch { /* not up yet */ }
}
if (!pages) { console.error('chrome debug port never came up'); cleanup(2); }

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
await send('Page.navigate', { url: `http://127.0.0.1:${PORT}/harness/bake-page.html` });
let ready = false;
for (let i = 0; i < 120 && !ready; i++) {
  ready = (await evaluate('document.title')) === 'ready';
  if (!ready) await new Promise((r) => setTimeout(r, 250));
}
if (!ready) {
  const diag = await evaluate('JSON.stringify({title: document.title, errors: window.__errors})');
  console.error('bake page never became ready:', diag);
  cleanup(2);
}

if (listFile) {
  const list = await evaluate('JSON.stringify(window.__speciesList(), null, 2)');
  fs.writeFileSync(listFile, `${list}\n`);
  console.log(`species registry: ${listFile} (${JSON.parse(list).length} presets, rev ${seedthreeRev.slice(0, 8)})`);
  cleanup(0);
}

// ---- run the jobs ------------------------------------------------------------
const spec = JSON.parse(fs.readFileSync(jobsFile, 'utf8'));
const jobs = spec.jobs || [];
let manifest = { format: 'urbaney3d-tree-billboards-manifest/1', entries: {} };
if (fs.existsSync(MANIFEST)) {
  try { manifest = JSON.parse(fs.readFileSync(MANIFEST, 'utf8')); } catch { /* rewrite */ }
}
manifest.rev = seedthreeRev;
manifest.runAt = new Date().toISOString();

let okCount = 0; let failCount = 0;
for (const job of jobs) {
  const size = job.size ?? spec.size ?? 512;
  const views = job.views ?? [0];
  const bakeJob = {
    species: job.species, seed: job.seed, size, views,
    controls: job.controls ?? {}, lod: job.lod ?? {},
    dilate: job.dilate ?? spec.dilate ?? 4, margin: job.margin ?? spec.margin ?? 1.03,
  };
  let res;
  try {
    res = await evaluate(`window.__bake(${JSON.stringify(bakeJob)})`);
  } catch (err) {
    res = { ok: false, error: String(err).slice(0, 800) };
  }
  const entry = {
    id: job.id, species: job.species, seed: job.seed,
    controls: bakeJob.controls, lod: bakeJob.lod, size, views,
    rev: seedthreeRev, bakedAt: new Date().toISOString(),
  };
  if (!res?.ok) {
    entry.error = res?.error ?? 'bake returned no result';
    failCount += 1;
    console.error(`FAIL ${job.id}: ${entry.error.split('\n')[0]}`);
    manifest.entries[job.id] = entry;
    continue;
  }
  entry.ms = res.ms;
  entry.stats = res.stats;
  Object.assign(entry, res.meta);

  const alpha = { transparent: 0, mid: 0, high: 0, opaque: 0 };
  for (const view of res.views) {
    const name = res.views.length > 1 ? `${job.id}-${view}` : job.id;
    const b64 = await evaluate(`window.__pixTake(${JSON.stringify(view)})`);
    if (!b64) { entry.error = `missing pixels for view ${view}`; break; }
    const raw = Buffer.from(b64, 'base64');
    for (let y = res.meta.cropY; y < res.meta.cropY + res.meta.cropH; y++) {
      for (let x = res.meta.cropX; x < res.meta.cropX + res.meta.cropW; x++) {
        const a = raw[(y * size + x) * 4 + 3];
        if (a === 0) alpha.transparent += 1;
        else if (a <= 128) alpha.mid += 1;
        else if (a < 255) alpha.high += 1;
        else alpha.opaque += 1;
      }
    }
    entry.png = `${name}.png`;
    await sharp(raw, { raw: { width: size, height: size, channels: 4 } })
      .extract({ left: res.meta.cropX, top: res.meta.cropY, width: res.meta.cropW, height: res.meta.cropH })
      .png()
      .toFile(`${OUT}/${name}.png`);
    if (FLAT_BG) {
      entry.flatPng = `${name}-flat.png`;
      await sharp(raw, { raw: { width: size, height: size, channels: 4 } })
        .extract({ left: res.meta.cropX, top: res.meta.cropY, width: res.meta.cropW, height: res.meta.cropH })
        .flatten({ background: FLAT_BG })
        .png()
        .toFile(`${OUT}/${name}-flat.png`);
    }
  }
  entry.alpha = alpha;
  okCount += 1;
  console.log(`OK ${job.id} ${entry.realWidthMeters}x${entry.realHeightMeters}m `
    + `${res.meta.cropW}x${res.meta.cropH}px ${res.ms}ms alpha=${JSON.stringify(alpha)}`);
  manifest.entries[job.id] = entry;
}

fs.writeFileSync(MANIFEST, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`manifest: ${MANIFEST} (${okCount} ok, ${failCount} failed, rev ${seedthreeRev.slice(0, 8)})`);
cleanup(failCount === jobs.length && jobs.length > 0 ? 1 : 0);
