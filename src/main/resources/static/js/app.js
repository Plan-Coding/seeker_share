const apiUrl = "/api/v1/shares";
const state = {items: [], stats: null, clearProtected: false, address: window.location.origin};
const $ = (selector) => document.querySelector(selector);
const messageForm = $("#messageForm");
const messageInput = $("#messageInput");
const fileInput = $("#fileInput");
const dropZone = $("#dropZone");
const uploadQueue = $("#uploadQueue");
const shareList = $("#shareList");
const emptyState = $("#emptyState");
const clearButton = $("#clearButton");
const toast = $("#toast");

messageInput.addEventListener("input", () => $("#characterCount").textContent = `${messageInput.value.length} / 5000`);
messageInput.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") messageForm.requestSubmit();
});

messageForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const content = messageInput.value.trim();
    if (!content) return;
    const button = messageForm.querySelector("button[type=submit]");
    button.disabled = true;
    triggerTransmission();
    try {
        await request(`${apiUrl}/messages`, {
            method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({content})
        });
        messageForm.reset();
        $("#characterCount").textContent = "0 / 5000";
        showToast("信号发送成功");
        await loadShares();
    } catch (error) {
        showToast(error.message, true);
    } finally {
        button.disabled = false;
    }
});

fileInput.addEventListener("change", () => uploadFiles([...fileInput.files]));
["dragenter", "dragover"].forEach(type => dropZone.addEventListener(type, (event) => {
    event.preventDefault();
    dropZone.classList.add("dragging");
}));
["dragleave", "drop"].forEach(type => dropZone.addEventListener(type, (event) => {
    event.preventDefault();
    dropZone.classList.remove("dragging");
}));
dropZone.addEventListener("drop", event => uploadFiles([...event.dataTransfer.files]));

async function uploadFiles(files) {
    if (!files.length) return;
    for (const file of files) {
        const row = createUploadRow(file);
        uploadQueue.append(row.element);
        try {
            await uploadFile(file, progress => {
                row.bar.style.width = `${progress}%`;
                row.status.textContent = `${progress}%`;
            });
            row.bar.style.width = "100%";
            row.status.textContent = "完成";
            showToast(`${file.name} 上传成功`);
        } catch (error) {
            row.element.classList.add("failed");
            row.status.textContent = "失败";
            showToast(`${file.name}: ${error.message}`, true);
        }
    }
    fileInput.value = "";
    window.setTimeout(() => uploadQueue.replaceChildren(), 1800);
    await loadShares();
}

function createUploadRow(file) {
    const element = document.createElement("div");
    element.className = "upload-row";
    const head = document.createElement("div");
    const name = document.createElement("span");
    name.textContent = file.name;
    const status = document.createElement("span");
    status.textContent = "等待";
    const progress = document.createElement("div");
    progress.className = "progress";
    const bar = document.createElement("i");
    progress.append(bar);
    head.append(name, status);
    element.append(head, progress);
    return {element, status, bar};
}

function uploadFile(file, onProgress) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open("POST", `${apiUrl}/files`);
        xhr.upload.addEventListener("progress", event => {
            if (event.lengthComputable) onProgress(Math.round(event.loaded / event.total * 100));
        });
        xhr.addEventListener("load", () => {
            if (xhr.status >= 200 && xhr.status < 300) resolve();
            else reject(new Error(readError(xhr.responseText, `上传失败 (${xhr.status})`)));
        });
        xhr.addEventListener("error", () => reject(new Error("网络连接中断")));
        const body = new FormData();
        body.append("file", file);
        xhr.send(body);
    });
}

clearButton.addEventListener("click", async () => {
    if (!window.confirm("确定销毁全部消息和文件吗？此操作不可恢复。")) return;
    clearButton.disabled = true;
    try {
        await request(apiUrl, {method: "DELETE", headers: adminHeaders()});
        showToast("全部共享内容已销毁");
        await loadShares();
    } catch (error) {
        handleAdminError(error);
    } finally {
        clearButton.disabled = state.items.length === 0;
    }
});

shareList.addEventListener("click", async (event) => {
    const itemElement = event.target.closest(".share-item");
    if (!itemElement) return;
    if (event.target.closest("[data-copy]")) {
        const content = itemElement.querySelector(".message-content").textContent;
        await copyText(content);
        showToast("消息已复制到剪贴板");
    }
    if (event.target.closest("[data-delete]")) {
        if (!window.confirm("删除这条共享内容？")) return;
        try {
            await request(`${apiUrl}/${itemElement.dataset.id}`, {method: "DELETE", headers: adminHeaders()});
            showToast("共享内容已删除");
            await loadShares();
        } catch (error) {
            handleAdminError(error);
        }
    }
});

$("#searchInput").addEventListener("input", renderShares);
$("#typeFilter").addEventListener("change", renderShares);
$("#copyAddress").addEventListener("click", async () => {
    await copyText(state.address);
    showToast("局域网地址已复制");
});

