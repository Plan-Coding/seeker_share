const apiUrl = "/api/v1/shares";
const state = {
    items: [], stats: null, devices: [], auth: null, csrfToken: null,
    protectedLoaded: false, address: window.location.origin,
    admin: {users: [], roles: [], permissions: [], loaded: false}
};
const selectedUserIds = new Set();
const adminPagers = { user: { current: 0 }, role: { current: 0 } };
const PAGE_SIZE = 20;

/* 主题切换 */
const themeToggle = document.querySelector("#themeToggle");
function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    themeToggle.textContent = theme === "light" ? "☀" : "🌙";
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) metaTheme.content = theme === "light" ? "#eef1f4" : "#080a0f";
    try { localStorage.setItem("seeker-theme", theme); } catch { /* ignore */ }
}
themeToggle.addEventListener("click", () => {
    applyTheme(document.documentElement.dataset.theme === "light" ? "dark" : "light");
});
try { applyTheme(localStorage.getItem("seeker-theme") || "light"); }
catch { applyTheme("light"); }

/* ===== 登录凭据加密:与后端一致的加盐哈希 + RSA-OAEP ===== */
const textEncoder = new TextEncoder();

function bytesToHex(bytes) {
    return [...bytes].map(byte => byte.toString(16).padStart(2, "0")).join("");
}

function base64ToBytes(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
}

function bytesToBase64(bytes) {
    let binary = "";
    bytes.forEach(byte => { binary += String.fromCharCode(byte); });
    return btoa(binary);
}

async function passwordDigest(password, salt) {
    if (window.crypto?.subtle) {
        const key = await crypto.subtle.importKey("raw", textEncoder.encode(salt),
            {name: "HMAC", hash: "SHA-256"}, false, ["sign"]);
        const signature = await crypto.subtle.sign("HMAC", key, textEncoder.encode(password));
        return bytesToHex(new Uint8Array(signature));
    }
    if (!window.forge) throw new Error("缺少加密组件，无法登录");
    const hmac = window.forge.hmac.create();
    hmac.start("sha256", window.forge.util.encodeUtf8(salt));
    hmac.update(window.forge.util.encodeUtf8(password));
    return hmac.digest().toHex();
}

async function encryptCredential(publicKeyBase64, plaintext) {
    if (window.crypto?.subtle) {
        const publicKey = await crypto.subtle.importKey("spki", base64ToBytes(publicKeyBase64),
            {name: "RSA-OAEP", hash: "SHA-256"}, false, ["encrypt"]);
        const encrypted = await crypto.subtle.encrypt({name: "RSA-OAEP"}, publicKey, textEncoder.encode(plaintext));
        return bytesToBase64(new Uint8Array(encrypted));
    }
    if (!window.forge) throw new Error("缺少加密组件，无法登录");
    const forge = window.forge;
    const asn1 = forge.asn1.fromDer(forge.util.createBuffer(forge.util.decode64(publicKeyBase64)));
    const publicKey = forge.pki.publicKeyFromAsn1(asn1);
    const cipher = publicKey.encrypt(forge.util.encodeUtf8(plaintext), "RSA-OAEP", {md: forge.md.sha256.create()});
    return forge.util.encode64(cipher);
}

async function buildCredential(password, challenge) {
    const digest = await passwordDigest(password, challenge.salt);
    return encryptCredential(challenge.publicKey, `${digest}:${challenge.nonce}`);
}

async function prelogin(username) {
    const response = await request("/api/v1/auth/prelogin", {
        method: "POST", headers: {"Content-Type": "application/json"},
        body: JSON.stringify({username})
    });
    return response.data;
}

