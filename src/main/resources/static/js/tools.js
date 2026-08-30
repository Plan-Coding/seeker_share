/* Seeker Share 工具箱 —— 全部工具纯前端实现，数据不出本机 */
(() => {
"use strict";

/* ================= 基础工具 ================= */
const $ = (selector, root = document) => root.querySelector(selector);

function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) continue;
        if (key === "class") node.className = value;
        else if (key === "text") node.textContent = value;
        else if (key === "value") node.value = value;
        else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2).toLowerCase(), value);
        else node.setAttribute(key, value);
    }
    for (const child of children.flat(Infinity)) {
        if (child === null || child === undefined || child === false) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

const textToBytes = (text) => new TextEncoder().encode(text);
const bytesToText = (bytes) => new TextDecoder("utf-8", {fatal: false}).decode(bytes);

async function copyValue(get) {
    const text = typeof get === "function" ? get() : get;
    if (!text) { showToast("没有可复制的内容", true); return; }
    await copyText(String(text));
    showToast("已复制到剪贴板");
}

const area = (value = "", rows = 6) => {
    const node = el("textarea", {class: "tool-area", spellcheck: "false", rows});
    node.value = value;
    return node;
};
const field = (label, ...nodes) => el("label", {class: "tool-field"}, el("span", {text: label}), ...nodes);
const input = (props = {}) => el("input", {class: "tool-input", spellcheck: "false", ...props});
const btn = (label, onclick, primary = false) => el("button", {type: "button", class: `tool-btn${primary ? " primary" : ""}`, onclick}, label);
const copyBtn = (get) => el("button", {type: "button", class: "tool-btn tiny", onclick: () => copyValue(get)}, "复制");
const outRow = (label, value, get) => el("div", {class: "out-row"},
    el("span", {class: "out-label", text: label}),
    el("div", {class: "out-value"}, value),
    get === undefined ? copyBtn(() => value.textContent) : (get === null ? null : copyBtn(get)));
const note = (text, cls = "") => el("p", {class: `tool-note ${cls}`.trim(), text});
const checkbox = (labelText, checked = true) => {
    const box = el("input", {type: "checkbox"});
    box.checked = checked;
    return {box, node: el("label", {class: "tool-check"}, box, el("span", {text: labelText}))};
};
const cap = (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
const pad2 = (n) => String(n).padStart(2, "0");

/* ================= 算法实现（纯 JS，无外部依赖） ================= */

/* ---- MD5 ---- */
const MD5_S = [7,12,17,22,7,12,17,22,7,12,17,22,7,12,17,22,5,9,14,20,5,9,14,20,5,9,14,20,5,9,14,20,
    4,11,16,23,4,11,16,23,4,11,16,23,4,11,16,23,6,10,15,21,6,10,15,21,6,10,15,21,6,10,15,21];
const MD5_K = Array.from({length: 64}, (_, i) => (Math.abs(Math.sin(i + 1)) * 4294967296) | 0);

function md5(bytes) {
    const len = bytes.length;
    const total = (((len + 8) >> 6) + 1) << 6;
    const msg = new Uint8Array(total);
    msg.set(bytes);
    msg[len] = 0x80;
    const dv = new DataView(msg.buffer);
    dv.setUint32(total - 8, (len * 8) >>> 0, true);
    dv.setUint32(total - 4, Math.floor(len * 8 / 4294967296), true);
    let a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
    const M = new Int32Array(16);
    for (let chunk = 0; chunk < total; chunk += 64) {
        for (let i = 0; i < 16; i++) M[i] = dv.getInt32(chunk + i * 4, true);
        let A = a0, B = b0, C = c0, D = d0;
        for (let i = 0; i < 64; i++) {
            let F, g;
            if (i < 16) { F = (B & C) | (~B & D); g = i; }
            else if (i < 32) { F = (D & B) | (~D & C); g = (5 * i + 1) % 16; }
            else if (i < 48) { F = B ^ C ^ D; g = (3 * i + 5) % 16; }
            else { F = C ^ (B | ~D); g = (7 * i) % 16; }
            F = (F + A + MD5_K[i] + M[g]) | 0;
            A = D; D = C; C = B;
            B = (B + ((F << MD5_S[i]) | (F >>> (32 - MD5_S[i])))) | 0;
        }
        a0 = (a0 + A) | 0; b0 = (b0 + B) | 0; c0 = (c0 + C) | 0; d0 = (d0 + D) | 0;
    }
    const out = new Uint8Array(16);
    const odv = new DataView(out.buffer);
    odv.setInt32(0, a0, true); odv.setInt32(4, b0, true); odv.setInt32(8, c0, true); odv.setInt32(12, d0, true);
    return [...out].map(b => b.toString(16).padStart(2, "0")).join("");
}

/* ---- SHA-1 ---- */
function sha1(bytes) {
    const len = bytes.length;
    const total = Math.ceil((len + 9) / 64) * 64;
    const msg = new Uint8Array(total);
    msg.set(bytes);
    msg[len] = 0x80;
    const dv = new DataView(msg.buffer);
    dv.setUint32(total - 8, Math.floor(len * 8 / 4294967296));
    dv.setUint32(total - 4, (len * 8) >>> 0);
    let h0 = 0x67452301, h1 = 0xefcdab89, h2 = 0x98badcfe, h3 = 0x10325476, h4 = 0xc3d2e1f0;
    const w = new Uint32Array(80);
    for (let off = 0; off < total; off += 64) {
        for (let i = 0; i < 16; i++) w[i] = dv.getUint32(off + i * 4);
        for (let i = 16; i < 80; i++) {
            const n = w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16];
            w[i] = (n << 1) | (n >>> 31);
        }
        let a = h0, b = h1, c = h2, d = h3, e = h4;
        for (let i = 0; i < 80; i++) {
            let f, k;
            if (i < 20) { f = (b & c) | (~b & d); k = 0x5a827999; }
            else if (i < 40) { f = b ^ c ^ d; k = 0x6ed9eba1; }
            else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8f1bbcdc; }
            else { f = b ^ c ^ d; k = 0xca62c1d6; }
            const t = (((a << 5) | (a >>> 27)) + f + e + k + w[i]) >>> 0;
            e = d; d = c; c = (b << 30) | (b >>> 2); b = a; a = t;
        }
        h0 = (h0 + a) >>> 0; h1 = (h1 + b) >>> 0; h2 = (h2 + c) >>> 0; h3 = (h3 + d) >>> 0; h4 = (h4 + e) >>> 0;
    }
    return [h0, h1, h2, h3, h4].map(x => x.toString(16).padStart(8, "0")).join("");
}

/* ---- SHA-256 ---- */
const SHA256_K = [
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];

function sha256(bytes) {
    const rotr = (x, n) => (x >>> n) | (x << (32 - n));
    const len = bytes.length;
    const total = Math.ceil((len + 9) / 64) * 64;
    const msg = new Uint8Array(total);
    msg.set(bytes);
    msg[len] = 0x80;
    new DataView(msg.buffer).setUint32(total - 4, (len * 8) >>> 0);
    const H = new Uint32Array([0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19]);
    const w = new Uint32Array(64);
    const dv = new DataView(msg.buffer);
    for (let off = 0; off < total; off += 64) {
        for (let i = 0; i < 16; i++) w[i] = dv.getUint32(off + i * 4);
        for (let i = 16; i < 64; i++) {
            const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
            const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
            w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
        }
        let [a, b, c, d, e, f, g, h] = H;
        for (let i = 0; i < 64; i++) {
            const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
            const ch = (e & f) ^ (~e & g);
            const t1 = (h + S1 + ch + SHA256_K[i] + w[i]) >>> 0;
            const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
            const maj = (a & b) ^ (a & c) ^ (b & c);
            const t2 = (S0 + maj) >>> 0;
            h = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
        }
        H[0] = (H[0] + a) >>> 0; H[1] = (H[1] + b) >>> 0; H[2] = (H[2] + c) >>> 0; H[3] = (H[3] + d) >>> 0;
        H[4] = (H[4] + e) >>> 0; H[5] = (H[5] + f) >>> 0; H[6] = (H[6] + g) >>> 0; H[7] = (H[7] + h) >>> 0;
    }
    return [...H].map(x => x.toString(16).padStart(8, "0")).join("");
}

/* ---- CRC32 ---- */
const CRC_TABLE = (() => {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c;
    }
    return table;
})();
function crc32(bytes) {
    let c = 0xffffffff;
    for (const byte of bytes) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
    return ((c ^ 0xffffffff) >>> 0).toString(16).padStart(8, "0");
}

/* ---- Cron 表达式 ---- */
const CRON_MONTHS = {jan:1,feb:2,mar:3,apr:4,may:5,jun:6,jul:7,aug:8,sep:9,oct:10,nov:11,dec:12};
const CRON_WEEKS = {sun:0,mon:1,tue:2,wed:3,thu:4,fri:5,sat:6};
const WEEK_NAMES = ["周日","周一","周二","周三","周四","周五","周六"];

function parseCronField(spec, min, max, names) {
    const values = new Set();
    const resolve = (token) => {
        const key = token.toLowerCase();
        if (names && names[key] !== undefined) return names[key];
        const num = Number(token);
        if (!Number.isInteger(num)) throw new Error(`无效取值 "${token}"`);
        return num;
    };
    for (const part of spec.split(",")) {
        const [rangePart, stepPart] = part.split("/");
        const step = stepPart === undefined ? 1 : Number(stepPart);
        if (!Number.isInteger(step) || step < 1) throw new Error(`无效步长 "${part}"`);
        let start, end;
        if (rangePart === "*" || rangePart === "?") { start = min; end = max; }
        else if (rangePart.includes("-")) {
            const [a, b] = rangePart.split("-");
            start = resolve(a); end = resolve(b);
        } else {
            start = end = resolve(rangePart);
            if (stepPart !== undefined) end = max;
        }
        if (start < min || end > max || start > end) throw new Error(`取值范围越界 "${part}"（应为 ${min}-${max}）`);
        for (let v = start; v <= end; v += step) values.add(names === CRON_WEEKS && v === 7 ? 0 : v);
    }
    return values;
}