async function loadServerInfo() {
    try {
        const response = await request("/api/v1/server");
        const info = response.data;
        state.clearProtected = info.clearProtected;
        state.address = info.accessUrls[0] || window.location.origin;
        $("#networkAddress").textContent = state.address;
        $("#hostName").textContent = info.hostName.toUpperCase();
    } catch {
        $("#networkAddress").textContent = window.location.origin;
    }
}

async function loadShares() {
    try {
        const response = await request(apiUrl);
        state.items = response.data.items;
        state.stats = response.data.stats;
        renderMetrics();
        renderShares();
    } catch (error) {
        showToast(error.message, true);
    }
}

function renderMetrics() {
    const stats = state.stats;
    $("#itemMetric").textContent = String(stats.itemCount).padStart(2, "0");
    $("#fileMetric").textContent = String(stats.fileCount).padStart(2, "0");
    $("#storageMetric").textContent = formatSize(stats.storageUsed);
    $("#storageLimit").textContent = `OF ${formatSize(stats.storageLimit)}`;
    $("#storageBar").style.width = `${Math.min(100, stats.storageUsed / stats.storageLimit * 100)}%`;
    $("#itemCount").textContent = stats.itemCount;
    clearButton.disabled = stats.itemCount === 0;
}

function renderShares() {
    const query = $("#searchInput").value.trim().toLowerCase();
    const type = $("#typeFilter").value;
    const items = state.items.filter(item => {
        const haystack = `${item.content || ""} ${item.fileName || ""}`.toLowerCase();
        return (type === "ALL" || item.type === type) && (!query || haystack.includes(query));
    });
    shareList.replaceChildren(...items.map(createShareItem));
    emptyState.hidden = items.length > 0;
    emptyState.querySelector("h3").textContent = state.items.length && !items.length ? "没有匹配的信号" : "等待第一个信号";
}

function createShareItem(item, index) {
    const element = document.createElement("article");
    element.className = `share-item ${item.type === "FILE" ? "file" : "message"}`;
    element.dataset.id = item.id;
    element.style.animationDelay = `${Math.min(index, 8) * 35}ms`;
    const icon = document.createElement("span");
    icon.className = "type-icon";
    icon.textContent = item.type === "MESSAGE" ? "TXT" : fileExtension(item.fileName);
    const body = document.createElement("div");
    body.className = "item-body";
    const meta = document.createElement("span");
    meta.className = "meta";
    const created = document.createElement("span");
    created.textContent = formatDate(item.createdAt);
    const expires = document.createElement("span");
    expires.className = "expires";
    expires.textContent = `${remainingTime(item.expiresAt)}后销毁`;
    meta.append(created);
    if (item.type === "FILE") {
        const size = document.createElement("span");
        size.textContent = formatSize(item.size);
        meta.append(size);
    }
    meta.append(expires);
    const actions = document.createElement("div");
    actions.className = "item-actions";

    if (item.type === "MESSAGE") {
        const content = document.createElement("p");
        content.className = "message-content";
        content.textContent = item.content;
        body.append(content, meta);
        actions.append(actionButton("复制", "copy"));
    } else {
        const name = document.createElement("span");
        name.className = "file-name";
        name.textContent = item.fileName;
        body.append(name, meta);
        const download = document.createElement("a");
        download.href = `${apiUrl}/files/${item.id}`;
        download.textContent = "下载";
        actions.append(download);
    }
    actions.append(actionButton("删除", "delete", "delete"));
    element.append(icon, body, actions);
    return element;
}

function actionButton(label, action, className = "") {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = label;
    button.dataset[action] = "true";
    button.className = className;
    return button;
}

function connectEvents() {
    const source = new EventSource(`${apiUrl}/events`);
    source.addEventListener("open", () => setConnection(true));
    source.addEventListener("refresh", loadShares);
    source.addEventListener("error", () => setConnection(false));
}

function setConnection(connected) {
    $(".connection").classList.toggle("connected", connected);
    $("#connectionText").textContent = connected ? "实时在线" : "正在重连";
}

function adminHeaders() {
    if (!state.clearProtected) return {};
    let token = sessionStorage.getItem("seekerAdminToken");
    if (!token) {
        token = window.prompt("请输入管理员口令") || "";
        if (token) sessionStorage.setItem("seekerAdminToken", token);
    }
    return {"X-Admin-Token": token};
}

function handleAdminError(error) {
    if (error.status === 403) sessionStorage.removeItem("seekerAdminToken");
    showToast(error.message, true);
}

async function request(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const error = new Error(await responseError(response));
        error.status = response.status;
        throw error;
    }
    return response.status === 204 ? null : response.json();
}