function validatePasswordPolicy(value, username) {
    const errors = [];
    if (value.length < 12) errors.push("至少 12 个字符");
    if (value.length > 128) errors.push("不能超过 128 个字符");
    if (!/[A-Z]/.test(value)) errors.push("包含大写字母");
    if (!/[a-z]/.test(value)) errors.push("包含小写字母");
    if (!/\d/.test(value)) errors.push("包含数字");
    if (!/[^A-Za-z0-9\s]/.test(value)) errors.push("包含特殊字符");
    if (/\s/.test(value)) errors.push("不能包含空白字符");
    if (username && value.toLowerCase().includes(username.toLowerCase())) errors.push("不能包含用户名");
    return errors;
}

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
const authGate = $("#authGate");
const loginForm = $("#loginForm");
const passwordForm = $("#passwordForm");
let eventSource;

loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    setAuthError("login", "");
    const button = loginForm.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const username = $("#loginUsername").value.trim();
        const password = $("#loginPassword").value;
        if (!username || !password) {
            setAuthError("login", "请输入用户名和密码");
            return;
        }
        const challenge = await prelogin(username);
        const credential = await buildCredential(password, challenge);
        const response = await request("/api/v1/auth/login", {
            method: "POST", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({username, credential})
        });
        applyAuth(response.data);
        $("#loginPassword").value = "";
        if (!state.auth.passwordChangeRequired) await startAuthorizedRoute();
    } catch (error) {
        setAuthError("login", error.message);
    } finally {
        button.disabled = false;
    }
});

passwordForm.addEventListener("submit", async event => {
    event.preventDefault();
    setAuthError("password", "");
    const button = passwordForm.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const currentPassword = $("#currentPassword").value;
        const newPassword = $("#newPassword").value;
        const confirmation = $("#confirmPassword").value;
        if (newPassword !== confirmation) {
            setAuthError("password", "两次输入的新密码不一致");
            return;
        }
        const policyErrors = validatePasswordPolicy(newPassword, state.auth?.username);
        if (policyErrors.length) {
            setAuthError("password", "密码强度不足：" + policyErrors.join("、"));
            return;
        }
        const username = state.auth.username;
        const challenge1 = await prelogin(username);
        const currentCredential = await buildCredential(currentPassword, challenge1);
        const challenge2 = await prelogin(username);
        const newCredential = await buildCredential(newPassword, challenge2);
        const response = await request("/api/v1/auth/change-password", {
            method: "POST", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({currentCredential, newCredential})
        });
        applyAuth(response.data);
        passwordForm.reset();
        updatePasswordStrength();
        showToast("密码已更新，欢迎进入共享节点");
        await startAuthorizedRoute();
    } catch (error) {
        setAuthError("password", error.message);
    } finally {
        button.disabled = false;
    }
});