function parseCron(expression) {
    const parts = expression.trim().split(/\s+/);
    if (parts.length < 5 || parts.length > 6) throw new Error("需要 5 或 6 个字段：分 时 日 月 周 [秒]");
    const hasSeconds = parts.length === 6;
    const [secondSpec, minuteSpec, hourSpec, domSpec, monthSpec, dowSpec] =
        hasSeconds ? parts : ["0", ...parts];
    const cron = {
        hasSeconds,
        seconds: parseCronField(secondSpec, 0, 59),
        minutes: parseCronField(minuteSpec, 0, 59),
        hours: parseCronField(hourSpec, 0, 23),
        daysOfMonth: parseCronField(domSpec, 1, 31),
        months: parseCronField(monthSpec, 1, 12, CRON_MONTHS),
        daysOfWeek: parseCronField(dowSpec, 0, 7, CRON_WEEKS),
        domStar: /^[\*\?]$/.test(domSpec),
        dowStar: /^[\*\?]$/.test(dowSpec)
    };
    cron.matches = (date) => {
        if (!cron.minutes.has(date.getMinutes()) || !cron.hours.has(date.getHours()) || !cron.months.has(date.getMonth() + 1)) return false;
        const domOk = cron.daysOfMonth.has(date.getDate());
        const dowOk = cron.daysOfWeek.has(date.getDay());
        if (!cron.domStar && !cron.dowStar) { if (!domOk && !dowOk) return false; }
        else if (!cron.domStar) { if (!domOk) return false; }
        else if (!cron.dowStar) { if (!dowOk) return false; }
        return true;
    };
    return cron;
}

function nextCronRuns(cron, count = 5, from = new Date()) {
    const runs = [];
    const cursor = new Date(from.getTime());
    cursor.setSeconds(0, 0);
    cursor.setMinutes(cursor.getMinutes() + 1);
    const limit = from.getTime() + 1000 * 60 * 60 * 24 * 366 * 2;
    let guard = 0;
    while (runs.length < count && cursor.getTime() < limit && guard++ < 1200000) {
        if (cron.matches(cursor)) {
            for (const second of [...cron.seconds].sort((a, b) => a - b)) {
                const date = new Date(cursor);
                date.setSeconds(second);
                if (date.getTime() > from.getTime()) runs.push(date);
            }
        }
        cursor.setMinutes(cursor.getMinutes() + 1);
    }
    return runs.slice(0, count);
}

function cronDescribe(cron) {
    const sorted = (set) => [...set].sort((a, b) => a - b);
    const segs = [];
    if (cron.months.size < 12) segs.push(`${sorted(cron.months).join("、")} 月`);
    if (!cron.domStar) segs.push(`每月 ${sorted(cron.daysOfMonth).join("、")} 日`);
    if (!cron.dowStar) segs.push(`每 ${sorted(cron.daysOfWeek).map(d => WEEK_NAMES[d]).join("、")}`);
    if (cron.hours.size === 24 && cron.minutes.size === 60) {
        segs.push("每分钟");
    } else if (cron.hours.size === 1 && cron.minutes.size === 1) {
        const h = sorted(cron.hours)[0], m = sorted(cron.minutes)[0];
        const s = cron.hasSeconds && cron.seconds.size === 1 ? `:${pad2(sorted(cron.seconds)[0])}` : "";
        segs.push(`每天 ${pad2(h)}:${pad2(m)}${s}`);
    } else {
        segs.push(`${cron.hours.size === 24 ? "每小时" : `${sorted(cron.hours).map(h => pad2(h)).join("、")} 点`}`);
        segs.push(`${cron.minutes.size === 60 ? "每分" : `${sorted(cron.minutes).map(m => pad2(m)).join("、")} 分`}`);
    }
    return segs.length ? segs.join(" · ") : "每分钟执行";
}

const CRON_CHEATS = [
    ["* * * * *", "每分钟"], ["*/5 * * * *", "每 5 分钟"], ["0 8 * * *", "每天 08:00"],
    ["30 8 * * 1-5", "工作日 08:30"], ["0 0 1 * *", "每月 1 日 00:00"], ["0 2 * * 0", "每周日 02:00"],
    ["0 9-18 * * *", "每天 9~18 点整点"], ["15,45 */2 * * *", "每 2 小时的 15、45 分"],
    ["0 0 * jan-mar *", "每年 1~3 月 0 点"], ["0 8 ? * MON", "每周一 08:00"]
];

/* ---- CIDR / IPv4 ---- */
function parseIPv4(text) {
    const octets = text.trim().split(".");
    if (octets.length !== 4) throw new Error("IPv4 地址需要 4 段，例如 192.168.1.10");
    return octets.reduce((acc, part) => {
        const value = Number(part);
        if (!/^\d{1,3}$/.test(part) || value > 255) throw new Error(`非法地址段 "${part}"`);
        return (acc << 8 | value) >>> 0;
    }, 0) >>> 0;
}
const intToIp = (int) => [int >>> 24, int >>> 16 & 255, int >>> 8 & 255, int & 255].join(".");
const ipToBinary = (int) => [int >>> 24, int >>> 16 & 255, int >>> 8 & 255, int & 255].map(v => v.toString(2).padStart(8, "0")).join(".");
const ipType = (int) => {
    const a = int >>> 24, b = int >>> 16 & 255;
    if (a === 127) return "环回地址";
    if (a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168)) return "私有地址";
    if (a === 169 && b === 254) return "链路本地";
    if (a === 100 && b >= 64 && b <= 127) return "运营商 NAT";
    if (a === 0) return "未指定";
    return "公网地址";
};

function computeCidr(input) {
    const [ipPart, prefixPart = "32"] = input.trim().split("/");
    const prefix = Number(prefixPart);
    if (!/^\d{1,2}$/.test(prefixPart) || prefix > 32) throw new Error("前缀长度应为 0-32，例如 /24");
    const ip = parseIPv4(ipPart);
    const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0;
    const network = (ip & mask) >>> 0;
    const broadcast = (network | ~mask) >>> 0;
    const hosts = 2 ** (32 - prefix);
    const usable = prefix <= 30 ? hosts - 2 : hosts;
    return {
        network: intToIp(network), broadcast: intToIp(broadcast), mask: intToIp(mask),
        wildcard: intToIp(~mask >>> 0), first: prefix <= 30 ? intToIp(network + 1) : intToIp(network),
        last: prefix <= 30 ? intToIp(broadcast - 1) : intToIp(broadcast),
        hosts, usable, prefix, type: ipType(ip), binary: ipToBinary(network), maskBinary: ipToBinary(mask),
        class: ip >>> 31 ? "C 类及以后" : ip >>> 30 & 1 ? "B 类" : "A 类"
    };
}

/* ---- 行级 diff ---- */
function diffLines(aText, bText) {
    const a = aText.split("\n");
    const b = bText.split("\n");
    const n = a.length, m = b.length;
    if (n * m > 4000000) throw new Error("文本过长，请缩小对比范围");
    const dp = Array.from({length: n + 1}, () => new Uint32Array(m + 1));
    for (let i = n - 1; i >= 0; i--)
        for (let j = m - 1; j >= 0; j--)
            dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
    const rows = [];
    let i = 0, j = 0;
    while (i < n && j < m) {
        if (a[i] === b[j]) { rows.push({t: "ctx", line: a[i], a: i + 1, b: j + 1}); i++; j++; }
        else if (dp[i + 1][j] >= dp[i][j + 1]) { rows.push({t: "del", line: a[i], a: i + 1}); i++; }
        else { rows.push({t: "add", line: b[j], b: j + 1}); j++; }
    }
    while (i < n) { rows.push({t: "del", line: a[i], a: i + 1}); i++; }
    while (j < m) { rows.push({t: "add", line: b[j], b: j + 1}); j++; }
    return rows;
}

/* ---- 命名风格 ---- */
function splitWords(text) {
    return text
        .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
        .replace(/([A-Z]+)([A-Z][a-z])/g, "$1 $2")
        .split(/[^A-Za-z0-9]+/)
        .filter(Boolean);
}

/* ---- 颜色 ---- */
function hexToRgb(hex) {
    const match = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex.trim());
    if (!match) throw new Error("HEX 格式应为 #RRGGBB");
    let value = match[1];
    if (value.length === 3) value = [...value].map(c => c + c).join("");
    return [0, 2, 4].map(i => parseInt(value.slice(i, i + 2), 16));
}
function rgbToHsl(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
    let h = 0;
    if (d) {
        if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
        else if (max === g) h = ((b - r) / d + 2) * 60;
        else h = ((r - g) / d + 4) * 60;
    }
    const l = (max + min) / 2;
    const s = d ? d / (1 - Math.abs(2 * l - 1)) : 0;
    return [Math.round(h), Math.round(s * 100), Math.round(l * 100)];
}

/* ---- Base64 ---- */
function b64encode(text) {
    const bytes = textToBytes(text);
    let binary = "";
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    return btoa(binary);
}
function b64decode(text) {
    const binary = atob(text.replace(/\s+/g, ""));
    return bytesToText(Uint8Array.from(binary, c => c.charCodeAt(0)));
}
function b64urlDecode(text) {
    return b64decode(text.replace(/-/g, "+").replace(/_/g, "/"));
}

/* ---- 摩斯电码 ---- */
const MORSE_MAP = {
    a:".-",b:"-...",c:"-.-.",d:"-..",e:".",f:"..-.",g:"--.",h:"....",i:"..",j:".---",k:"-.-",l:".-..",m:"--",
    n:"-.",o:"---",p:".--.",q:"--.-",r:".-.",s:"...",t:"-",u:"..-",v:"...-",w:".--",x:"-..-",y:"-.--",z:"--..",
    "0":"-----","1":".----","2":"..---","3":"...--","4":"....-","5":".....","6":"-....","7":"--...","8":"---..","9":"----.",
    ".":".-.-.-",",":"--..--","?":"..--..","!":"-.-.--","/":"-..-.","@":".--.-.","(":"-.--.",")":"-.--.-","-":"-....-",
    "=":"-...-",":":"---...","'":".----.",'"':".-..-.","+":".-.-.",";":"-.-.-."
};
const MORSE_REVERSE = Object.fromEntries(Object.entries(MORSE_MAP).map(([k, v]) => [v, k]));

