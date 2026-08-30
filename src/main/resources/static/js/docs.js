/* Seeker Share 文档库 —— Markdown 知识库 + 实时协同编辑(纯前端,无 Node) */
import * as Y from "../vendor/yjs.mjs";

const docsBody = document.getElementById("docsBody");
const escapeHtml = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/* ---- markdown-it 渲染(带代码高亮) ---- */
const md = window.markdownit({
    html: false,
    linkify: true,
    breaks: true
});
md.renderer.rules.fence = (tokens, idx) => {
    const token = tokens[idx];
    const lang = (token.info || "").trim().split(/\s+/)[0];
    const code = escapeHtml(token.content);
    if (lang && window.hljs && window.hljs.getLanguage(lang)) {
        try {
            const highlighted = window.hljs.highlight(token.content, {language: lang, ignoreIllegals: true}).value;
            return `<pre class="doc-code"><code class="hljs language-${escapeHtml(lang)}">${highlighted}</code></pre>`;
        } catch (error) { }
    }
    return `<pre class="doc-code"><code class="hljs">${code}</code></pre>`;
};

const docEl = (tag, props = {}, ...children) => {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) continue;
        if (key === "class") node.className = value;
        else if (key === "text") node.textContent = value;
        else if (key === "html") node.innerHTML = value;
        else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2).toLowerCase(), value);
        else node.setAttribute(key, value);
    }
    for (const child of children.flat(Infinity)) {
        if (child === null || child === undefined || child === false) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
};

/* ---- 编辑器运行态 ---- */
const editor = {
    docId: null, title: null, category: null, tags: null,
    ydoc: null, ytext: null, ws: null,
    members: 1, dirty: false, autosaveTimer: null, previewTimer: null
};

/* ================= API ================= */
const api = {
    list: (q, category) => request(`/api/v1/documents?q=${encodeURIComponent(q || "")}&category=${encodeURIComponent(category || "")}`).then(r => r.data),
    categories: () => request("/api/v1/documents/categories").then(r => r.data),
    get: (id) => request(`/api/v1/documents/${id}`).then(r => r.data),
    create: (payload, state) => request(`/api/v1/documents${state ? `?state=${encodeURIComponent(state)}` : ""}`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(payload)}).then(r => r.data),
    update: (id, payload, state) => request(`/api/v1/documents/${id}${state ? `?state=${encodeURIComponent(state)}` : ""}`, {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify(payload)}).then(r => r.data),
    remove: (id) => request(`/api/v1/documents/${id}`, {method: "DELETE"}),
    versions: (id) => request(`/api/v1/documents/${id}/versions`).then(r => r.data),
    createVersion: (id) => request(`/api/v1/documents/${id}/versions`, {method: "POST"}).then(r => r.data),
    rollback: (id, versionId) => request(`/api/v1/documents/${id}/rollback/${versionId}`, {method: "POST"}).then(r => r.data),
    attachments: (id) => request(`/api/v1/documents/${id}/attachments`).then(r => r.data),
    uploadAttachment: (id, file) => {
        const form = new FormData();
        form.append("file", file);
        return request(`/api/v1/documents/${id}/attachments`, {method: "POST", body: form}).then(r => r.data);
    }
};