$("#newPassword").addEventListener("input", updatePasswordStrength);
$("#accountButton").addEventListener("click", async () => {
    if (!state.auth?.authenticated) {
        location.hash = "#/share";
        renderAuth();
        $("#loginUsername").focus();
        return;
    }
    try {
        await request("/api/v1/auth/logout", {method: "POST"});
    } finally {
        if (eventSource) eventSource.close();
        eventSource = null;
        state.protectedLoaded = false;
        state.admin = {users: [], roles: [], permissions: [], loaded: false};
        state.items = []; state.devices = []; state.stats = null;
        try {
            await loadAuth();
        } catch {
            state.auth = {authenticated: false, roles: [], permissions: [], passwordChangeRequired: false};
            state.csrfToken = null;
            renderAuth();
        }
        showToast("已安全退出");
    }
});

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
        if (state.csrfToken) xhr.setRequestHeader("X-XSRF-TOKEN", state.csrfToken);
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
        await request(apiUrl, {method: "DELETE"});
        showToast("全部共享内容已销毁");
        await loadShares();
    } catch (error) {
        showToast(error.message, true);
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
            await request(`${apiUrl}/${itemElement.dataset.id}`, {method: "DELETE"});
            showToast("共享内容已删除");
            await loadShares();
        } catch (error) {
            showToast(error.message, true);
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

async function loadDevices() {
    try {
        const response = await request("/api/v1/devices");
        state.devices = response.data;
        renderDevices();
    } catch (error) {
        showToast(`设备扫描失败：${error.message}`, true);
    }
}

function renderDevices() {
    const heatField = $("#heatField");
    const deviceList = $("#deviceList");
    heatField.querySelectorAll(".heat-node").forEach(node => node.remove());
    deviceList.replaceChildren(...state.devices.map(createDeviceRow));
    $("#onlineDeviceCount").textContent = state.devices.length;
    $("#heatEmpty").hidden = state.devices.length > 0;

    state.devices.forEach((device, index) => {
        const node = document.createElement("div");
        const position = devicePosition(device.id, index, state.devices.length);
        const intensity = Math.min(3, device.connectionCount);
        node.className = `heat-node heat-${intensity}`;
        node.style.setProperty("--x", `${position.x}%`);
        node.style.setProperty("--y", `${position.y}%`);
        node.style.setProperty("--delay", `${index * -0.45}s`);
        node.setAttribute("aria-label", `${device.name}，${device.address}，${device.connectionCount} 个连接`);
        node.innerHTML = `<i></i><b>${deviceTypeIcon(device.type)}</b><span></span>`;
        const label = document.createElement("small");
        label.textContent = device.address;
        node.append(label);
        heatField.append(node);
    });
}

function createDeviceRow(device) {
    const row = document.createElement("article");
    row.className = "device-row";
    const icon = document.createElement("span");
    icon.className = `device-icon ${device.type.toLowerCase()}`;
    icon.textContent = deviceTypeIcon(device.type);
    const identity = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = device.name;
    const address = document.createElement("small");
    address.textContent = device.address;
    identity.append(name, address);
    const signal = document.createElement("div");
    signal.className = "device-signal";
    signal.title = `${device.connectionCount} 个实时连接`;
    signal.append(...Array.from({length: 3}, (_, index) => {
        const bar = document.createElement("i");
        if (index < Math.min(3, device.connectionCount)) bar.className = "active";
        return bar;
    }));
    const status = document.createElement("span");
    status.className = "device-status";
    status.textContent = device.connectionCount > 1 ? `${device.connectionCount} 连接` : "在线";
    row.append(icon, identity, signal, status);
    return row;
}

function devicePosition(id, index, total) {
    let hash = 0;
    for (const character of id) hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
    const angle = (index / Math.max(total, 1)) * Math.PI * 2 + (hash % 31) / 31;
    const radius = 25 + (hash % 18);
    return {x: 50 + Math.cos(angle) * radius, y: 50 + Math.sin(angle) * radius * 0.72};
}

function deviceTypeIcon(type) {
    if (type === "MOBILE") return "▯";
    if (type === "TABLET") return "▭";
    return "⌗";
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
    if (eventSource) return;
    eventSource = new EventSource(`${apiUrl}/events`);
    eventSource.addEventListener("open", () => {
        setConnection(true);
        loadDevices();
    });
    eventSource.addEventListener("refresh", loadShares);
    eventSource.addEventListener("devices", loadDevices);
    eventSource.addEventListener("heartbeat", loadDevices);
    eventSource.addEventListener("error", () => setConnection(false));
}

function setConnection(connected) {
    $(".connection").classList.toggle("connected", connected);
    $("#connectionText").textContent = connected ? "实时在线" : "正在重连";
}

async function request(url, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    if (!["GET", "HEAD", "OPTIONS"].includes(method) && state.csrfToken) {
        headers.set("X-XSRF-TOKEN", state.csrfToken);
    }
    const response = await fetch(url, {...options, headers});
    if (!response.ok) {
        const error = new Error(await responseError(response));
        error.status = response.status;
        if (response.status === 401 && !url.endsWith("/login")) {
            state.auth = {authenticated: false, roles: [], permissions: [], passwordChangeRequired: false};
            state.protectedLoaded = false;
            if (eventSource) eventSource.close();
            eventSource = null;
            state.csrfToken = null;
            await loadAuth().catch(renderAuth);
        }
        throw error;
    }
    return response.status === 204 ? null : response.json();
}

async function loadAuth() {
    const response = await request("/api/v1/auth/me");
    applyAuth(response.data);
}

function applyAuth(auth) {
    state.auth = auth;
    if (auth.csrfToken) state.csrfToken = auth.csrfToken;
    renderAuth();
}

function renderAuth() {
    const authenticated = Boolean(state.auth?.authenticated);
    const changeRequired = authenticated && state.auth.passwordChangeRequired;
    const toolsRoute = location.hash.startsWith("#/tools");
    const authOpen = !toolsRoute && (!authenticated || changeRequired);
    authGate.hidden = !authOpen;
    document.documentElement.classList.toggle("auth-open", authOpen);
    loginForm.hidden = authenticated;
    passwordForm.hidden = !changeRequired;
    const account = $("#accountButton");
    account.classList.toggle("authenticated", authenticated);
    account.querySelector("span").textContent = authenticated ? `${state.auth.username} · 退出` : "登录";
    $("#adminNav").hidden = !authenticated || !hasAnyPermission("USER_MANAGE", "ROLE_MANAGE");
    $("#docsNav").hidden = !authenticated || !hasPermission("DOCUMENT_READ");
    if (!authenticated) {
        $("#connectionText").textContent = "等待登录";
        $(".connection").classList.remove("connected");
    }
}

async function startProtectedFeatures() {
    if (!state.auth?.authenticated || state.auth.passwordChangeRequired) return;
    if (!state.protectedLoaded) {
        await Promise.all([loadShares(), loadDevices()]);
        state.protectedLoaded = true;
    }
    connectEvents();
    renderAuth();
}

function setAuthError(form, message) {
    $(`#${form}Error`).textContent = message;
}

function updatePasswordStrength() {
    const value = $("#newPassword").value;
    const rules = {
        length: value.length >= 12, upper: /[A-Z]/.test(value), lower: /[a-z]/.test(value),
        digit: /\d/.test(value), special: /[^A-Za-z0-9\s]/.test(value), space: !/\s/.test(value)
    };
    $("#passwordStrength").querySelectorAll("[data-rule]").forEach(item =>
        item.classList.toggle("valid", rules[item.dataset.rule]));
}

function syncAuthRoute() {
    renderAuth();
    startAuthorizedRoute();
}

async function startAuthorizedRoute() {
    const root = location.hash.replace(/^#\/?/, "").split("/")[0] || "share";
    if (root === "share") await startProtectedFeatures();
    if (root === "admin") await loadAdminData();
}

function hasPermission(permission) {
    return Boolean(state.auth?.permissions?.includes(permission));
}

function hasAnyPermission(...permissions) {
    return permissions.some(hasPermission);
}

async function loadAdminData(force = false) {
    const authenticated = state.auth?.authenticated && !state.auth.passwordChangeRequired;
    const canManageUsers = hasPermission("USER_MANAGE");
    const canManageRoles = hasPermission("ROLE_MANAGE");
    $("#adminDenied").hidden = authenticated && (canManageUsers || canManageRoles);
    $("#userManagement").hidden = !authenticated || !canManageUsers;
    $("#roleManagement").hidden = !authenticated || !canManageRoles;
    if (!authenticated || (!canManageUsers && !canManageRoles)) return;
    if (state.admin.loaded && !force) {
        renderAdmin();
        return;
    }
    try {
        const [usersResponse, rolesResponse, permissionsResponse] = await Promise.all([
            canManageUsers ? request("/api/v1/admin/users") : Promise.resolve({data: []}),
            canManageRoles ? request("/api/v1/admin/roles") : Promise.resolve({data: []}),
            canManageRoles ? request("/api/v1/admin/permissions") : Promise.resolve({data: []})
        ]);
        state.admin = {
            users: usersResponse.data,
            roles: rolesResponse.data,
            permissions: permissionsResponse.data,
            loaded: true
        };
        renderAdmin();
    } catch (error) {
        showToast(`管理数据加载失败：${error.message}`, true);
    }
}

function renderAdmin() {
    selectedUserIds.clear();
    adminPagers.user.current = 0;
    adminPagers.role.current = 0;
    $("#adminUserCount").textContent = state.admin.users.length;
    $("#adminRoleCount").textContent = state.admin.roles.length;
    $("#adminPermissionCount").textContent = state.admin.permissions.length;
    renderNewUserRoles();
    renderNewRolePermissions();
    renderAdminUsers();
    renderAdminRoles();
    updateBatchSelection();
}

function renderNewUserRoles() {
    const fieldset = $("#newUserRoles");
    fieldset.hidden = !hasPermission("ROLE_MANAGE");
    const target = fieldset.querySelector(".admin-checks");
    target.replaceChildren(...state.admin.roles.map(role => adminCheck("new-user-role", role.name, role.name, role.name === "MEMBER")));
}

function renderNewRolePermissions() {
    $("#newRolePermissions").replaceChildren(...state.admin.permissions.map(permission =>
        adminCheck("new-role-permission", permission.code, permission.description, false, permission.code)));
}

function paginate(items, kind) {
    const pager = adminPagers[kind];
    const pages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
    pager.current = Math.min(Math.max(pager.current, 0), pages - 1);
    const start = pager.current * PAGE_SIZE;
    return {page: items.slice(start, start + PAGE_SIZE), total: items.length, pages, current: pager.current};
}

function refreshPagination(kind, total) {
    const pager = adminPagers[kind];
    const prefix = kind === "user" ? "adminUser" : "adminRole";
    const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    const box = $(`#${prefix}Pagination`);
    box.hidden = total <= PAGE_SIZE;
    $(`#${prefix}PageNow`).textContent = pager.current + 1;
    $(`#${prefix}PageTotal`).textContent = pages;
    $(`#${prefix}Total`).textContent = total;
    box.querySelector('[data-dir="prev"]').disabled = pager.current === 0;
    box.querySelector('[data-dir="next"]').disabled = pager.current >= pages - 1;
}

function renderAdminUsers() {
    const query = $("#adminUserSearch").value.trim().toLowerCase();
    const filtered = state.admin.users.filter(user =>
        `${user.username} ${user.roles.join(" ")}`.toLowerCase().includes(query));
    const result = paginate(filtered, "user");
    $("#adminUserList").replaceChildren(...result.page.map(createAdminUser));
    refreshPagination("user", result.total);
}

function createAdminUser(user) {
    const current = user.username === state.auth.username;
    const card = document.createElement("article");
    card.className = "admin-user";
    card.dataset.userId = user.id;

    const select = document.createElement("input");
    select.type = "checkbox";
    select.className = "user-select";
    select.value = user.id;
    select.checked = selectedUserIds.has(user.id);
    select.disabled = current;
    if (current) select.title = "当前账户不可删除";
    card.append(select);

    const identity = document.createElement("div");
    identity.className = "admin-user-identity";
    const title = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = user.username;
    const status = document.createElement("span");
    status.className = `status-chip ${!user.enabled || !user.accountNonLocked ? "danger" : ""}`;
    status.textContent = !user.enabled ? "已停用" : !user.accountNonLocked ? "已锁定" : "正常";
    title.append(name, status);
    const details = document.createElement("small");
    details.textContent = `创建 ${formatAdminDate(user.createdAt)} · 最近登录 ${formatAdminDate(user.lastLoginAt)}`;
    identity.append(title, details);

    const badges = document.createElement("div");
    badges.className = "role-badges";
    user.roles.forEach(role => {
        const badge = document.createElement("span");
        badge.textContent = role;
        badges.append(badge);
    });
    if (user.passwordChangeRequired) {
        const badge = document.createElement("span");
        badge.className = "warning";
        badge.textContent = "待改密码";
        badges.append(badge);
    }

    const actions = document.createElement("div");
    actions.className = "admin-actions";
    actions.append(
        adminAction(user.enabled ? "停用" : "启用", "toggle-status", user.enabled ? "danger" : ""),
        adminAction("解锁", "unlock", "", user.accountNonLocked),
        adminAction("重置密码", "reset-password")
    );
    if (current) actions.querySelector('[data-action="toggle-status"]').disabled = true;

    card.append(identity, badges, actions);
    if (hasPermission("ROLE_MANAGE")) {
        const editor = document.createElement("form");
        editor.className = "user-role-editor";
        editor.dataset.userId = user.id;
        const checks = document.createElement("div");
        checks.className = "admin-checks";
        checks.append(...state.admin.roles.map(role =>
            adminCheck(`user-role-${user.id}`, role.name, role.name, user.roles.includes(role.name))));
        const save = document.createElement("button");
        save.className = "admin-button primary";
        save.type = "submit";
        save.textContent = current ? "当前账户不可修改" : "保存角色";
        save.disabled = current;
        editor.append(checks, save);
        card.append(editor);
    }
    return card;
}

function renderAdminRoles() {
    const query = $("#adminRoleSearch").value.trim().toLowerCase();
    const filtered = state.admin.roles.filter(role =>
        `${role.name} ${role.description} ${role.permissions.join(" ")}`.toLowerCase().includes(query));
    const result = paginate(filtered, "role");
    $("#adminRoleList").replaceChildren(...result.page.map(role => {
        const form = document.createElement("form");
        form.className = "admin-role-card glass";
        form.dataset.roleId = role.id;
        const heading = document.createElement("div");
        heading.className = "role-card-head";
        const name = document.createElement("strong");
        name.textContent = role.name;
        const badge = document.createElement("span");
        badge.textContent = role.builtIn ? "内置" : "自定义";
        heading.append(name, badge);
        const description = document.createElement("input");
        description.className = "role-description";
        description.maxLength = 100;
        description.required = true;
        description.value = role.description;
        description.disabled = role.name === "ADMIN";
        const checks = document.createElement("div");
        checks.className = "admin-checks permission-checks";
        checks.append(...state.admin.permissions.map(permission => {
            const item = adminCheck(`permission-${role.id}`, permission.code, permission.description,
                role.permissions.includes(permission.code), permission.code);
            item.querySelector("input").disabled = role.name === "ADMIN";
            return item;
        }));
        const actions = document.createElement("div");
        actions.className = "admin-actions";
        if (role.name !== "ADMIN") actions.append(adminAction("保存权限", "save-role", "primary"));
        if (!role.builtIn) actions.append(adminAction("删除角色", "delete-role", "danger"));
        form.append(heading, description, checks, actions);
        return form;
    }));
    refreshPagination("role", result.total);
}

function adminCheck(group, value, label, checked, code = "") {
    const wrapper = document.createElement("label");
    wrapper.className = "admin-check";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.name = group;
    input.value = value;
    input.checked = checked;
    const text = document.createElement("span");
    const strong = document.createElement("strong");
    strong.textContent = label;
    text.append(strong);
    if (code) {
        const small = document.createElement("small");
        small.textContent = code;
        text.append(small);
    }
    wrapper.append(input, text);
    return wrapper;
}

function adminAction(label, action, className = "", disabled = false) {
    const button = document.createElement("button");
    button.type = action === "save-role" ? "submit" : "button";
    button.className = `admin-button ${className}`.trim();
    button.dataset.action = action;
    button.textContent = label;
    button.disabled = disabled;
    return button;
}

function checkedValues(root) {
    return [...root.querySelectorAll('input[type="checkbox"]:checked')].map(input => input.value);
}

function formatAdminDate(value) {
    return value ? new Intl.DateTimeFormat("zh-CN", {dateStyle: "short", timeStyle: "short"}).format(new Date(value)) : "从未";
}

$("#refreshAdmin").addEventListener("click", () => loadAdminData(true));
$("#adminUserSearch").addEventListener("input", () => {
    adminPagers.user.current = 0;
    renderAdminUsers();
    updateBatchSelection();
});
$("#adminRoleSearch").addEventListener("input", () => {
    adminPagers.role.current = 0;
    renderAdminRoles();
});

$("#viewAdmin").addEventListener("click", event => {
    const button = event.target.closest(".admin-pagination button");
    if (!button) return;
    const kind = button.dataset.list;
    adminPagers[kind].current += button.dataset.dir === "prev" ? -1 : 1;
    if (kind === "user") { renderAdminUsers(); updateBatchSelection(); }
    else renderAdminRoles();
});

function updateBatchSelection() {
    const checkboxes = [...document.querySelectorAll("#adminUserList .user-select:not(:disabled)")];
    const selectedCount = checkboxes.filter(cb => cb.checked).length;
    const selectAll = $("#adminSelectAll");
    selectAll.checked = checkboxes.length > 0 && selectedCount === checkboxes.length;
    selectAll.indeterminate = selectedCount > 0 && selectedCount < checkboxes.length;
    $("#adminBatchDelete").disabled = selectedUserIds.size === 0;
    const label = $("#adminSelectedCount");
    label.textContent = `已选 ${selectedUserIds.size} 个用户`;
    label.hidden = selectedUserIds.size === 0;
}

$("#adminUserList").addEventListener("change", event => {
    const checkbox = event.target.closest(".user-select");
    if (!checkbox) return;
    if (checkbox.checked) selectedUserIds.add(checkbox.value);
    else selectedUserIds.delete(checkbox.value);
    updateBatchSelection();
});

$("#adminSelectAll").addEventListener("change", event => {
    const select = event.currentTarget.checked;
    document.querySelectorAll("#adminUserList .user-select:not(:disabled)").forEach(cb => {
        cb.checked = select;
        if (select) selectedUserIds.add(cb.value);
        else selectedUserIds.delete(cb.value);
    });
    updateBatchSelection();
});

$("#adminBatchDelete").addEventListener("click", async () => {
    if (selectedUserIds.size === 0) return;
    const names = state.admin.users
        .filter(user => selectedUserIds.has(user.id))
        .map(user => user.username).join("、");
    if (!window.confirm(`确定删除选中的 ${selectedUserIds.size} 个用户？\n${names}\n该操作不可恢复。`)) return;
    const button = $("#adminBatchDelete");
    button.disabled = true;
    try {
        const response = await request("/api/v1/admin/users", {
            method: "DELETE", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({ids: [...selectedUserIds]})
        });
        selectedUserIds.clear();
        showToast(`已删除 ${response.data.deleted} 个用户`);
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
    finally { updateBatchSelection(); }
});

$("#createUserForm").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const usernameInput = $("#adminNewUsername");
        const username = usernameInput.value.trim();
        if (!/^[a-zA-Z0-9_.-]{3,50}$/.test(username)) {
            showToast("用户名只能包含字母、数字、点、横线和下划线，长度 3-50 位", true);
            return;
        }
        const password = $("#adminNewPassword").value;
        const policyErrors = validatePasswordPolicy(password, username);
        if (policyErrors.length) {
            showToast("密码强度不足：" + policyErrors.join("、"), true);
            return;
        }
        const roles = hasPermission("ROLE_MANAGE") ? checkedValues($("#newUserRoles")) : [];
        const challenge = await prelogin(username);
        const credential = await buildCredential(password, challenge);
        await request("/api/v1/admin/users", {
            method: "POST", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({username, credential, roles})
        });
        form.reset();
        showToast("用户已创建");
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
    finally { button.disabled = false; }
});

$("#createRoleForm").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        await request("/api/v1/admin/roles", {
            method: "POST", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                name: $("#adminNewRoleName").value.trim().toUpperCase(),
                description: $("#adminNewRoleDescription").value,
                permissions: checkedValues($("#newRolePermissions"))
            })
        });
        form.reset();
        showToast("角色已创建");
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
    finally { button.disabled = false; }
});