/* ---- 进制 ---- */
const RADIX_DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";
function parseInBase(text, base) {
    let body = text.trim().toLowerCase();
    let sign = 1n;
    if (body.startsWith("-")) { sign = -1n; body = body.slice(1); }
    if (body.startsWith("+")) body = body.slice(1);
    if (!body) throw new Error("请输入数字");
    let result = 0n;
    const big = BigInt(base);
    for (const ch of body) {
        const v = RADIX_DIGITS.indexOf(ch);
        if (v < 0 || v >= base) throw new Error(`${base} 进制中不存在字符 "${ch}"`);
        result = result * big + BigInt(v);
    }
    return sign * result;
}

/* ---- 假文生成 ---- */
const LOREM_WORDS = ("lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua enim ad minim veniam quis nostrud exercitation ullamco laboris nisi aliquip ex ea commodo consequat duis aute irure in reprehenderit voluptate velit esse cillum eu fugiat nulla pariatur excepteur sint occaecat cupidatat non proident sunt culpa qui officia deserunt mollit anim id est laborum").split(" ");

/* ---- 汉字数据访问（数据来自 hanzi-data.js） ---- */
const HANZI_ALPH = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
const hanziDecode = (code) => HANZI_ALPH.indexOf(code[0]) * 62 + HANZI_ALPH.indexOf(code[1]);

function hanziLookup(ch) {
    const i = HANZI_CHARS.indexOf(ch);
    if (i < 0) return null;
    const primary = HANZI_SYLLABLES[hanziDecode(HANZI_PY.substr(i * 2, 2))];
    const extras = (HANZI_EXTRA[ch] || "").split("|").filter(Boolean).map(c => HANZI_SYLLABLES[hanziDecode(c)]);
    return {char: ch, pinyin: [primary, ...extras], strokes: HANZI_STROKES.charCodeAt(i) - 0x30};
}

/* ---- 拼音声调处理 ---- */
const TONE_CHARS = {"ā":["a",1],"á":["a",2],"ǎ":["a",3],"à":["a",4],"ē":["e",1],"é":["e",2],"ě":["e",3],"è":["e",4],"ī":["i",1],"í":["i",2],"ǐ":["i",3],"ì":["i",4],"ō":["o",1],"ó":["o",2],"ǒ":["o",3],"ò":["o",4],"ū":["u",1],"ú":["u",2],"ǔ":["u",3],"ù":["u",4],"ǖ":["ü",1],"ǘ":["ü",2],"ǚ":["ü",3],"ǜ":["ü",4],"ń":["n",2],"ň":["n",3],"ǹ":["n",4],"ḿ":["m",2]};
function splitSyllable(syl) {
    for (const ch of syl) {
        const t = TONE_CHARS[ch];
        if (t) return {base: syl.replace(ch, t[0]), tone: t[1]};
    }
    return {base: syl, tone: 5};
}
function formatPinyin(syl, style) {
    const {base, tone} = splitSyllable(syl);
    if (style === "num") return base + (tone === 5 ? "" : tone);
    if (style === "plain") return base;
    return syl;
}
const capFirst = (s) => s ? s.charAt(0).toUpperCase() + s.slice(1) : s;

/* ================= 工具注册表 ================= */
const CATEGORIES = [
    {id: "text", name: "文本处理"},
    {id: "encode", name: "编码转换"},
    {id: "crypto", name: "安全加密"},
    {id: "parse", name: "解析校验"},
    {id: "net", name: "网络运维"},
    {id: "learn", name: "学习工具"},
    {id: "misc", name: "实用杂项"}
];