async function responseError(response) {
    try {
        const problem = await response.json();
        return problem.detail || problem.message || `请求失败 (${response.status})`;
    } catch { return `请求失败 (${response.status})`; }
}

function readError(text, fallback) {
    try { const body = JSON.parse(text); return body.detail || body.message || fallback; }
    catch { return fallback; }
}

async function copyText(text) {
    try { await navigator.clipboard.writeText(text); }
    catch {
        const area = document.createElement("textarea");
        area.value = text; document.body.append(area); area.select(); document.execCommand("copy"); area.remove();
    }
}

function formatDate(value) {
    return new Intl.DateTimeFormat("zh-CN", {month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"}).format(new Date(value));
}

function remainingTime(value) {
    const minutes = Math.max(0, Math.round((new Date(value) - Date.now()) / 60000));
    if (minutes < 60) return `${minutes} 分钟`;
    return `${Math.ceil(minutes / 60)} 小时`;
}

function formatSize(bytes) {
    if (!bytes) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    const value = bytes / 1024 ** index;
    return `${value.toFixed(value < 10 && index ? 1 : 0)} ${units[index]}`;
}

function fileExtension(name) {
    const extension = name.includes(".") ? name.split(".").pop() : "FILE";
    return extension.slice(0, 4).toUpperCase();
}

let toastTimer;
function showToast(message, error = false) {
    clearTimeout(toastTimer);
    toast.querySelector("span").textContent = message;
    toast.className = `toast visible${error ? " error" : ""}`;
    toastTimer = setTimeout(() => toast.className = "toast", 2600);
}

function startParticles() {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const canvas = $("#particleCanvas");
    const context = canvas.getContext("2d");
    let particles = [];
    const resize = () => {
        const ratio = Math.min(devicePixelRatio, 2);
        canvas.width = innerWidth * ratio; canvas.height = innerHeight * ratio;
        context.setTransform(ratio, 0, 0, ratio, 0, 0);
        particles = Array.from({length: Math.min(55, Math.floor(innerWidth / 24))}, () => ({
            x: Math.random() * innerWidth, y: Math.random() * innerHeight,
            vx: (Math.random() - .5) * .18, vy: (Math.random() - .5) * .18
        }));
    };
    const draw = () => {
        context.clearRect(0, 0, innerWidth, innerHeight);
        context.fillStyle = "rgba(183,255,60,.45)";
        particles.forEach((particle, index) => {
            particle.x += particle.vx; particle.y += particle.vy;
            if (particle.x < 0 || particle.x > innerWidth) particle.vx *= -1;
            if (particle.y < 0 || particle.y > innerHeight) particle.vy *= -1;
            context.fillRect(particle.x, particle.y, 1, 1);
            particles.slice(index + 1).forEach(other => {
                const distance = Math.hypot(particle.x - other.x, particle.y - other.y);
                if (distance < 115) {
                    context.strokeStyle = `rgba(66,232,255,${.07 * (1 - distance / 115)})`;
                    context.beginPath(); context.moveTo(particle.x, particle.y); context.lineTo(other.x, other.y); context.stroke();
                }
            });
        });
        requestAnimationFrame(draw);
    };
    resize(); addEventListener("resize", resize); draw();
}

function startInterfaceEffects() {
    const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
    const bootScreen = $("#bootScreen");
    window.setTimeout(() => bootScreen.classList.add("done"), reducedMotion ? 0 : 1250);

    const clock = $("#systemClock");
    const updateClock = () => {
        clock.textContent = new Intl.DateTimeFormat("zh-CN", {
            hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false
        }).format(new Date());
    };
    updateClock();
    window.setInterval(updateClock, 1000);
    if (reducedMotion || matchMedia("(pointer: coarse)").matches) return;

    const glow = $("#cursorGlow");
    document.addEventListener("pointermove", event => {
        glow.style.left = `${event.clientX}px`;
        glow.style.top = `${event.clientY}px`;
    }, {passive: true});

    document.querySelectorAll(".metric, .node-card").forEach(card => {
        card.addEventListener("pointermove", event => {
            const bounds = card.getBoundingClientRect();
            const x = (event.clientX - bounds.left) / bounds.width - .5;
            const y = (event.clientY - bounds.top) / bounds.height - .5;
            card.style.setProperty("--rx", `${-y * 4}deg`);
            card.style.setProperty("--ry", `${x * 5}deg`);
        });
        card.addEventListener("pointerleave", () => {
            card.style.setProperty("--rx", "0deg");
            card.style.setProperty("--ry", "0deg");
        });
    });
}

function triggerTransmission() {
    document.body.classList.remove("transmitting");
    void document.body.offsetWidth;
    document.body.classList.add("transmitting");
    window.setTimeout(() => document.body.classList.remove("transmitting"), 600);
}

Promise.all([loadServerInfo(), loadShares()]).then(connectEvents);
startParticles();
startInterfaceEffects();