/* ================= 路由 ================= */
async function renderRoute() {
    const hash = decodeURIComponent(location.hash.replace(/^#\/?/, ""));
    const [root, sub] = hash.split("/");
    if (root !== "docs") return;
    if (!sub) await renderList();
    else if (sub === "new") renderEditor(null);
    else if (editor.docId !== sub) await renderEditor(sub);
}

/* ================= 列表 ================= */
async function renderList() {
    teardownEditor();
    docsBody.replaceChildren(docEl("div", {class: "docs-head"},
        docEl("h2", {text: "文档库"}),
        docEl("p", {class: "docs-sub", text: "局域网 Markdown 知识库 · 多人实时协同 · 数据不出本机"})));
    const search = docEl("input", {class: "tool-input", placeholder: "搜索标题 / 内容 / 标签…", oninput: refresh});
    const category = docEl("select", {class: "tool-input sm", onchange: refresh});
    const createBtn = docEl("button", {class: "tool-btn primary", onclick: () => location.hash = "#/docs/new", text: "+ 新建文档"});
    const grid = docEl("div", {class: "docs-grid"});
    docsBody.append(docEl("div", {class: "docs-toolbar"},
        docEl("div", {class: "docs-search", html: "<span>⌕</span>"}, search), category, createBtn),
        grid);

    async function refresh() {
        try {
            const [items, cats] = await Promise.all([api.list(search.value, category.value), api.categories()]);
            category.replaceChildren(docEl("option", {value: "", text: "全部分类"}),
                ...cats.map(c => docEl("option", {value: c, text: c})));
            if (category.value && !cats.includes(category.value)) category.value = "";
            grid.replaceChildren(...(items.length ? items.map(card) : [docEl("div", {class: "docs-empty", text: "暂无文档,点击右上角新建"})]));
        } catch (error) {
            grid.replaceChildren(docEl("div", {class: "docs-empty", text: error.status === 401 ? "请先登录后访问文档库" : "加载失败:" + error.message}));
        }
    }

    function card(item) {
        return docEl("a", {class: "docs-card", href: `#/docs/${item.id}`},
            docEl("div", {class: "docs-card-title"}, docEl("strong", {text: item.title}),
                item.category ? docEl("span", {class: "docs-badge", text: item.category}) : null),
            item.tags ? docEl("div", {class: "docs-tags", text: item.tags.split(",").map(t => "#" + t.trim()).join(" ")}) : null,
            docEl("small", {text: `更新于 ${new Date(item.updatedAt).toLocaleString("zh-CN", {hour12: false})} · ${item.updatedBy || "未知"} · v${item.versionNo}`}));
    }
    refresh();
}

/* ================= 编辑器 ================= */
function renderEditor(id) {
    teardownEditor();
    editor.docId = id;
    const body = docEl("div", {class: "docs-editor"});
    const title = docEl("input", {class: "doc-title-input", placeholder: "文档标题…"});
    const category = docEl("input", {class: "tool-input sm", placeholder: "分类"});
    const tags = docEl("input", {class: "tool-input", placeholder: "标签(逗号分隔)"});
    const status = docEl("span", {class: "doc-collab-status", text: id ? "连接中…" : "新建文档"});
    const textarea = docEl("textarea", {class: "doc-textarea", spellcheck: "false", rows: 18, placeholder: "在这里输入 Markdown…"});
    const preview = docEl("div", {class: "doc-preview", hidden: true});

    const actions = docEl("div", {class: "docs-toolbar"},
        docEl("a", {class: "tool-back", href: "#/docs", text: "← 返回列表"}),
        status,
        docEl("button", {class: "tool-btn", onclick: () => { preview.hidden = !preview.hidden; textarea.hidden = !preview.hidden; }}, preview.hidden ? "预览" : "编辑"),
        docEl("button", {class: "tool-btn", onclick: exportMenu}, "导出 ▾"),
        docEl("button", {class: "tool-btn", onclick: openVersions}, "版本"),
        docEl("button", {class: "tool-btn", onclick: openAttachments}, "附件"),
        id ? docEl("button", {class: "tool-btn", onclick: () => removeDoc(), text: "删除"}) : null,
        docEl("button", {class: "tool-btn primary", onclick: saveDoc, text: id ? "保存" : "创建"}));

    body.append(actions,
        docEl("div", {class: "doc-meta"}, title, category, tags),
        docEl("div", {class: "doc-split"},
            docEl("div", {class: "doc-edit-wrap"}, textarea),
            preview));
    docsBody.replaceChildren(body);

    function refreshPreview() {
        clearTimeout(editor.previewTimer);
        editor.previewTimer = setTimeout(() => {
            preview.innerHTML = md.render(textarea.value);
            if (window.hljs) preview.querySelectorAll("pre code").forEach(block => window.hljs.highlightElement(block));
        }, 150);
    }
    textarea.addEventListener("input", () => { editor.dirty = true; if (!editor.ytext) refreshPreview(); });

    if (!id) {
        textarea.addEventListener("input", refreshPreview);
        textarea.value = "# 新文档\n\n开始写作…\n";
        preview.innerHTML = md.render(textarea.value);
        title.focus();
        return;
    }

    api.get(id).then(detail => {
        editor.title = detail.title; editor.category = detail.category || ""; editor.tags = detail.tags || "";
        title.value = detail.title; category.value = detail.category || ""; tags.value = detail.tags || "";
        textarea.value = detail.content || "";
        preview.innerHTML = md.render(detail.content || "");
        connectCollaboration(id, textarea, preview, status, () => refreshPreview());
    }).catch(error => {
        body.replaceChildren(docEl("div", {class: "docs-empty", text: "文档加载失败:" + error.message}));
    });
}

/* ================= Yjs 协同 ================= */
function connectCollaboration(docId, textarea, preview, statusEl, onLocalChange) {
    const ydoc = new Y.Doc();
    const ytext = ydoc.getText("content");
    editor.ydoc = ydoc; editor.ytext = ytext;
    const wsUrl = `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/documents/${docId}`;
    const ws = new WebSocket(wsUrl);
    ws.binaryType = "arraybuffer";
    editor.ws = ws;
    let self = false;
    let stateReceived = false;

    ws.onopen = () => {
        statusEl.textContent = "协同已连接";
        statusEl.classList.add("ok");
        // 新建文档(服务端无 Yjs 状态)时,用已加载内容初始化 ytext
        setTimeout(() => {
            if (!stateReceived && ytext.toString() === "" && textarea.value !== "") {
                ytext.insert(0, textarea.value);
                onLocalChange();
            }
        }, 250);
    };
    ws.onclose = () => { statusEl.textContent = "协同已断开(只读)"; statusEl.classList.remove("ok"); };
    ws.onerror = () => { statusEl.textContent = "协同连接失败"; statusEl.classList.remove("ok"); };
    ws.onmessage = (event) => {
        if (typeof event.data === "string") {
            try {
                const message = JSON.parse(event.data);
                if (message.type === "members") {
                    editor.members = message.count;
                    statusEl.textContent = `${message.count} 人在线协作`;
                    statusEl.classList.add("ok");
                } else if (message.type === "saved") {
                    showToast(`已保存${message.by ? " · " + message.by : ""}`);
                }
            } catch (error) { }
            return;
        }
        stateReceived = true;
        Y.applyUpdate(ydoc, new Uint8Array(event.data), "remote");
    };

    textarea.addEventListener("input", () => {
        const value = textarea.value;
        const current = ytext.toString();
        if (value === current || self) return;
        self = true;
        let start = 0;
        while (start < current.length && start < value.length && current[start] === value[start]) start++;
        let endCurrent = current.length, endValue = value.length;
        while (endCurrent > start && endValue > start && current[endCurrent - 1] === value[endValue - 1]) { endCurrent--; endValue--; }
        if (endCurrent > start) ytext.delete(start, endCurrent - start);
        if (endValue > start) ytext.insert(start, value.slice(start, endValue));
        self = false;
    });

    ytext.observe(() => {
        if (!self) {
            textarea.value = ytext.toString();
            editor.dirty = true;
        }
        onLocalChange();
        scheduleAutosave();
    });

    ydoc.on("update", (update, origin) => {
        if (origin === "remote") return;
        if (ws.readyState === WebSocket.OPEN) ws.send(update);
    });
}

function scheduleAutosave() {
    clearTimeout(editor.autosaveTimer);
    editor.autosaveTimer = setTimeout(() => { if (editor.dirty && editor.ws?.readyState === WebSocket.OPEN) saveDoc(false); }, 8000);
}

/* ================= 保存 / 版本 / 删除 ================= */
async function saveDoc(toast = true) {
    const titleEl = document.querySelector(".doc-title-input");
    const categoryEl = document.querySelector(".doc-meta input:nth-child(2)");
    const tagsEl = document.querySelector(".doc-meta input:nth-child(3)");
    const textarea = document.querySelector(".doc-textarea");
    const title = (titleEl?.value || "").trim();
    if (!title) { showToast("请填写文档标题", true); return; }
    const category = (categoryEl?.value || "").trim();
    const tags = (tagsEl?.value || "").trim();
    if (editor.ytext) syncTextareaToYtext();
    const content = editor.ytext ? editor.ytext.toString() : (textarea?.value || "");
    const state = editor.ydoc ? Y.encodeStateAsUpdate(editor.ydoc) : null;
    const stateB64 = state && state.length ? bytesToB64(state) : "";

    try {
        if (!editor.docId) {
            const created = await api.create({title, category, tags, content}, stateB64);
            editor.dirty = false;
            location.hash = `#/docs/${created.id}`;
            if (toast) showToast("文档已创建");
            return;
        }
        if (editor.ws?.readyState === WebSocket.OPEN) {
            editor.ws.send(JSON.stringify({type: "save", content, state: stateB64}));
            editor.dirty = false;
            if (toast) showToast("已保存(协同)");
        } else {
            await api.update(editor.docId, {title, category, tags, content}, stateB64);
            editor.dirty = false;
            if (toast) showToast("已保存");
        }
    } catch (error) {
        showToast("保存失败:" + error.message, true);
    }
}

function openVersions() {
    const id = editor.docId;
    if (!id) { showToast("请先创建文档", true); return; }
    const dialog = docEl("dialog", {class: "docs-dialog"},
        docEl("h3", {text: "版本历史"}),
        docEl("div", {class: "docs-dialog-body"}),
        docEl("div", {class: "dialog-actions"},
            docEl("button", {class: "tool-btn primary", onclick: async () => {
                await api.createVersion(id).catch(e => showToast(e.message, true));
                loadVersions();
            }, text: "生成当前快照"}),
            docEl("button", {class: "tool-btn", onclick: () => dialog.close(), text: "关闭"})));
    const loadVersions = async () => {
        try {
            const list = await api.versions(id);
            const container = dialog.querySelector(".docs-dialog-body");
            if (!list.length) {
                container.replaceChildren(docEl("div", {class: "docs-empty", text: "暂无历史版本"}));
                return;
            }
            const rows = list.map(v => docEl("div", {class: "doc-version-row"},
                docEl("span", {text: `v${v.versionNo}`}),
                docEl("span", {class: "muted", text: `${new Date(v.createdAt).toLocaleString("zh-CN", {hour12: false})} · ${v.createdBy || "未知"}`}),
                v.versionNo === list[0].versionNo ? docEl("span", {class: "docs-badge", text: "当前"}) : null,
                docEl("button", {class: "tool-btn tiny", onclick: async () => {
                    if (!confirm(`回滚到 v${v.versionNo}?当前内容将被覆盖`)) return;
                    await api.rollback(id, v.id);
                    dialog.close();
                    await renderEditor(id);
                    showToast(`已回滚到 v${v.versionNo}`);
                }, text: "回滚"})));
            container.replaceChildren(...rows);
        } catch (error) { showToast(error.message, true); }
    };
    loadVersions();
    document.body.append(dialog);
    dialog.showModal();
}

async function removeDoc() {
    if (!confirm(`确认删除文档「${editor.title || ""}」?版本与附件将一并删除`)) return;
    try {
        await api.remove(editor.docId);
        teardownEditor();
        location.hash = "#/docs";
        showToast("文档已删除");
    } catch (error) { showToast("删除失败:" + error.message, true); }
}

function teardownEditor() {
    clearTimeout(editor.autosaveTimer);
    clearTimeout(editor.previewTimer);
    if (editor.ws) { try { editor.ws.close(); } catch (error) { } }
    editor.docId = null; editor.ws = null; editor.ydoc = null; editor.ytext = null; editor.dirty = false;
}

/* ================= 附件 ================= */
async function openAttachments() {
    const id = editor.docId;
    if (!id) { showToast("请先创建文档", true); return; }
    const dialog = docEl("dialog", {class: "docs-dialog"},
        docEl("h3", {text: "附件管理"}),
        docEl("input", {type: "file", class: "doc-upload-input"}),
        docEl("div", {class: "docs-dialog-body"}),
        docEl("div", {class: "dialog-actions"},
            docEl("button", {class: "tool-btn primary", onclick: async () => {
                const file = dialog.querySelector("input[type=file]").files[0];
                if (!file) { showToast("请选择文件", true); return; }
                try {
                    const view = await api.uploadAttachment(id, file);
                    const textarea = document.querySelector(".doc-textarea");
                    if (view.contentType?.startsWith("image/") && textarea) {
                        textarea.value += `\n![${view.fileName}](${view.url})\n`;
                        if (editor.ytext) syncTextareaToYtext();
                    }
                    loadList();
                    showToast("附件已上传");
                } catch (error) { showToast("上传失败:" + error.message, true); }
            }, text: "上传附件"}),
            docEl("button", {class: "tool-btn", onclick: () => dialog.close(), text: "关闭"})));
    const loadList = async () => {
        try {
            const list = await api.attachments(id);
            const container = dialog.querySelector(".docs-dialog-body");
            if (!list.length) {
                container.replaceChildren(docEl("div", {class: "docs-empty", text: "暂无附件"}));
                return;
            }
            const rows = list.map(a => docEl("div", {class: "doc-attach-row"},
                docEl("span", {text: a.fileName}),
                docEl("span", {class: "muted", text: formatSize(a.size)}),
                docEl("a", {class: "tool-btn tiny", href: a.url, target: "_blank", rel: "noopener", text: "下载"}),
                docEl("button", {class: "tool-btn tiny", onclick: () => {
                    const textarea = document.querySelector(".doc-textarea");
                    if (textarea) textarea.value += `\n![${a.fileName}](${a.url})\n`;
                    if (editor.ytext) syncTextareaToYtext();
                }, text: "插入链接"})));
            container.replaceChildren(...rows);
        } catch (error) { showToast(error.message, true); }
    };
    loadList();
    document.body.append(dialog);
    dialog.showModal();
}

function syncTextareaToYtext() {
    const textarea = document.querySelector(".doc-textarea");
    if (!textarea || !editor.ytext) return;
    const value = textarea.value, current = editor.ytext.toString();
    if (value === current) return;
    let start = 0;
    while (start < current.length && start < value.length && current[start] === value[start]) start++;
    let endCurrent = current.length, endValue = value.length;
    while (endCurrent > start && endValue > start && current[endCurrent - 1] === value[endValue - 1]) { endCurrent--; endValue--; }
    if (endCurrent > start) editor.ytext.delete(start, endCurrent - start);
    if (endValue > start) editor.ytext.insert(start, value.slice(start, endValue));
}

/* ================= 导出 ================= */
function exportMenu() {
    const id = editor.docId;
    if (!id) { showToast("请先创建文档", true); return; }
    const content = editor.ytext ? editor.ytext.toString() : document.querySelector(".doc-textarea")?.value || "";
    const title = editor.title || document.querySelector(".doc-title-input")?.value || "文档";
    const rendered = md.render(content);
    const htmlDoc = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
<style>body{max-width:820px;margin:2.4rem auto;padding:0 1.4rem;font:16px/1.8 -apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#222}pre{background:#f6f8fa;padding:1rem;border-radius:8px;overflow:auto}code{font-family:ui-monospace,monospace}table{border-collapse:collapse}td,th{border:1px solid #d0d7de;padding:.35rem .6rem}img{max-width:100%}blockquote{border-left:3px solid #d0d7de;margin-left:0;padding-left:1rem;color:#57606a}</style></head>
<body>${rendered}</body></html>`;
    const actions = docEl("div", {class: "docs-dialog"},
        docEl("h3", {text: "导出文档"}),
        docEl("div", {class: "docs-dialog-body", html: `<p>选择导出格式:<br><code>${escapeHtml(title)}</code></p>`}),
        docEl("div", {class: "dialog-actions"},
            docEl("button", {class: "tool-btn", onclick: () => downloadBlob(new Blob([content], {type: "text/markdown;charset=utf-8"}), `${title}.md`), text: "Markdown"}),
            docEl("button", {class: "tool-btn", onclick: () => downloadBlob(new Blob([htmlDoc], {type: "text/html;charset=utf-8"}), `${title}.html`), text: "HTML"}),
            docEl("button", {class: "tool-btn primary", onclick: () => {
                const win = window.open("", "_blank");
                win.document.write(htmlDoc);
                win.document.close();
                win.focus();
                win.print();
            }, text: "打印 / PDF"}),
            docEl("button", {class: "tool-btn", onclick: () => actions.close(), text: "取消"})));
    document.body.append(actions);
    actions.showModal();
}

function downloadBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const link = docEl("a", {href: url, download: fileName});
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    showToast("已导出 " + fileName);
}

/* ================= 工具 ================= */
function bytesToB64(bytes) {
    let binary = "";
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    return btoa(binary);
}

function formatSize(bytes) {
    if (!bytes) return "0 B";
    const units = ["B", "KB", "MB", "GB"];
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`;
}

/* ---- 事件接入 ---- */
window.addEventListener("seeker:route", () => { renderRoute().catch(() => { }); });
window.addEventListener("hashchange", () => {
    if (!location.hash.startsWith("#/docs")) teardownEditor();
});
renderRoute().catch(() => { });