const TOOLS = [
    /* ---------- 文本处理 ---------- */
    {
        id: "case", cat: "text", icon: "Aa", name: "命名风格转换",
        desc: "大小写、驼峰、下划线、中划线等命名格式一键互转",
        render(body) {
            const src = area("hello world example\nSeekerShare API token");
            const grid = el("div", {class: "out-grid"});
            const formats = [
                ["UPPER CASE", w => w.join(" ").toUpperCase()],
                ["lower case", w => w.join(" ").toLowerCase()],
                ["camelCase", w => w.map((x, i) => i ? cap(x) : x.toLowerCase()).join("")],
                ["PascalCase", w => w.map(cap).join("")],
                ["snake_case", w => w.map(x => x.toLowerCase()).join("_")],
                ["kebab-case", w => w.map(x => x.toLowerCase()).join("-")],
                ["CONSTANT_CASE", w => w.map(x => x.toUpperCase()).join("_")],
                ["Title Case", w => w.map(cap).join(" ")],
                ["dot.case", w => w.map(x => x.toLowerCase()).join(".")]
            ];
            const update = () => {
                const words = splitWords(src.value);
                grid.replaceChildren(...formats.map(([label, fn]) => {
                    const value = words.length ? fn(words) : "";
                    return outRow(label, value || el("span", {class: "muted", text: "—"}), value);
                }));
            };
            src.addEventListener("input", update);
            update();
            body.append(field("输入文本（支持空格 / 下划线 / 中划线 / 驼峰混排）", src), grid);
        }
    },
    {
        id: "lines", cat: "text", icon: "≡", name: "行处理工具",
        desc: "排序、去重、去空行、加行号、打乱等批量行操作",
        render(body) {
            const src = area("banana\napple\n\ncherry\napple\nbanana\ndragon fruit", 9);
            const count = el("span", {class: "tool-count"});
            const update = () => {
                const lines = src.value ? src.value.split("\n") : [];
                count.textContent = `${lines.length} 行 · ${src.value.length} 字符`;
            };
            const apply = (fn) => {
                const lines = fn(src.value.split("\n"));
                src.value = lines.join("\n");
                update();
                showToast(`已处理，当前 ${lines.length} 行`);
            };
            const ops = [
                ["升序排序", a => [...a].sort((x, y) => x.localeCompare(y, "zh"))],
                ["降序排序", a => [...a].sort((x, y) => y.localeCompare(x, "zh"))],
                ["倒序排列", a => [...a].reverse()],
                ["去除重复", a => [...new Set(a)]],
                ["去除空行", a => a.filter(x => x.trim())],
                ["修剪空白", a => a.map(x => x.trim())],
                ["添加行号", a => a.map((x, i) => `${i + 1}. ${x}`)],
                ["随机打乱", a => {
                    const b = [...a];
                    for (let i = b.length - 1; i > 0; i--) {
                        const j = Math.floor(Math.random() * (i + 1));
                        [b[i], b[j]] = [b[j], b[i]];
                    }
                    return b;
                }]
            ];
            src.addEventListener("input", update);
            update();
            body.append(field("输入多行文本", src),
                el("div", {class: "tool-actions"}, ops.map(([label, fn]) => btn(label, () => apply(fn))), count),
                copyBtn(() => src.value));
        }
    },
    {
        id: "stats", cat: "text", icon: "+", name: "文本统计",
        desc: "字符、汉字、单词、行数、字节数等实时统计",
        render(body) {
            const src = area("Seeker Share 是一个轻量的局域网共享节点。\nPaste your text here.", 9);
            const grid = el("div", {class: "stat-grid"});
            const metrics = [
                ["字符数", s => [...s].length],
                ["非空白字符", s => s.replace(/\s/g, "").length],
                ["汉字", s => (s.match(/[\u4e00-\u9fff]/g) || []).length],
                ["单词数", s => (s.match(/[A-Za-z0-9_']+|[\u4e00-\u9fff]/g) || []).length],
                ["行数", s => s ? s.split("\n").length : 0],
                ["段落数", s => s.split(/\n\s*\n+/).filter(x => x.trim()).length],
                ["句数", s => (s.match(/[.!?。！？]+/g) || []).length],
                ["UTF-8 字节", s => textToBytes(s).length]
            ];
            const update = () => grid.replaceChildren(...metrics.map(([label, fn]) =>
                el("div", {class: "stat-cell"}, el("strong", {text: fn(src.value)}), el("span", {text: label}))));
            src.addEventListener("input", update);
            update();
            body.append(field("输入文本", src), grid);
        }
    },
    {
        id: "diff", cat: "text", icon: "±", name: "文本对比",
        desc: "按行对比两段文本的差异，绿色为新增，红色为删除",
        render(body) {
            const left = area("server.port=8080\nserver.address=0.0.0.0\nmax.file=100MB", 6);
            const right = area("server.port=8080\nserver.address=127.0.0.1\nmax.file=100MB\nmode=lan", 6);
            const result = el("div", {class: "diff-result"});
            const summary = note("等待对比");
            const run = () => {
                try {
                    const rows = diffLines(left.value, right.value);
                    summary.textContent = `共 ${rows.length} 行：${rows.filter(r => r.t === "add").length} 行新增 · ${rows.filter(r => r.t === "del").length} 行删除`;
                    summary.className = "tool-note ok";
                    result.replaceChildren(...rows.map(row => {
                        const gutter = row.t === "del" ? `- ${row.a ?? ""}` : row.t === "add" ? `+ ${row.b ?? ""}` : `${row.a}`;
                        return el("div", {class: `diff-line diff-${row.t}`},
                            el("span", {class: "diff-gutter", text: gutter}),
                            el("span", {text: row.line || " "}));
                    }));
                } catch (error) {
                    summary.textContent = error.message;
                    summary.className = "tool-note error";
                }
            };
            body.append(
                el("div", {class: "diff-grid"}, field("原始文本 A", left), field("修改后文本 B", right)),
                el("div", {class: "tool-actions"}, btn("开始对比", run, true), summary),
                result);
            run();
        }
    },
    {
        id: "morse", cat: "text", icon: "·−", name: "摩斯电码",
        desc: "文本与摩斯电码互转，支持字母、数字与常用符号",
        render(body) {
            const src = area("SOS SEEKER", 5);
            const out = area("", 5);
            out.readOnly = true;
            const encode = () => [...src.value.toLowerCase()].map(ch => {
                if (ch === " ") return "/";
                return MORSE_MAP[ch] ?? "";
            }).filter(Boolean).join(" ");
            const decode = () => src.value.trim().split(/\s*\/\s*/).map(word =>
                word.trim().split(/\s+/).map(code => MORSE_REVERSE[code] ?? "?").join("")).join(" ");
            const convert = () => {
                const mode = selector.value;
                try {
                    if (mode === "encode") out.value = encode();
                    else if (mode === "decode") out.value = decode();
                    else out.value = /^[.\-\s/]+$/.test(src.value.trim()) && /[.\-]/.test(src.value) ? decode() : encode();
                } catch (error) { out.value = ""; showToast(error.message, true); }
            };
            const selector = el("select", {class: "tool-input sm", onchange: convert},
                el("option", {value: "auto", text: "自动识别"}),
                el("option", {value: "encode", text: "编码 →"}),
                el("option", {value: "decode", text: "← 解码"}));
            src.addEventListener("input", convert);
            convert();
            body.append(field("输入内容", src),
                el("div", {class: "tool-actions"}, selector, copyBtn(() => out.value)),
                field("转换结果", out));
        }
    },
    {
        id: "width", cat: "text", icon: "◧", name: "全角/半角转换",
        desc: "中文全角与英文半角字符互转，含标点符号",
        render(body) {
            const src = area("Ｈｅｌｌｏ，世界！你好 123 ＡＢＣ。", 5);
            const out = area("", 5);
            out.readOnly = true;
            const toHalf = (s) => s.replace(/[\uFF01-\uFF5E]/g, c => String.fromCharCode(c.charCodeAt(0) - 0xFEE0)).replace(/\u3000/g, " ");
            const toFull = (s) => s.replace(/[\u0021-\u007E]/g, c => String.fromCharCode(c.charCodeAt(0) + 0xFEE0)).replace(/ /g, "\u3000");
            const run = (fn, label, toast = true) => { out.value = fn(src.value); if (toast) showToast(label + "完成"); };
            body.append(field("输入文本", src),
                el("div", {class: "tool-actions"},
                    btn("全角 → 半角", () => run(toHalf, "全角转半角"), true),
                    btn("半角 → 全角", () => run(toFull, "半角转全角")),
                    copyBtn(() => out.value)),
                field("输出结果", out));
            run(toHalf, "", false);
        }
    },

    /* ---------- 编码转换 ---------- */
    {
        id: "base64", cat: "encode", icon: "64", name: "Base64 编解码",
        desc: "UTF-8 安全的 Base64 编码与解码",
        render(body) {
            const src = area("Seeker Share 局域网共享", 5);
            const out = area("", 5);
            out.readOnly = true;
            const act = (mode) => {
                try {
                    out.value = mode === "encode" ? b64encode(src.value) : b64decode(src.value);
                    if (mode === "encode") showToast("已编码");
                    else showToast("已解码");
                } catch (error) {
                    out.value = "";
                    showToast(`解码失败：输入不是合法 Base64`, true);
                }
            };
            body.append(field("输入文本或 Base64", src),
                el("div", {class: "tool-actions"}, btn("编码", () => act("encode"), true), btn("解码", () => act("decode")), copyBtn(() => out.value)),
                field("输出结果", out));
        }
    },
    {
        id: "urlcode", cat: "encode", icon: "%", name: "URL 编解码",
        desc: "URL 组件与完整 URI 的百分号编码、解码",
        render(body) {
            const src = area("https://seeker.local/search?q=局域网 共享&page=2", 5);
            const out = area("", 5);
            out.readOnly = true;
            const actions = [
                ["组件编码", () => encodeURIComponent(src.value)],
                ["组件解码", () => decodeURIComponent(src.value)],
                ["URI 编码", () => encodeURI(src.value)],
                ["URI 解码", () => decodeURI(src.value)]
            ];
            const run = (fn, label) => {
                try {
                    out.value = fn(src.value);
                    showToast(label + "完成");
                } catch (error) { out.value = ""; showToast(`${label}失败：输入格式不合法`, true); }
            };
            body.append(field("输入内容", src),
                el("div", {class: "tool-actions"}, actions.map(([label, fn]) => btn(label, () => run(fn, label))), copyBtn(() => out.value)),
                field("输出结果", out));
        }
    },
    {
        id: "htmlentity", cat: "encode", icon: "&", name: "HTML 实体",
        desc: "HTML 特殊字符转义与实体还原，防注入场景常用",
        render(body) {
            const src = area('<div class="msg">Tom & Jerry "say hi"</div>', 5);
            const out = area("", 5);
            out.readOnly = true;
            const escape = () => src.value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
            const unescape = () => new DOMParser().parseFromString(src.value, "text/html").documentElement.textContent;
            body.append(field("输入内容", src),
                el("div", {class: "tool-actions"},
                    btn("转义 Escape", () => { out.value = escape(); showToast("已转义"); }, true),
                    btn("还原 Unescape", () => { out.value = unescape(); showToast("已还原"); }),
                    copyBtn(() => out.value)),
                field("输出结果", out));
        }
    },
    {
        id: "unicode", cat: "encode", icon: "u", name: "Unicode 转义",
        desc: "文本与 \\uXXXX 转义序列互转，支持代理对与码点查看",
        render(body) {
            const src = area("局域网共享 Seeker", 5);
            const out = area("", 5);
            out.readOnly = true;
            const encode = () => [...src.value].map(ch => {
                const cp = ch.codePointAt(0);
                if (cp < 128) return ch;
                if (cp > 0xffff) {
                    const high = Math.floor((cp - 0x10000) / 0x400) + 0xd800;
                    const low = (cp - 0x10000) % 0x400 + 0xdc00;
                    return `\\u${high.toString(16).padStart(4, "0")}\\u${low.toString(16).padStart(4, "0")}`;
                }
                return `\\u${cp.toString(16).padStart(4, "0")}`;
            }).join("");
            const decode = () => src.value.replace(/\\u\{([0-9a-f]{1,6})\}|\\u([0-9a-f]{4})/gi, (_, brace, plain) =>
                String.fromCodePoint(parseInt(brace || plain, 16)));
            const run = (fn, label) => {
                try { out.value = fn(src.value); showToast(label + "完成"); }
                catch (error) { out.value = ""; showToast(`${label}失败：${error.message}`, true); }
            };
            const points = () => {
                const chars = [...src.value].slice(0, 200);
                return chars.map(ch => `U+${ch.codePointAt(0).toString(16).toUpperCase().padStart(4, "0")}`).join(" ");
            };
            body.append(field("输入内容", src),
                el("div", {class: "tool-actions"},
                    btn("转义", () => run(encode, "转义"), true),
                    btn("还原", () => run(decode, "还原")),
                    copyBtn(() => out.value)),
                field("输出结果", out),
                field("码点列表（前 200 个字符）", (() => { const p = el("div", {class: "out-value"}); src.addEventListener("input", () => p.textContent = points()); p.textContent = points(); return p; })()));
        }
    },
    {
        id: "radix", cat: "encode", icon: "0x", name: "进制转换",
        desc: "2/8/10/16/36 进制任意互转，支持大整数",
        render(body) {
            const src = input({value: "20260829", placeholder: "输入数字"});
            const base = el("select", {class: "tool-input sm", onchange: update},
                ...[2, 8, 10, 16, 36].map(b => el("option", {value: b, text: `${b} 进制${b === 10 ? "（默认）" : ""}`})));
            base.value = "10";
            const grid = el("div", {class: "out-grid"});
            const names = {2: "二进制", 8: "八进制", 10: "十进制", 16: "十六进制", 36: "三十六进制"};
            function update() {
                try {
                    const value = parseInBase(src.value, Number(base.value));
                    grid.replaceChildren(...Object.entries(names).map(([b, label]) => {
                        const text = value.toString(Number(b)).toUpperCase();
                        return outRow(label, text, text);
                    }));
                } catch (error) {
                    grid.replaceChildren(el("div", {class: "out-row"}, el("span", {class: "out-label", text: "错误"}), el("div", {class: "out-value"}, el("span", {class: "muted", text: error.message})), null));
                }
            }
            src.addEventListener("input", update);
            update();
            body.append(el("div", {class: "tool-actions"},
                el("div", {style: "flex:1"}, field("输入数字", src)),
                el("div", {style: "width:150px"}, field("源进制", base))), grid);
        }
    },
    {
        id: "jwt", cat: "encode", icon: "JWT", name: "JWT 解码",
        desc: "解码 JWT 的 Header 与 Payload，自动翻译时间字段",
        render(body) {
            const src = area("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzZWVrZXIiLCJuYW1lIjoiTm9kZS0wMSIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDgzNjAwfQ.dQw4w9WgXcQ_signature", 5);
            const result = el("div", {class: "out-grid"});
            const update = () => {
                const parts = src.value.trim().split(".");
                if (parts.length < 2) {
                    result.replaceChildren(note("JWT 至少包含 Header.Payload 两段，用 . 分隔", "error"));
                    return;
                }
                const decodePart = (part) => {
                    const json = b64urlDecode(part);
                    return JSON.stringify(JSON.parse(json), null, 2);
                };
                const rows = [];
                try {
                    const header = decodePart(parts[0]);
                    const payload = decodePart(parts[1]);
                    rows.push(outRow("Header", header, header));
                    rows.push(outRow("Payload", payload, payload));
                    const claims = JSON.parse(payload);
                    for (const key of ["iat", "nbf", "exp"]) {
                        if (claims[key] !== undefined) {
                            const date = new Date(claims[key] * 1000);
                            rows.push(outRow(key.toUpperCase(), `${date.toLocaleString("zh-CN")}（${claims[key]}）`, null));
                        }
                    }
                    rows.push(note(parts.length === 3 ? "签名段已跳过：本工具不校验签名，请勿以此验证真伪。" : "缺少签名段（第 3 段）。"));
                } catch (error) {
                    result.replaceChildren(note(`解码失败：${error.message}`, "error"));
                    return;
                }
                result.replaceChildren(...rows);
            };
            src.addEventListener("input", update);
            update();
            body.append(field("粘贴 JWT Token", src), result);
        }
    },

    /* ---------- 安全加密 ---------- */
    {
        id: "hash", cat: "crypto", icon: "#", name: "哈希计算",
        desc: "MD5 / CRC32 / SHA-1 / SHA-256 实时计算，纯前端实现",
        render(body) {
            const src = area("Seeker Share", 4);
            const rows = ["MD5", "CRC32", "SHA-1", "SHA-256"].map(label => {
                const value = el("div", {class: "out-value"}, el("span", {class: "muted", text: "—"}));
                return {label, value, row: outRow(label, value, () => value.textContent)};
            });
            let timer;
            const update = () => {
                clearTimeout(timer);
                timer = setTimeout(() => {
                    if (!src.isConnected) return;
                    const bytes = textToBytes(src.value);
                    const results = {MD5: md5(bytes), CRC32: crc32(bytes).toUpperCase(), "SHA-1": sha1(bytes), "SHA-256": sha256(bytes)};
                    for (const row of rows) row.value.textContent = results[row.label];
                }, 200);
            };
            src.addEventListener("input", update);
            update();
            body.append(field("输入任意文本", src), el("div", {class: "out-grid"}, rows.map(r => r.row)),
                note("算法为纯 JavaScript 实现，无需 HTTPS 环境；输入仅在本机计算。"));
        }
    },
    {
        id: "password", cat: "crypto", icon: "✳", name: "密码生成器",
        desc: "按字符集与长度批量生成高强度随机密码",
        render(body) {
            const upper = checkbox("大写 A-Z");
            const lower = checkbox("小写 a-z");
            const digits = checkbox("数字 0-9");
            const symbols = checkbox("符号 !@#$");
            const length = input({type: "number", value: "16", min: "6", max: "128", class: "tool-input sm"});
            const count = input({type: "number", value: "5", min: "1", max: "20", class: "tool-input sm"});
            const list = el("div", {class: "gen-list"});
            const entropy = note("");
            const generate = () => {
                let pool = "";
                if (upper.box.checked) pool += "ABCDEFGHJKLMNPQRSTUVWXYZ";
                if (lower.box.checked) pool += "abcdefghijkmnpqrstuvwxyz";
                if (digits.box.checked) pool += "23456789";
                if (symbols.box.checked) pool += "!@#$%^&*()-_=+[]{};:,.?";
                if (!pool) { showToast("请至少选择一种字符集", true); return; }
                const len = Math.min(128, Math.max(6, Number(length.value) || 16));
                const total = Math.min(20, Math.max(1, Number(count.value) || 5));
                const random = new Uint32Array(len * total);
                crypto.getRandomValues(random);
                list.replaceChildren(...Array.from({length: total}, (_, i) => {
                    const text = [...random.slice(i * len, i * len + len)].map(n => pool[n % pool.length]).join("");
                    return el("div", {class: "gen-item"}, el("span", {text}), copyBtn(text));
                }));
                entropy.textContent = `强度估算：每个密码约 ${Math.round(len * Math.log2(pool.length))} bits 熵 · 字符池 ${pool.length} 个`;
            };
            body.append(el("div", {class: "tool-actions"}, upper.node, lower.node, digits.node, symbols.node),
                el("div", {class: "tool-actions"},
                    el("div", {}, field("长度", length)), el("div", {}, field("数量", count)),
                    btn("生成密码", generate, true), copyBtn(() => [...list.querySelectorAll(".gen-item span")].map(s => s.textContent).join("\n"))),
                list, entropy);
            generate();
        }
    },
    {
        id: "uuid", cat: "crypto", icon: "ID", name: "UUID 生成器",
        desc: "批量生成 UUID v4，基于加密级随机数",
        render(body) {
            const count = input({type: "number", value: "5", min: "1", max: "100", class: "tool-input sm"});
            const upper = checkbox("大写", false);
            const list = el("div", {class: "gen-list"});
            const generate = () => {
                const total = Math.min(100, Math.max(1, Number(count.value) || 5));
                const bytes = new Uint8Array(total * 16);
                crypto.getRandomValues(bytes);
                list.replaceChildren(...Array.from({length: total}, (_, i) => {
                    const b = [...bytes.slice(i * 16, i * 16 + 16)];
                    b[6] = b[6] & 0x0f | 0x40;
                    b[8] = b[8] & 0x3f | 0x80;
                    const hex = b.map(x => x.toString(16).padStart(2, "0")).join("");
                    let uuid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
                    if (upper.box.checked) uuid = uuid.toUpperCase();
                    return el("div", {class: "gen-item"}, el("span", {text: uuid}), copyBtn(uuid));
                }));
            };
            count.addEventListener("change", generate);
            upper.box.addEventListener("change", generate);
            body.append(el("div", {class: "tool-actions"}, el("div", {}, field("生成数量", count)), upper.node,
                btn("生成 UUID", generate, true), copyBtn(() => [...list.querySelectorAll(".gen-item span")].map(s => s.textContent).join("\n"))),
                list);
            generate();
        }
    },

    /* ---------- 解析校验 ---------- */
    {
        id: "json", cat: "parse", icon: "{}", name: "JSON 格式化",
        desc: "格式化、压缩、校验 JSON，支持递归排序键名",
        render(body) {
            const src = area('{"name":"seeker-share","tags":["lan","share"],"config":{"port":8080,"storage":1073741824}}', 7);
            const out = area("", 8);
            out.readOnly = true;
            const sortValue = (value) => {
                if (Array.isArray(value)) return value.map(sortValue);
                if (value && typeof value === "object") {
                    const sorted = {};
                    for (const key of Object.keys(value).sort()) sorted[key] = sortValue(value[key]);
                    return sorted;
                }
                return value;
            };
            const run = (label, fn) => {
                try {
                    const parsed = JSON.parse(src.value);
                    out.value = fn(parsed);
                    showToast(label + "成功");
                } catch (error) { out.value = ""; showToast(`JSON 解析失败：${error.message}`, true); }
            };
            body.append(field("输入 JSON", src),
                el("div", {class: "tool-actions"},
                    btn("格式化 2 空格", () => run("格式化", v => JSON.stringify(v, null, 2)), true),
                    btn("格式化 4 空格", () => run("格式化", v => JSON.stringify(v, null, 4))),
                    btn("压缩", () => run("压缩", v => JSON.stringify(v))),
                    btn("键名排序", () => run("排序", v => JSON.stringify(sortValue(v), null, 2))),
                    copyBtn(() => out.value)),
                field("输出结果", out));
        }
    },
    {
        id: "regex", cat: "parse", icon: ".*", name: "正则测试器",
        desc: "实时测试正则表达式，高亮匹配并查看捕获分组",
        render(body) {
            const pattern = input({value: "(\\w+)@(\\w+\\.\\w+)", placeholder: "正则表达式"});
            const flags = input({value: "g", class: "tool-input sm", placeholder: "gimsuy"});
            const src = area("联系 support@seeker.local 或 admin@lan.net 获取帮助。\n备份节点 backup@node.local。", 5);
            const status = note("");
            const highlight = el("div", {class: "regex-highlight", "aria-hidden": "true"});
            const tableWrap = el("div", {});
            const update = () => {
                let re;
                try {
                    const clean = [...new Set(flags.value)].join("").replace(/[^gimsuy]/g, "");
                    re = new RegExp(pattern.value, clean.includes("g") ? clean : clean + "g");
                } catch (error) {
                    status.textContent = `正则语法错误：${error.message}`;
                    status.className = "tool-note error";
                    highlight.textContent = src.value;
                    tableWrap.replaceChildren();
                    return;
                }
                const frag = document.createDocumentFragment();
                const matches = [];
                let last = 0, m;
                while ((m = re.exec(src.value)) !== null) {
                    if (m[0].length === 0) { re.lastIndex++; continue; }
                    matches.push(m);
                    if (m.index > last) frag.append(src.value.slice(last, m.index));
                    frag.append(el("mark", {class: "hl-match", text: m[0]}));
                    last = m.index + m[0].length;
                    if (matches.length >= 200) break;
                }
                frag.append(src.value.slice(last));
                highlight.replaceChildren(frag);
                status.textContent = matches.length ? `匹配 ${matches.length} 处` : "无匹配";
                status.className = `tool-note ${matches.length ? "ok" : ""}`;
                if (matches.length) {
                    const groupCount = Math.max(...matches.map(x => x.length)) - 1;
                    tableWrap.replaceChildren(el("table", {class: "tool-table"},
                        el("thead", {}, el("tr", {}, el("th", {text: "#"}), el("th", {text: "位置"}), el("th", {text: "匹配"}), ...(groupCount ? Array.from({length: groupCount}, (_, i) => el("th", {text: `分组 ${i + 1}`})) : []))),
                        el("tbody", {}, matches.slice(0, 50).map((match, i) => el("tr", {},
                            el("td", {text: i + 1}), el("td", {text: match.index}),
                            el("td", {}, el("code", {text: match[0]})),
                            ...Array.from({length: groupCount}, (_, g) => el("td", {text: match[g + 1] ?? "—"})))))));
                } else tableWrap.replaceChildren(note("没有匹配结果，试试调整表达式或测试文本"));
            };
            [pattern, flags, src].forEach(node => node.addEventListener("input", update));
            update();
            const cheats = [
                ["\\d+", "数字"], ["[a-zA-Z0-9_]+", "单词字符"], ["^\\s*$", "空白行"], ["^[ \\t]*#", "注释行"],
                ["(\\d{4})-(\\d{2})-(\\d{2})", "ISO 日期"], ["\\b(\\w+)\\1\\b", "重复单词"],
                ["^[A-Z]:\\\\", "Windows 路径"], ["\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "IPv4 地址"],
                ["<([a-z]+)[^>]*>(.*?)</\\1>", "HTML 标签"], ["(?i)error|warn", "忽略大小写（需要支持）"]
            ];
            body.append(
                el("div", {class: "tool-actions", style: "align-items:end"},
                    el("div", {style: "flex:1"}, field("正则表达式", pattern)),
                    el("div", {style: "width:100px"}, field("标志", flags))),
                field("测试文本", src), status, field("匹配高亮", highlight),
                tableWrap,
                el("details", {class: "tool-details"},
                    el("summary", {text: "常用正则速查"}),
                    el("div", {class: "details-body"},
                        el("table", {class: "tool-table"},
                            el("tbody", {}, cheats.map(([re, desc]) => el("tr", {}, el("td", {}, el("code", {text: re})), el("td", {text: desc}))))))));
        }
    },
    {
        id: "cron", cat: "parse", icon: "*✱", name: "Cron 解析器",
        desc: "解析 5/6 位 Cron 表达式，中文释义并推算下次执行时间",
        render(body) {
            const src = input({value: "30 8 * * 1-5", placeholder: "分 时 日 月 周 [秒]"});
            const desc = note("");
            const fieldsGrid = el("div", {class: "out-grid"});
            const runsList = el("div", {class: "out-grid"});
            const fieldNames = ["秒", "分", "时", "日", "月", "周"];
            const update = () => {
                try {
                    const cron = parseCron(src.value);
                    desc.textContent = cronDescribe(cron);
                    desc.className = "tool-note ok";
                    const specs = src.value.trim().split(/\s+/);
                    const offset = specs.length === 6 ? 0 : 1;
                    const ranges = ["0-59", "0-59", "0-23", "1-31", "1-12", "0-7"];
                    fieldsGrid.replaceChildren(...specs.map((spec, i) =>
                        outRow(fieldNames[i + offset] ?? "?", `${spec}（${ranges[i + offset]}）`, null)));
                    const runs = nextCronRuns(cron, 5);
                    runsList.replaceChildren(...runs.map((date, i) => outRow(`第 ${i + 1} 次`,
                        `${date.toLocaleString("zh-CN", {hour12: false})}（星期${"日一二三四五六"[date.getDay()]}）`, null)));
                } catch (error) {
                    desc.textContent = error.message;
                    desc.className = "tool-note error";
                    fieldsGrid.replaceChildren();
                    runsList.replaceChildren();
                }
            };
            src.addEventListener("input", update);
            update();
            body.append(field("Cron 表达式", src), desc,
                field("字段解析", fieldsGrid), field("接下来 5 次执行时间（按服务器本地时区）", runsList),
                el("details", {class: "tool-details"},
                    el("summary", {text: "常见表达式示例"}),
                    el("div", {class: "details-body"},
                        el("table", {class: "tool-table"},
                            el("tbody", {}, CRON_CHEATS.map(([expr, mean]) => el("tr", {}, el("td", {}, el("code", {text: expr})), el("td", {text: mean}))))))));
        }
    },
    {
        id: "timestamp", cat: "parse", icon: "ms", name: "时间戳转换",
        desc: "Unix 时间戳与日期互转，自动识别秒/毫秒",
        render(body) {
            const nowSeconds = el("div", {class: "out-value"});
            const nowMillis = el("div", {class: "out-value"});
            const nowIso = el("div", {class: "out-value"});
            const tick = () => {
                const now = new Date();
                nowSeconds.textContent = String(Math.floor(now.getTime() / 1000));
                nowMillis.textContent = String(now.getTime());
                nowIso.textContent = now.toISOString();
            };
            every(tick, 1000);
            const src = input({value: String(Date.now()), placeholder: "输入秒或毫秒时间戳"});
            const grid = el("div", {class: "out-grid"});
            const convert = () => {
                const raw = Number(src.value.trim());
                if (!Number.isFinite(raw) || src.value.trim() === "") {
                    grid.replaceChildren(note("等待输入有效数字", "error"));
                    return;
                }
                const date = new Date(Math.abs(raw) >= 1e12 ? raw : raw * 1000);
                if (isNaN(date.getTime())) { grid.replaceChildren(note("超出可表示的时间范围", "error")); return; }
                const relative = (ms) => {
                    const diff = Math.round((ms - Date.now()) / 1000);
                    const abs = Math.abs(diff);
                    const unit = abs < 60 ? `${abs} 秒` : abs < 3600 ? `${Math.round(abs / 60)} 分钟` : abs < 86400 ? `${Math.round(abs / 3600)} 小时` : `${Math.round(abs / 86400)} 天`;
                    return diff >= 0 ? `${unit}后` : `${unit}前`;
                };
                grid.replaceChildren(
                    outRow("判定", Math.abs(raw) >= 1e12 ? "毫秒" : "秒", null),
                    outRow("本地时间", date.toLocaleString("zh-CN", {hour12: false}), null),
                    outRow("星期", `星期${"日一二三四五六"[date.getDay()]}`, null),
                    outRow("ISO 8601", date.toISOString(), () => date.toISOString()),
                    outRow("UTC", date.toUTCString(), () => date.toUTCString()),
                    outRow("相对", relative(date.getTime()), null));
            };
            src.addEventListener("input", convert);
            convert();
            const reverse = input({type: "datetime-local", class: "tool-input sm"});
            const reverseOut = el("div", {class: "out-grid"});
            const reverseConvert = () => {
                if (!reverse.value) { reverseOut.replaceChildren(); return; }
                const date = new Date(reverse.value);
                reverseOut.replaceChildren(
                    outRow("秒", String(Math.floor(date.getTime() / 1000)), () => String(Math.floor(date.getTime() / 1000))),
                    outRow("毫秒", String(date.getTime()), () => String(date.getTime())));
            };
            reverse.addEventListener("input", reverseConvert);
            const init = new Date();
            init.setMinutes(init.getMinutes() - init.getTimezoneOffset(), 0, 0);
            reverse.value = init.toISOString().slice(0, 16);
            reverseConvert();
            body.append(
                field("当前时间", el("div", {class: "out-grid"},
                    outRow("Unix 秒", nowSeconds, () => nowSeconds.textContent),
                    outRow("Unix 毫秒", nowMillis, () => nowMillis.textContent),
                    outRow("ISO", nowIso, () => nowIso.textContent))),
                field("时间戳 → 日期（自动识别秒 / 毫秒）", src), grid,
                field("日期 → 时间戳", reverse), reverseOut);
        }
    },
    {
        id: "urlparse", cat: "parse", icon: "//", name: "URL 解析",
        desc: "拆解 URL 的协议、主机、路径、查询参数与哈希",
        render(body) {
            const src = input({value: "https://admin:secret@seeker.local:8443/files/list?sort=name&page=2&tag=lan#anchor"});
            const grid = el("div", {class: "out-grid"});
            const update = () => {
                try {
                    const url = new URL(src.value.trim());
                    const rows = [
                        outRow("协议", url.protocol.replace(":", ""), null),
                        outRow("主机", url.hostname, url.hostname),
                        outRow("端口", url.port || (url.protocol === "https:" ? "443（默认）" : "80（默认）"), null)
                    ];
                    if (url.username || url.password) rows.push(outRow("认证信息", `${url.username} / ${url.password ? "••••" : "（空）"}`, null));
                    rows.push(
                        outRow("路径", url.pathname, url.pathname),
                        outRow("哈希", url.hash ? url.hash.slice(1) : el("span", {class: "muted", text: "（无）"}), null));
                    grid.replaceChildren(...rows);
                    const params = [...url.searchParams.entries()];
                    paramTable.replaceChildren(params.length
                        ? el("table", {class: "tool-table"},
                            el("thead", {}, el("tr", {}, el("th", {text: "参数"}), el("th", {text: "值"}), el("th", {text: ""}))),
                            el("tbody", {}, params.map(([k, v]) => el("tr", {},
                                el("td", {}, el("code", {text: k})), el("td", {text: v}),
                                el("td", {}, copyBtn(`${k}=${v}`))))))
                        : note("URL 中没有查询参数"));
                } catch (error) {
                    grid.replaceChildren(note(`无法解析：${error.message}`, "error"));
                    paramTable.replaceChildren();
                }
            };
            const paramTable = el("div", {});
            src.addEventListener("input", update);
            update();
            body.append(field("输入 URL", src), grid, field("查询参数", paramTable));
        }
    },

    /* ---------- 网络运维 ---------- */
    {
        id: "cidr", cat: "net", icon: "/", name: "IP 子网计算器",
        desc: "IPv4/CIDR 计算网络地址、掩码、广播地址与可用主机",
        render(body) {
            const src = input({value: "192.168.1.10/24", placeholder: "例如 10.0.0.5/16"});
            const grid = el("div", {class: "out-grid"});
            const update = () => {
                try {
                    const info = computeCidr(src.value);
                    grid.replaceChildren(
                        outRow("网络地址", `${info.network}/${info.prefix}`, info.network),
                        outRow("子网掩码", info.mask, info.mask),
                        outRow("通配符掩码", info.wildcard, info.wildcard),
                        outRow("广播地址", info.broadcast, info.broadcast),
                        outRow("可用主机", `${info.first} ~ ${info.last}`, `${info.first} ~ ${info.last}`),
                        outRow("主机容量", `${info.hosts.toLocaleString()}（可用 ${info.usable.toLocaleString()}）`, null),
                        outRow("地址类型", `${info.type} · ${info.class}`, null),
                        outRow("二进制", `${info.binary}\n${info.maskBinary}`, null));
                } catch (error) { grid.replaceChildren(note(error.message, "error")); }
            };
            src.addEventListener("input", update);
            update();
            body.append(field("IP 地址或 CIDR", src), grid,
                note("支持 0-32 前缀；/31、/32 按点对点与单主机规则处理。"));
        }
    },
    {
        id: "httpstatus", cat: "net", icon: "404", name: "HTTP 状态码",
        desc: "常用 HTTP 状态码速查，支持搜索过滤",
        render(body) {
            const codes = {
                1: [[100, "Continue", "已收到请求头，请继续发送请求体"], [101, "Switching Protocols", "服务器同意切换协议"], [103, "Early Hints", "提前推送响应头提示"]],
                2: [[200, "OK", "请求成功"], [201, "Created", "资源已创建"], [202, "Accepted", "已接受，尚未处理完成"], [204, "No Content", "成功但无返回内容"], [206, "Partial Content", "范围请求部分内容"]],
                3: [[301, "Moved Permanently", "永久重定向"], [302, "Found", "临时重定向"], [303, "See Other", "用 GET 访问其他 URI"], [304, "Not Modified", "缓存仍然有效"], [307, "Temporary Redirect", "临时重定向且保持方法"], [308, "Permanent Redirect", "永久重定向且保持方法"]],
                4: [[400, "Bad Request", "请求语法错误"], [401, "Unauthorized", "未认证或凭证无效"], [403, "Forbidden", "服务器拒绝执行"], [404, "Not Found", "资源不存在"], [405, "Method Not Allowed", "HTTP 方法不被允许"], [408, "Request Timeout", "请求超时"], [409, "Conflict", "与资源当前状态冲突"], [410, "Gone", "资源已永久移除"], [413, "Payload Too Large", "请求体超过限制"], [415, "Unsupported Media Type", "媒体类型不支持"], [416, "Range Not Satisfiable", "请求范围不合法"], [418, "I'm a teapot", "我是一个茶杯（彩蛋）"], [422, "Unprocessable Entity", "语义错误无法处理"], [429, "Too Many Requests", "请求频率超限"], [431, "Header Fields Too Large", "请求头过大"], [451, "Unavailable For Legal Reasons", "因法律原因不可用"]],
                5: [[500, "Internal Server Error", "服务器内部错误"], [501, "Not Implemented", "功能未实现"], [502, "Bad Gateway", "上游服务返回无效响应"], [503, "Service Unavailable", "服务暂时不可用"], [504, "Gateway Timeout", "上游服务响应超时"], [505, "HTTP Version Not Supported", "HTTP 版本不支持"], [507, "Insufficient Storage", "服务器存储不足"], [511, "Network Authentication Required", "需要网络认证"]]
            };
            const search = input({placeholder: "搜索状态码或描述…"});
            const list = el("div", {class: "status-list"});
            const render = () => {
                const query = search.value.trim().toLowerCase();
                list.replaceChildren(...Object.entries(codes).flatMap(([group, items]) =>
                    items.filter(([code, name, desc]) => !query || String(code).includes(query) || name.toLowerCase().includes(query) || desc.toLowerCase().includes(query))
                        .map(([code, name, desc]) => el("div", {class: `status-chip status-${group}`},
                            el("code", {text: code}),
                            el("small", {}, el("strong", {text: name}), el("br"), desc)))));
            };
            search.addEventListener("input", render);
            render();
            body.append(field("搜索", search), list);
        }
    },

    /* ---------- 实用杂项 ---------- */
    {
        id: "color", cat: "misc", icon: "◐", name: "颜色转换",
        desc: "HEX / RGB / HSL 互转，实时预览色块",
        render(body) {
            const picker = input({type: "color", value: "#b7ff3c"});
            const hex = input({value: "#B7FF3C", placeholder: "#RRGGBB"});
            const grid = el("div", {class: "out-grid"});
            const preview = el("div", {class: "color-preview"});
            const update = (source) => {
                try {
                    if (source === picker) hex.value = picker.value.toUpperCase();
                    else picker.value = hexToRgb(hex.value) && /^#[0-9a-f]{6}$/i.test(hex.value) ? hex.value : picker.value;
                    const [r, g, b] = hexToRgb(hex.value);
                    const [h, s, l] = rgbToHsl(r, g, b);
                    const rgbText = `rgb(${r}, ${g}, ${b})`;
                    const hslText = `hsl(${h}, ${s}%, ${l}%)`;
                    preview.style.setProperty("--preview", rgbText);
                    preview.style.setProperty("--hex", `"${hex.value.toUpperCase()}"`);
                    grid.replaceChildren(
                        outRow("HEX", hex.value.toUpperCase(), hex.value.toUpperCase()),
                        outRow("RGB", rgbText, rgbText),
                        outRow("HSL", hslText, hslText),
                        outRow("亮度", `${Math.round((0.299 * r + 0.587 * g + 0.114 * b) / 255 * 100)}%（深色文字建议 < 50%）`, null));
                } catch (error) { grid.replaceChildren(note(error.message, "error")); }
            };
            picker.addEventListener("input", () => update(picker));
            hex.addEventListener("input", () => update(hex));
            update(picker);
            body.append(
                field("选取颜色", el("div", {class: "color-input-row"}, picker, hex)),
                preview, grid);
        }
    },
    {
        id: "lorem", cat: "misc", icon: "¶", name: "假文生成器",
        desc: "生成 Lorem Ipsum 占位文本，用于排版与测试",
        render(body) {
            const count = input({type: "number", value: "3", min: "1", max: "20", class: "tool-input sm"});
            const out = area("", 10);
            out.readOnly = true;
            const generate = () => {
                const total = Math.min(20, Math.max(1, Number(count.value) || 3));
                const paragraphs = [];
                for (let p = 0; p < total; p++) {
                    const sentences = 3 + Math.floor(Math.random() * 4);
                    const words = [];
                    for (let s = 0; s < sentences; s++) {
                        const len = 6 + Math.floor(Math.random() * 10);
                        const sentence = Array.from({length: len}, () => LOREM_WORDS[Math.floor(Math.random() * LOREM_WORDS.length)]).join(" ");
                        words.push(sentence.charAt(0).toUpperCase() + sentence.slice(1) + ".");
                    }
                    paragraphs.push(words.join(" "));
                }
                out.value = paragraphs.join("\n\n");
            };
            body.append(el("div", {class: "tool-actions"},
                el("div", {}, field("段落数", count)),
                btn("生成", generate, true), copyBtn(() => out.value)),
                field("输出结果", out));
            generate();
        }
    },

    /* ---------- 学习工具 ---------- */
    {
        id: "ziti", cat: "learn", icon: "字", name: "字帖生成器",
        desc: "生成田字格 / 米字格练字字帖，支持描红、拼音与笔画标注",
        render(body) {
            const src = area("床前明月光，疑是地上霜。\n举头望明月，低头思故乡。", 4);
            const grid = el("select", {class: "tool-input sm"},
                el("option", {value: "tian", text: "田字格"}),
                el("option", {value: "mi", text: "米字格"}),
                el("option", {value: "square", text: "方格"}));
            const trace = checkbox("描红（浅色临摹）");
            const showPy = checkbox("标注拼音", false);
            const showSc = checkbox("标注笔画", false);
            const cols = input({type: "number", value: "7", min: "1", max: "20", class: "tool-input sm"});
            const rows = input({type: "number", value: "5", min: "1", max: "20", class: "tool-input sm"});
            const size = input({type: "number", value: "56", min: "24", max: "160", class: "tool-input sm"});
            const preview = el("div", {class: "ziti-preview"});
            const count = note("");

            const buildPages = () => {
                const text = src.value.replace(/\r/g, "");
                const cellPx = Math.max(24, Math.min(160, Number(size.value) || 56));
                const perRow = Math.max(1, Math.min(20, Number(cols.value) || 7));
                const perCol = Math.max(1, Math.min(20, Number(rows.value) || 5));
                const pages = [];
                let current = [], row = [];
                const flushRow = () => { if (row.length) { current.push(row); row = []; } };
                const flushPage = () => { if (current.length) { pages.push(current); current = []; } };
                for (const ch of text) {
                    if (ch === "\n") { flushRow(); if (current.length >= perCol) flushPage(); continue; }
                    if (ch === " ") { row.push(null); }
                    else {
                        const info = hanziLookup(ch);
                        row.push(info ? {char: ch, pinyin: info.pinyin, strokes: info.strokes} : {char: ch, pinyin: null, strokes: null});
                    }
                    if (row.length >= perRow) flushRow();
                    if (current.length >= perCol) flushPage();
                }
                flushRow();
                flushPage();
                if (!pages.length) pages.push([]);
                return {pages, cellPx, perRow, perCol};
            };

            const render = () => {
                const {pages, cellPx, perRow} = buildPages();
                const gridType = grid.value;
                const traceMode = trace.box.checked;
                const withPy = showPy.box.checked;
                const withSc = showSc.box.checked;
                let charTotal = 0, pyTotal = 0;
                preview.replaceChildren(...pages.map(page => {
                    const sheet = el("div", {class: "ziti-page", style: `--cell: ${cellPx}px; --cols: ${perRow}`});
                    sheet.append(el("div", {class: "ziti-title", text: "练 字 帖"}));
                    const gridEl = el("div", {class: "ziti-grid"});
                    for (const row of page) {
                        for (const cell of row) {
                            if (cell === null) { gridEl.append(el("div", {class: `ziti-cell ziti-${gridType} ziti-empty`})); continue; }
                            charTotal++;
                            const node = el("div", {class: `ziti-cell ziti-${gridType}${traceMode ? " trace" : ""}`});
                            if (withPy && cell.pinyin) { pyTotal++; node.append(el("span", {class: "ziti-py", text: cell.pinyin[0]})); }
                            node.append(el("span", {class: "ziti-char", text: cell.char}));
                            if (withSc && cell.strokes) node.append(el("span", {class: "ziti-sc", text: cell.strokes}));
                            gridEl.append(node);
                        }
                    }
                    sheet.append(gridEl);
                    return sheet;
                }));
                count.textContent = `共 ${pages.length} 页 · ${charTotal} 字${withPy ? ` · 标注拼音 ${pyTotal} 字` : ""}`;
                count.className = "tool-note ok";
            };

            const exportPng = () => {
                const {pages, cellPx, perRow, perCol} = buildPages();
                const gridType = grid.value, traceMode = trace.box.checked, withPy = showPy.box.checked, withSc = showSc.box.checked;
                const pad = 28, titleH = 46, pageGap = 32;
                const pageW = perRow * cellPx, pageH = perCol * cellPx;
                const width = pageW + pad * 2;
                const height = pages.length * (pageH + titleH) + pad * 2 + (pages.length - 1) * pageGap;
                const canvas = document.createElement("canvas");
                canvas.width = width * 2;
                canvas.height = height * 2;
                const ctx = canvas.getContext("2d");
                ctx.scale(2, 2);
                ctx.fillStyle = "#fff";
                ctx.fillRect(0, 0, width, height);
                const fontStack = "'Kaiti SC','KaiTi','STKaiti','Noto Serif SC','PingFang SC',serif";
                pages.forEach((page, p) => {
                    const top = pad + p * (pageH + titleH + pageGap);
                    ctx.strokeStyle = "#9a948a";
                    ctx.lineWidth = 1.5;
                    ctx.strokeRect(pad - 8, top - 8, pageW + 16, pageH + titleH + 16);
                    ctx.fillStyle = "#555";
                    ctx.font = `600 ${Math.round(titleH * 0.55)}px ${fontStack}`;
                    ctx.textAlign = "center";
                    ctx.textBaseline = "middle";
                    ctx.fillText("练 字 帖", width / 2, top + titleH / 2);
                    page.forEach((row, r) => {
                        row.forEach((cell, c) => {
                            const x = pad + c * cellPx, y = top + titleH + r * cellPx;
                            ctx.strokeStyle = "#8a8478";
                            ctx.lineWidth = 1;
                            ctx.strokeRect(x + .5, y + .5, cellPx - 1, cellPx - 1);
                            if (gridType !== "square") {
                                ctx.strokeStyle = "rgba(120,115,105,.55)";
                                ctx.lineWidth = 1;
                                ctx.setLineDash([4, 4]);
                                ctx.beginPath();
                                ctx.moveTo(x + cellPx / 2, y + 2); ctx.lineTo(x + cellPx / 2, y + cellPx - 2);
                                ctx.moveTo(x + 2, y + cellPx / 2); ctx.lineTo(x + cellPx - 2, y + cellPx / 2);
                                if (gridType === "mi") {
                                    ctx.moveTo(x + 2, y + 2); ctx.lineTo(x + cellPx - 2, y + cellPx - 2);
                                    ctx.moveTo(x + cellPx - 2, y + 2); ctx.lineTo(x + 2, y + cellPx - 2);
                                }
                                ctx.stroke();
                                ctx.setLineDash([]);
                            }
                            if (!cell) return;
                            if (withPy && cell.pinyin) {
                                ctx.fillStyle = "#777";
                                ctx.font = `${Math.round(cellPx * 0.24)}px ${fontStack}`;
                                ctx.textAlign = "center"; ctx.textBaseline = "middle";
                                ctx.fillText(cell.pinyin[0], x + cellPx / 2, y + cellPx * 0.24);
                            }
                            ctx.fillStyle = traceMode ? "#e8b6b6" : "#1a1a1a";
                            ctx.font = `${Math.round(cellPx * 0.66)}px ${fontStack}`;
                            ctx.textAlign = "center"; ctx.textBaseline = "middle";
                            ctx.fillText(cell.char, x + cellPx / 2, y + cellPx * 0.55);
                            if (withSc && cell.strokes) {
                                ctx.fillStyle = "#9a948a";
                                ctx.font = `${Math.round(cellPx * 0.2)}px ${fontStack}`;
                                ctx.fillText(String(cell.strokes), x + cellPx * 0.8, y + cellPx * 0.16);
                            }
                        });
                    });
                });
                const link = el("a", {href: canvas.toDataURL("image/png"), download: "字帖.png"});
                document.body.append(link);
                link.click();
                link.remove();
                showToast("已导出字帖 PNG");
            };

            src.addEventListener("input", render);
            grid.addEventListener("change", render);
            [cols, rows, size].forEach(n => n.addEventListener("input", render));
            trace.box.addEventListener("change", render);
            showPy.box.addEventListener("change", render);
            showSc.box.addEventListener("change", render);
            render();

            body.append(
                field("练习文本", src),
                el("div", {class: "tool-actions"},
                    el("div", {}, field("格子类型", grid)),
                    el("div", {}, field("每行字数", cols)),
                    el("div", {}, field("每页行数", rows)),
                    el("div", {}, field("格子大小 px", size))),
                el("div", {class: "tool-actions"}, trace.node, showPy.node, showSc.node,
                    btn("打印字帖", () => window.print(), true),
                    btn("下载 PNG", exportPng)),
                count,
                field("字帖预览（打印请用浏览器的打印功能）", preview));
        }
    },
    {
        id: "pinyin", cat: "learn", icon: "拼", name: "汉字注音",
        desc: "为汉字批量标注拼音，支持声调样式、大小写与多音字",
        render(body) {
            const src = area("少壮不努力，老大徒伤悲。", 4);
            const mode = el("select", {class: "tool-input sm"},
                el("option", {value: "pair", text: "对照 字(拼音)"}),
                el("option", {value: "py", text: "仅拼音"}),
                el("option", {value: "char", text: "仅汉字"}));
            const toneStyle = el("select", {class: "tool-input sm"},
                el("option", {value: "mark", text: "带声调"}),
                el("option", {value: "num", text: "数字声调"}),
                el("option", {value: "plain", text: "无声调"}));
            const caseStyle = el("select", {class: "tool-input sm"},
                el("option", {value: "lower", text: "小写"}),
                el("option", {value: "title", text: "首字母大写"}),
                el("option", {value: "upper", text: "全大写"}));
            const poly = el("select", {class: "tool-input sm"},
                el("option", {value: "first", text: "常用读音"}),
                el("option", {value: "all", text: "全部读音"}));
            const out = area("", 8);
            out.readOnly = true;
            const update = () => {
                let result = "";
                for (const ch of src.value) {
                    const info = hanziLookup(ch);
                    if (!info) { result += ch; continue; }
                    const pys = (poly.value === "all" ? info.pinyin : info.pinyin.slice(0, 1)).map(p => {
                        let s = formatPinyin(p, toneStyle.value);
                        if (caseStyle.value === "upper") s = s.toUpperCase();
                        else if (caseStyle.value === "title") s = capFirst(s);
                        return s;
                    });
                    const joined = pys.join("/");
                    if (mode.value === "py") result += joined + " ";
                    else if (mode.value === "char") result += ch;
                    else result += `${ch}(${joined})`;
                }
                out.value = result.trim();
            };
            [src, mode, toneStyle, caseStyle, poly].forEach(n => n.addEventListener("input", update));
            update();
            body.append(field("输入中文文本", src),
                el("div", {class: "tool-actions"},
                    el("div", {}, field("输出格式", mode)),
                    el("div", {}, field("声调", toneStyle)),
                    el("div", {}, field("大小写", caseStyle)),
                    el("div", {}, field("多音字", poly)),
                    copyBtn(() => out.value)),
                field("注音结果", out),
                note("覆盖通用规范汉字表 8105 常用字，多音字默认取常用读音，数据纯本地。"));
        }
    }
];

/* ================= 界面渲染与路由 ================= */
const toolsBody = $("#toolsBody");
const toolsMenu = $("#toolsMenu");
const toolsSearch = $("#toolsSearch");
const views = {share: $("#viewShare"), tools: $("#viewTools"), admin: $("#viewAdmin"), docs: $("#viewDocs")};
let activeTimers = [];
const every = (fn, ms) => { fn(); activeTimers.push(setInterval(fn, ms)); };
const clearTimers = () => { activeTimers.forEach(clearInterval); activeTimers = []; };
const catName = (id) => (CATEGORIES.find(c => c.id === id) || {}).name || "";

function renderMenu(filter = "") {
    const query = filter.trim().toLowerCase();
    toolsMenu.replaceChildren(...CATEGORIES.map(cat => {
        const tools = TOOLS.filter(t => t.cat === cat.id && (!query || `${t.name}${t.desc}${t.id}`.toLowerCase().includes(query)));
        if (!tools.length) return null;
        return el("div", {class: "tools-cat"},
            el("span", {class: "tools-cat-name", text: cat.name.toUpperCase()}),
            tools.map(t => el("a", {class: "tool-link", href: `#/tools/${t.id}`, "data-tool": t.id},
                el("span", {class: "tool-glyph sm", text: t.icon}),
                el("span", {text: t.name}))));
    }).filter(Boolean));
}

function menuActive(id) {
    toolsMenu.querySelectorAll(".tool-link").forEach(link =>
        link.classList.toggle("active", link.dataset.tool === id));
}

function renderHome() {
    clearTimers();
    menuActive(null);
    const blocks = CATEGORIES.map(cat => {
        const tools = TOOLS.filter(t => t.cat === cat.id);
        return el("section", {class: "tools-cat-block"},
            el("h3", {}, el("span", {class: "cat-tag", text: cat.name}), el("small", {text: `${tools.length} MODULES`})),
            el("div", {class: "tool-cards"}, tools.map(t => el("a", {class: "tool-card", href: `#/tools/${t.id}`},
                el("span", {class: "tool-glyph", text: t.icon}),
                el("strong", {text: t.name}),
                el("small", {text: t.desc})))));
    });
    const hero = el("div", {class: "tools-home-hero"},
        el("span", {class: "kicker"}, el("i"), " OPERATIONS TOOLBOX · ALL LOCAL"),
        el("h2", {class: "tools-title"}, "运维工具箱", el("br"), el("em", {text: "LOCAL COMPUTE."})),
        el("p", {class: "tools-sub", text: `${TOOLS.length} 个常用小工具，覆盖文本、编码、加密、解析与网络运维场景。全部在浏览器本地运行，零后端依赖，数据不出本机。`}));
    toolsBody.replaceChildren(el("div", {class: "tools-home"}, hero, ...blocks));
}

function renderTool(id) {
    clearTimers();
    const tool = TOOLS.find(t => t.id === id);
    if (!tool) { renderHome(); return; }
    menuActive(id);
    const bodyNode = el("div", {class: "tool-body"});
    tool.render(bodyNode);
    toolsBody.replaceChildren(el("article", {class: "tool-panel glass"},
        el("header", {class: "tool-head"},
            el("a", {class: "tool-back", href: "#/tools", text: "← 返回工具总览"}),
            el("span", {class: "tool-index", text: `TOOL // ${String(TOOLS.indexOf(tool) + 1).padStart(2, "0")}\nMODULE ${catName(tool.id).toUpperCase()}`}),
            el("h2", {text: tool.name}),
            el("p", {class: "tool-desc", text: tool.desc})),
        bodyNode));
}

function setActiveNav(name) {
    document.querySelectorAll(".main-nav a").forEach(link =>
        link.classList.toggle("active", link.dataset.nav === name));
}

function route() {
    const hash = decodeURIComponent(location.hash.replace(/^#\/?/, ""));
    const [root, sub] = hash.split("/");
    if (root === "tools") {
        views.share.hidden = true;
        views.tools.hidden = false;
        views.admin.hidden = true;
        setActiveNav("tools");
        const id = sub && TOOLS.some(t => t.id === sub) ? sub : null;
        renderMenu(toolsSearch.value);
        if (id) renderTool(id); else renderHome();
    } else if (root === "admin") {
        views.share.hidden = true;
        views.tools.hidden = true;
        views.admin.hidden = false;
        setActiveNav("admin");
        clearTimers();
    } else if (root === "docs") {
        views.share.hidden = true;
        views.tools.hidden = true;
        views.admin.hidden = true;
        views.docs.hidden = false;
        setActiveNav("docs");
        clearTimers();
    } else {
        views.share.hidden = false;
        views.tools.hidden = true;
        views.admin.hidden = true;
        setActiveNav("share");
        clearTimers();
    }
    window.dispatchEvent(new CustomEvent("seeker:route"));
}

toolsSearch.addEventListener("input", () => renderMenu(toolsSearch.value));
window.addEventListener("hashchange", () => { route(); window.scrollTo({top: 0, behavior: "auto"}); });
route();

/* 供自动化测试使用（浏览器中不生效） */
if (typeof module !== "undefined" && module.exports) {
    module.exports = {md5, sha1, sha256, crc32, parseCron, nextCronRuns, cronDescribe, computeCidr, diffLines, splitWords, hexToRgb, rgbToHsl, b64encode, b64decode, parseInBase, parseCronField};
}
})();