$("#adminUserList").addEventListener("submit", async event => {
    const form = event.target.closest(".user-role-editor");
    if (!form) return;
    event.preventDefault();
    try {
        await request(`/api/v1/admin/users/${form.dataset.userId}/roles`, {
            method: "PUT", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({roles: checkedValues(form)})
        });
        showToast("用户角色已更新");
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
});

let resetPasswordUserId = null;
$("#adminUserList").addEventListener("click", async event => {
    const button = event.target.closest("[data-action]");
    const card = event.target.closest(".admin-user");
    if (!button || !card) return;
    const user = state.admin.users.find(item => item.id === card.dataset.userId);
    if (button.dataset.action === "reset-password") {
        resetPasswordUserId = user.id;
        $("#resetPasswordValue").value = "";
        $("#resetPasswordDialog").showModal();
        $("#resetPasswordValue").focus();
        return;
    }
    try {
        const body = button.dataset.action === "unlock" ? {unlock: true} : {enabled: !user.enabled, unlock: false};
        await request(`/api/v1/admin/users/${user.id}/status`, {
            method: "PATCH", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)
        });
        showToast(button.dataset.action === "unlock" ? "账户已解锁" : "账户状态已更新");
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
});

$("#resetPasswordForm").addEventListener("submit", async event => {
    event.preventDefault();
    try {
        const user = state.admin.users.find(item => item.id === resetPasswordUserId);
        const password = $("#resetPasswordValue").value;
        const policyErrors = validatePasswordPolicy(password, user.username);
        if (policyErrors.length) {
            showToast("密码强度不足：" + policyErrors.join("、"), true);
            return;
        }
        const challenge = await prelogin(user.username);
        const credential = await buildCredential(password, challenge);
        await request(`/api/v1/admin/users/${resetPasswordUserId}/reset-password`, {
            method: "POST", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({credential})
        });
        $("#resetPasswordDialog").close();
        showToast("初始密码已重置");
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
});
$("#resetPasswordDialog .dialog-actions [value=cancel]").addEventListener("click", () => $("#resetPasswordDialog").close());

$("#adminRoleList").addEventListener("submit", async event => {
    const form = event.target.closest(".admin-role-card");
    if (!form) return;
    event.preventDefault();
    try {
        await request(`/api/v1/admin/roles/${form.dataset.roleId}`, {
            method: "PUT", headers: {"Content-Type": "application/json"},
            body: JSON.stringify({description: form.querySelector(".role-description").value, permissions: checkedValues(form)})
        });
        showToast("角色权限已更新");
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
});

$("#adminRoleList").addEventListener("click", async event => {
    const button = event.target.closest('[data-action="delete-role"]');
    const form = event.target.closest(".admin-role-card");
    if (!button || !form) return;
    const role = state.admin.roles.find(item => item.id === form.dataset.roleId);
    if (!window.confirm(`确定删除角色 ${role.name}？`)) return;
    try {
        await request(`/api/v1/admin/roles/${role.id}`, {method: "DELETE"});
        showToast("角色已删除");
        await loadAdminData(true);
    } catch (error) { showToast(error.message, true); }
});

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
    if (toast.popover) {
        try { if (toast.matches(":popover-open")) toast.hidePopover(); } catch { }
        try { toast.showPopover(); } catch { }
    }
    toastTimer = setTimeout(() => {
        toast.className = "toast";
        if (toast.popover) { try { toast.hidePopover(); } catch { } }
    }, 2600);
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

Promise.all([loadServerInfo(), loadAuth()]).then(syncAuthRoute).catch(error => showToast(error.message, true));
window.addEventListener("seeker:route", syncAuthRoute);
startParticles();
startInterfaceEffects();
