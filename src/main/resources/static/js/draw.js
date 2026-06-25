const CANVAS_W = 800, CANVAS_H = 600;

// Cấu trúc layer: { id, name, canvas, ctx, visible, opacity }
let layers = [];
let activeLayerIndex = 0;
let layerCounter = 0;

function createLayerCanvas(zIndex) {
    const c = document.createElement('canvas');
    c.width = CANVAS_W;
    c.height = CANVAS_H;
    c.style.position = 'absolute';
    c.style.top = '0';
    c.style.left = '0';
    c.style.zIndex = zIndex;
    c.style.pointerEvents = 'none';
    return c;
}

function addLayer(name, isBackground) {
    const canvasStack = document.getElementById('canvasStack');
    if (!canvasStack) return null;
    layerCounter++;
    const canvasEl = createLayerCanvas(layers.length + 1);
    const regionSelectBox = document.getElementById('regionSelectBox');
    if (regionSelectBox) {
        canvasStack.insertBefore(canvasEl, regionSelectBox);
    } else {
        canvasStack.appendChild(canvasEl);
    }
    const ctxL = canvasEl.getContext('2d');
    if (isBackground) {
        ctxL.fillStyle = '#ffffff';
        ctxL.fillRect(0, 0, CANVAS_W, CANVAS_H);
    }
    const layer = {
        id: layerCounter,
        name: name || ('Layer ' + layerCounter),
        canvas: canvasEl,
        ctx: ctxL,
        visible: true,
        opacity: 100
    };
    layers.push(layer);
    renderLayerList();
    return layer;
}

function getActiveLayer() {
    return layers[activeLayerIndex];
}

function renderLayerList() {
    const listEl = document.getElementById('layerList');
    if (!listEl) return;
    listEl.innerHTML = '';
    for (let i = layers.length - 1; i >= 0; i--) {
        const layer = layers[i];
        const item = document.createElement('div');
        item.className = 'layer-item' + (i === activeLayerIndex ? ' selected' : '');
        item.innerHTML = `
            <button type="button" class="layer-toggle-visible ${layer.visible ? '' : 'hidden-layer'}" data-idx="${i}">
                <i class="fa-solid ${layer.visible ? 'fa-eye' : 'fa-eye-slash'}"></i>
            </button>
            <div class="layer-thumb"></div>
            <span class="layer-name">${layer.name}</span>
        `;
        item.addEventListener('click', (e) => {
            if (e.target.closest('.layer-toggle-visible')) return;
            activeLayerIndex = i;
            renderLayerList();
        });
        listEl.appendChild(item);

        if (i === activeLayerIndex) {
            const opacityRow = document.createElement('div');
            opacityRow.className = 'layer-opacity-row';
            opacityRow.innerHTML = `
                <span style="font-size:10px;color:var(--ps-text-dim);">Opacity</span>
                <input type="range" min="0" max="100" value="${layer.opacity}" data-idx="${i}" class="layer-opacity-slider">
                <span>${layer.opacity}%</span>
            `;
            listEl.appendChild(opacityRow);
        }
    }

    listEl.querySelectorAll('.layer-toggle-visible').forEach(btn => {
        btn.addEventListener('click', () => {
            const idx = parseInt(btn.dataset.idx);
            layers[idx].visible = !layers[idx].visible;
            layers[idx].canvas.style.display = layers[idx].visible ? 'block' : 'none';
            renderLayerList();
        });
    });

    listEl.querySelectorAll('.layer-opacity-slider').forEach(slider => {
        slider.addEventListener('input', () => {
            const idx = parseInt(slider.dataset.idx);
            layers[idx].opacity = slider.value;
            layers[idx].canvas.style.opacity = slider.value / 100;
            slider.nextElementSibling.textContent = slider.value + '%';
        });
    });
}

let isDrawing = false;
let currentTool = 'pencil';
let history = [];
let shapeStart = null;
let shapeFillMode = 'outline';
let historyListEl = null;
let inputLayer = null;
let previewCtx = null;

function snapshotAllLayers() {
    return { idx: activeLayerIndex, data: getActiveLayer().canvas.toDataURL() };
}

function pushHistoryEntry(label) {
    history.push(snapshotAllLayers());
    if (history.length > 30) history.shift();
    if (!historyListEl) return;
    const item = document.createElement('div');
    item.className = 'history-item current';
    item.textContent = label;
    Array.from(historyListEl.children).forEach(c => c.classList.remove('current'));
    historyListEl.appendChild(item);
    historyListEl.scrollTop = historyListEl.scrollHeight;
}

function getPos(e) {
    if (!inputLayer) return { x: 0, y: 0 };
    const rect = inputLayer.getBoundingClientRect();
    const src = e.touches ? e.touches[0] : e;
    return {
        x: (src.clientX - rect.left) * (CANVAS_W / rect.width),
        y: (src.clientY - rect.top) * (CANVAS_H / rect.height)
    };
}

function getFgColor() {
    return document.getElementById('penColor')?.value || '#000000';
}

function getOpacity() {
    return (document.getElementById('penOpacity')?.value || 100) / 100;
}

function getSize() {
    return document.getElementById('penSize')?.value || 6;
}

// ---- Bucket fill ----
function floodFill(ctxTarget, startX, startY, fillColorHex) {
    startX = Math.floor(startX);
    startY = Math.floor(startY);
    const imgData = ctxTarget.getImageData(0, 0, CANVAS_W, CANVAS_H);
    const data = imgData.data;
    const fillColor = hexToRgba(fillColorHex);
    const startIdx = (startY * CANVAS_W + startX) * 4;
    const targetColor = [data[startIdx], data[startIdx + 1], data[startIdx + 2], data[startIdx + 3]];
    if (colorsMatch(targetColor, fillColor)) return;
    const stack = [[startX, startY]];
    const visited = new Uint8Array(CANVAS_W * CANVAS_H);
    while (stack.length) {
        const [x, y] = stack.pop();
        if (x < 0 || x >= CANVAS_W || y < 0 || y >= CANVAS_H) continue;
        const pos = y * CANVAS_W + x;
        if (visited[pos]) continue;
        const idx = pos * 4;
        const currentColor = [data[idx], data[idx + 1], data[idx + 2], data[idx + 3]];
        if (!colorsMatch(currentColor, targetColor)) continue;
        visited[pos] = 1;
        data[idx] = fillColor[0];
        data[idx + 1] = fillColor[1];
        data[idx + 2] = fillColor[2];
        data[idx + 3] = fillColor[3];
        stack.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
    }
    ctxTarget.putImageData(imgData, 0, 0);
}

function colorsMatch(a, b) {
    return Math.abs(a[0] - b[0]) < 12 && Math.abs(a[1] - b[1]) < 12 &&
        Math.abs(a[2] - b[2]) < 12 && Math.abs(a[3] - b[3]) < 12;
}

function hexToRgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return [r, g, b, alpha !== undefined ? Math.round(alpha * 255) : 255];
}

// ---- Text tool ----
function placeTextAt(pos) {
    const text = prompt('Nhập nội dung chữ:');
    if (!text) return;
    const layerCtx = getActiveLayer().ctx;
    layerCtx.globalAlpha = getOpacity();
    layerCtx.fillStyle = getFgColor();
    layerCtx.font = (document.getElementById('textSize')?.value || 24) + 'px Arial';
    layerCtx.textBaseline = 'top';
    layerCtx.fillText(text, pos.x, pos.y);
    layerCtx.globalAlpha = 1;
    pushHistoryEntry('Chèn chữ: "' + text.substring(0, 20) + '"');
}

// ---- Shape preview ----
function clearShapePreview() {
    if (previewCtx) previewCtx.clearRect(0, 0, CANVAS_W, CANVAS_H);
}

function drawShapeOnContext(ctxTarget, start, end) {
    ctxTarget.lineWidth = getSize();
    ctxTarget.strokeStyle = getFgColor();
    ctxTarget.fillStyle = getFgColor();
    ctxTarget.globalAlpha = getOpacity();
    ctxTarget.beginPath();
    if (currentTool === 'line') {
        ctxTarget.moveTo(start.x, start.y);
        ctxTarget.lineTo(end.x, end.y);
        ctxTarget.stroke();
    } else if (currentTool === 'rect') {
        const w = end.x - start.x, h = end.y - start.y;
        if (shapeFillMode === 'filled') ctxTarget.fillRect(start.x, start.y, w, h);
        else ctxTarget.strokeRect(start.x, start.y, w, h);
    } else if (currentTool === 'oval') {
        const cx = (start.x + end.x) / 2, cy = (start.y + end.y) / 2;
        const rx = Math.abs(end.x - start.x) / 2, ry = Math.abs(end.y - start.y) / 2;
        ctxTarget.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
        if (shapeFillMode === 'filled') ctxTarget.fill();
        else ctxTarget.stroke();
    }
    ctxTarget.globalAlpha = 1;
}

function redrawShapePreview(start, end) {
    clearShapePreview();
    drawShapeOnContext(previewCtx, start, end);
}

function commitShape(start, end) {
    drawShapeOnContext(getActiveLayer().ctx, start, end);
}

// ---- Region selection state ----
let selectionRect = null;
let selectionShape = 'rect';
let selectionPath = [];
let isSelecting = false;
let selStart = null;

function isSelectTool(tool) {
    return tool === 'select' || tool === 'select-oval' || tool === 'select-free';
}

// ---- AI modal state ----
let selectedFeature = null;
let regionMode = 'full';
let lastUsedRegion = null;

function flattenAllLayers() {
    const flat = document.createElement('canvas');
    flat.width = CANVAS_W;
    flat.height = CANVAS_H;
    const flatCtx = flat.getContext('2d');
    layers.forEach(layer => {
        if (!layer.visible) return;
        flatCtx.globalAlpha = layer.opacity / 100;
        flatCtx.drawImage(layer.canvas, 0, 0);
    });
    flatCtx.globalAlpha = 1;
    return flat;
}

function canvasRegionToBase64() {
    return flattenAllLayers().toDataURL('image/png').split(',')[1];
}

function generateMaskBase64() {
    if (regionMode !== 'select' || !selectionRect || selectionRect.w <= 5) return null;
    const mask = document.createElement('canvas');
    mask.width = CANVAS_W;
    mask.height = CANVAS_H;
    const mCtx = mask.getContext('2d');
    mCtx.fillStyle = '#ffffff';
    mCtx.fillRect(0, 0, CANVAS_W, CANVAS_H);
    mCtx.globalCompositeOperation = 'destination-out';
    mCtx.fillStyle = '#000000';
    if (selectionShape === 'oval') {
        const cx = selectionRect.x + selectionRect.w / 2;
        const cy = selectionRect.y + selectionRect.h / 2;
        mCtx.beginPath();
        mCtx.ellipse(cx, cy, selectionRect.w / 2, selectionRect.h / 2, 0, 0, Math.PI * 2);
        mCtx.fill();
    } else if (selectionShape === 'free' && selectionPath.length > 2) {
        mCtx.beginPath();
        mCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
        for (let i = 1; i < selectionPath.length; i++) mCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
        mCtx.closePath();
        mCtx.fill();
    } else {
        mCtx.fillRect(selectionRect.x, selectionRect.y, selectionRect.w, selectionRect.h);
    }
    mCtx.globalCompositeOperation = 'source-over';
    return mask.toDataURL('image/png').split(',')[1];
}

// ---- AI result ----
function showFeatureListView() {
    document.getElementById('aiFeatureListView').style.display = 'block';
    document.getElementById('aiDetailView').style.display = 'none';
    document.getElementById('aiLoadingView').style.display = 'none';
    document.getElementById('aiResultView').style.display = 'none';
}
function showAiDetailView() {
    document.getElementById('aiFeatureListView').style.display = 'none';
    document.getElementById('aiDetailView').style.display = 'block';
    document.getElementById('aiLoadingView').style.display = 'none';
    document.getElementById('aiResultView').style.display = 'none';
}
function showAiLoadingView() {
    document.getElementById('aiFeatureListView').style.display = 'none';
    document.getElementById('aiDetailView').style.display = 'none';
    document.getElementById('aiLoadingView').style.display = 'block';
    document.getElementById('aiResultView').style.display = 'none';
}
function showAiResultView() {
    document.getElementById('aiFeatureListView').style.display = 'none';
    document.getElementById('aiDetailView').style.display = 'none';
    document.getElementById('aiLoadingView').style.display = 'none';
    document.getElementById('aiResultView').style.display = 'block';
}

function applyAiResultToCanvas(base64) {
    const img = new Image();
    img.onload = () => {
        const layerCtx = getActiveLayer().ctx;
        if (lastUsedRegion && lastUsedRegion.mode === 'select' && lastUsedRegion.rect) {
            const r = lastUsedRegion.rect;
            layerCtx.save();
            layerCtx.beginPath();
            if (lastUsedRegion.shape === 'oval') {
                layerCtx.ellipse(r.x + r.w / 2, r.y + r.h / 2, r.w / 2, r.h / 2, 0, 0, Math.PI * 2);
            } else if (lastUsedRegion.shape === 'free' && lastUsedRegion.path?.length > 2) {
                layerCtx.moveTo(lastUsedRegion.path[0].x, lastUsedRegion.path[0].y);
                for (let i = 1; i < lastUsedRegion.path.length; i++)
                    layerCtx.lineTo(lastUsedRegion.path[i].x, lastUsedRegion.path[i].y);
                layerCtx.closePath();
            } else {
                layerCtx.rect(r.x, r.y, r.w, r.h);
            }
            layerCtx.clip();
            layerCtx.drawImage(img, r.x, r.y, r.w, r.h);
            layerCtx.restore();
        } else {
            layerCtx.drawImage(img, 0, 0, CANVAS_W, CANVAS_H);
        }
        pushHistoryEntry('AI: ' + (selectedFeature ? selectedFeature.name : ''));
        const aiModalOverlay = document.getElementById('aiModalOverlay');
        if (aiModalOverlay) aiModalOverlay.style.display = 'none';
    };
    img.src = 'data:image/png;base64,' + base64;
}

function renderAiResult(data) {
    showAiResultView();
    const titleEl = document.getElementById('aiResultTitle');
    const imgEl = document.getElementById('aiResultImage');
    const textEl = document.getElementById('aiResultText');
    const errEl = document.getElementById('aiErrorText');
    const applyBtn = document.getElementById('btnApplyToCanvas');
    imgEl.style.display = 'none';
    textEl.style.display = 'none';
    errEl.style.display = 'none';
    if (applyBtn) applyBtn.style.display = 'none';
    if (data.status === 'success') {
        titleEl.textContent = 'Hoàn thành — ' + (selectedFeature ? selectedFeature.name : '');
        if (data.type === 'image_base64' || data.type === 'image') {
            imgEl.src = data.type === 'image_base64' ? 'data:image/png;base64,' + data.result : data.result;
            imgEl.style.display = 'block';
            if (applyBtn) {
                applyBtn.style.display = 'inline-block';
                applyBtn.onclick = () => applyAiResultToCanvas(data.result);
            }
        } else if (data.type === 'text') {
            textEl.textContent = data.result;
            textEl.style.display = 'block';
        }
    } else {
        titleEl.textContent = 'Có lỗi xảy ra';
        errEl.textContent = data.message || 'Lỗi không xác định';
        errEl.style.display = 'block';
    }
}

// ========================================================
// KHỞI TẠO — chỉ chạy sau khi DOM ready
// ========================================================
document.addEventListener('DOMContentLoaded', () => {
    const canvasStack = document.getElementById('canvasStack');
    if (!canvasStack) return; // không ở tab draw, bỏ qua

    historyListEl = document.getElementById('historyList');
    inputLayer = document.getElementById('drawCanvas');
    if (!inputLayer) return;

    previewCtx = inputLayer.getContext('2d');
    inputLayer.style.zIndex = 999;
    inputLayer.style.pointerEvents = 'auto';

    // Tạo layers ban đầu
    const baseLayer = addLayer('Nền (trắng)', true);
    if (baseLayer) baseLayer.isBase = true;
    addLayer('Layer 1', false);
    activeLayerIndex = layers.length - 1;

    pushHistoryEntry('Tạo canvas mới');

    const regionBox = document.getElementById('regionSelectBox');
    const canvasViewport = document.getElementById('canvasViewport');

    // ---- Mouse events ----
    inputLayer.addEventListener('mousedown', (e) => {
        if (isSelectTool(currentTool)) {
            isSelecting = true;
            selStart = getPos(e);
            clearShapePreview();
            if (currentTool === 'select') {
                selectionShape = 'rect';
                if (regionBox) { regionBox.style.borderRadius = '0'; regionBox.style.display = 'block'; }
            } else if (currentTool === 'select-oval') {
                selectionShape = 'oval';
                if (regionBox) { regionBox.style.borderRadius = '50%'; regionBox.style.display = 'block'; }
            } else if (currentTool === 'select-free') {
                selectionShape = 'free';
                selectionPath = [selStart];
                if (regionBox) regionBox.style.display = 'none';
            }
            return;
        }
        const p = getPos(e);
        const layerCtx = getActiveLayer().ctx;
        if (currentTool === 'pencil' || currentTool === 'brush' || currentTool === 'eraser') {
            isDrawing = true;
            layerCtx.beginPath();
            layerCtx.moveTo(p.x, p.y);
        } else if (currentTool === 'bucket') {
            floodFill(layerCtx, p.x, p.y, getFgColor());
            pushHistoryEntry('Đổ màu');
        } else if (currentTool === 'line' || currentTool === 'rect' || currentTool === 'oval') {
            shapeStart = p;
            isDrawing = true;
        } else if (currentTool === 'text') {
            placeTextAt(p);
        }
    });

    inputLayer.addEventListener('mousemove', (e) => {
        if (isSelectTool(currentTool) && isSelecting) {
            const p = getPos(e);
            const rectCanvas = inputLayer.getBoundingClientRect();
            const scaleX = rectCanvas.width / CANVAS_W;
            const scaleY = rectCanvas.height / CANVAS_H;
            if (selectionShape === 'rect' || selectionShape === 'oval') {
                selectionRect = {
                    x: Math.min(selStart.x, p.x), y: Math.min(selStart.y, p.y),
                    w: Math.abs(p.x - selStart.x), h: Math.abs(p.y - selStart.y)
                };
                if (regionBox) {
                    regionBox.style.left = (selectionRect.x * scaleX) + 'px';
                    regionBox.style.top = (selectionRect.y * scaleY) + 'px';
                    regionBox.style.width = (selectionRect.w * scaleX) + 'px';
                    regionBox.style.height = (selectionRect.h * scaleY) + 'px';
                }
            } else if (selectionShape === 'free') {
                selectionPath.push(p);
                clearShapePreview();
                previewCtx.save();
                previewCtx.setLineDash([6, 4]);
                previewCtx.strokeStyle = '#3d8eff';
                previewCtx.lineWidth = 1.5;
                previewCtx.beginPath();
                previewCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
                for (let i = 1; i < selectionPath.length; i++) previewCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
                previewCtx.stroke();
                previewCtx.restore();
            }
            return;
        }
        if (!isDrawing) return;
        const p = getPos(e);
        const layerCtx = getActiveLayer().ctx;
        if (currentTool === 'pencil' || currentTool === 'brush' || currentTool === 'eraser') {
            layerCtx.lineWidth = getSize();
            layerCtx.lineCap = 'round';
            layerCtx.lineJoin = 'round';
            layerCtx.globalAlpha = currentTool === 'brush' ? getOpacity() * 0.6 : getOpacity();
            layerCtx.strokeStyle = currentTool === 'eraser' ? '#ffffff' : getFgColor();
            layerCtx.globalCompositeOperation = 'source-over';
            layerCtx.lineTo(p.x, p.y);
            layerCtx.stroke();
        } else if (currentTool === 'line' || currentTool === 'rect' || currentTool === 'oval') {
            redrawShapePreview(shapeStart, p);
        }
    });

    window.addEventListener('mouseup', (e) => {
        if (isSelectTool(currentTool) && isSelecting) {
            if (selectionShape === 'free' && selectionPath.length > 2) {
                clearShapePreview();
                previewCtx.save();
                previewCtx.setLineDash([6, 4]);
                previewCtx.strokeStyle = '#3d8eff';
                previewCtx.lineWidth = 1.5;
                previewCtx.beginPath();
                previewCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
                for (let i = 1; i < selectionPath.length; i++) previewCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
                previewCtx.closePath();
                previewCtx.stroke();
                previewCtx.restore();
                let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
                for (const pt of selectionPath) {
                    if (pt.x < minX) minX = pt.x; if (pt.y < minY) minY = pt.y;
                    if (pt.x > maxX) maxX = pt.x; if (pt.y > maxY) maxY = pt.y;
                }
                selectionRect = { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
            }
            const shapeLabels = { rect: 'rect', oval: 'oval', free: 'freehand' };
            if (selectionRect && selectionRect.w > 5) {
                const statusRegion = document.getElementById('statusRegion');
                if (statusRegion) statusRegion.textContent =
                    `Vùng AI (${shapeLabels[selectionShape]}): ${Math.round(selectionRect.w)}×${Math.round(selectionRect.h)}px`;
            }
            isSelecting = false;
            return;
        }
        if (!isDrawing) return;
        const layerCtx = getActiveLayer().ctx;
        if (currentTool === 'pencil' || currentTool === 'brush' || currentTool === 'eraser') {
            layerCtx.globalAlpha = 1;
            pushHistoryEntry(currentTool === 'eraser' ? 'Tẩy' : currentTool === 'brush' ? 'Vẽ cọ mềm' : 'Vẽ nét');
        } else if ((currentTool === 'line' || currentTool === 'rect' || currentTool === 'oval') && shapeStart) {
            const p = getPos(e);
            commitShape(shapeStart, p);
            clearShapePreview();
            pushHistoryEntry('Vẽ hình ' + currentTool);
            shapeStart = null;
        }
        isDrawing = false;
    });

    // ---- Toolbar ----
    document.querySelectorAll('.lt-btn[data-tool]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.lt-btn[data-tool]').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentTool = btn.dataset.tool;
            const labels = {
                pencil: 'Bút vẽ', brush: 'Cọ mềm', eraser: 'Tẩy', bucket: 'Đổ màu',
                line: 'Đường thẳng', rect: 'Hình chữ nhật', oval: 'Hình tròn/Oval',
                text: 'Chèn chữ', select: 'Chọn vùng',
                'select-oval': 'Chọn vùng (oval)', 'select-free': 'Chọn vùng (tự do)'
            };
            const statusTool = document.getElementById('statusTool');
            if (statusTool) statusTool.textContent = 'Công cụ: ' + (labels[currentTool] || currentTool);
            const isSelTool = isSelectTool(currentTool);
            inputLayer.style.cursor = isSelTool ? 'cell' : (currentTool === 'text' ? 'text' : 'crosshair');
            const isShape = ['line', 'rect', 'oval'].includes(currentTool);
            const shapeFillGroup = document.getElementById('shapeFillGroup');
            const textSizeGroup = document.getElementById('textSizeGroup');
            if (shapeFillGroup) shapeFillGroup.style.display = isShape ? 'flex' : 'none';
            if (textSizeGroup) textSizeGroup.style.display = currentTool === 'text' ? 'flex' : 'none';
        });
    });

    document.querySelectorAll('.shape-fill-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.shape-fill-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            shapeFillMode = btn.dataset.fillmode;
        });
    });

    document.getElementById('toolEyedrop')?.addEventListener('click', () => {
        const handler = (e) => {
            const p = getPos(e);
            const pixel = getActiveLayer().ctx.getImageData(p.x, p.y, 1, 1).data;
            const hex = '#' + [pixel[0], pixel[1], pixel[2]].map(v => v.toString(16).padStart(2, '0')).join('');
            const penColor = document.getElementById('penColor');
            if (penColor) penColor.value = hex;
            inputLayer.removeEventListener('click', handler);
        };
        inputLayer.addEventListener('click', handler);
    });

    document.querySelectorAll('.palette-swatch').forEach(sw => {
        sw.addEventListener('click', () => {
            const penColor = document.getElementById('penColor');
            if (penColor) penColor.value = sw.dataset.color;
        });
    });

    const penSizeEl = document.getElementById('penSize');
    penSizeEl?.addEventListener('input', () => {
        const el = document.getElementById('penSizeValue');
        if (el) el.textContent = penSizeEl.value;
    });
    const penOpacityEl = document.getElementById('penOpacity');
    penOpacityEl?.addEventListener('input', () => {
        const el = document.getElementById('penOpacityValue');
        if (el) el.textContent = penOpacityEl.value + '%';
    });
    const textSizeEl = document.getElementById('textSize');
    textSizeEl?.addEventListener('input', () => {
        const el = document.getElementById('textSizeValue');
        if (el) el.textContent = textSizeEl.value;
    });

    document.getElementById('toolUndo')?.addEventListener('click', () => {
        if (history.length <= 1) return;
        history.pop();
        const prev = history[history.length - 1];
        const layer = layers[prev.idx];
        const img = new Image();
        img.onload = () => {
            layer.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
            layer.ctx.drawImage(img, 0, 0);
        };
        img.src = prev.data;
        if (historyListEl?.lastChild) historyListEl.removeChild(historyListEl.lastChild);
    });

    document.getElementById('toolClear')?.addEventListener('click', () => {
        if (!confirm('Xóa toàn bộ nội dung của layer đang chọn?')) return;
        const layer = getActiveLayer();
        layer.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
        if (layer.isBase) { layer.ctx.fillStyle = '#ffffff'; layer.ctx.fillRect(0, 0, CANVAS_W, CANVAS_H); }
        pushHistoryEntry('Xóa layer "' + layer.name + '"');
    });

    document.getElementById('btnAddLayer')?.addEventListener('click', () => {
        addLayer('Layer ' + (layerCounter + 1), false);
        activeLayerIndex = layers.length - 1;
        renderLayerList();
        pushHistoryEntry('Thêm layer mới');
    });

    // ---- Zoom ----
    let zoomLevel = 1;
    function applyZoom() {
        canvasStack.style.transform = `scale(${zoomLevel})`;
        canvasStack.style.transformOrigin = 'center center';
        const zoomValue = document.getElementById('zoomValue');
        if (zoomValue) zoomValue.textContent = Math.round(zoomLevel * 100) + '%';
    }
    document.getElementById('zoomIn')?.addEventListener('click', () => { zoomLevel = Math.min(zoomLevel + 0.25, 3); applyZoom(); });
    document.getElementById('zoomOut')?.addEventListener('click', () => { zoomLevel = Math.max(zoomLevel - 0.25, 0.25); applyZoom(); });
    document.getElementById('zoomReset')?.addEventListener('click', () => { zoomLevel = 1; applyZoom(); });
    canvasViewport?.addEventListener('wheel', (e) => {
        if (!e.ctrlKey) return;
        e.preventDefault();
        zoomLevel = Math.min(Math.max(zoomLevel + (e.deltaY < 0 ? 0.1 : -0.1), 0.25), 3);
        applyZoom();
    });

    // ---- AI Modal ----
    const aiModalOverlay = document.getElementById('aiModalOverlay');
    document.getElementById('btnAiSupport')?.addEventListener('click', () => {
        showFeatureListView();
        if (aiModalOverlay) aiModalOverlay.style.display = 'flex';
    });
    document.getElementById('btnCloseAiModal')?.addEventListener('click', () => {
        if (aiModalOverlay) aiModalOverlay.style.display = 'none';
    });
    aiModalOverlay?.addEventListener('click', (e) => {
        if (e.target === aiModalOverlay) aiModalOverlay.style.display = 'none';
    });

    document.querySelectorAll('.ai-feature-card').forEach(card => {
        card.addEventListener('click', () => {
            selectedFeature = { code: card.dataset.feature, name: card.dataset.name, type: card.dataset.type };
            const aiDetailTitle = document.getElementById('aiDetailTitle');
            const aiPromptInput = document.getElementById('aiPromptInput');
            if (aiDetailTitle) aiDetailTitle.textContent = selectedFeature.name;
            if (aiPromptInput) aiPromptInput.value = '';
            const statusEl = document.getElementById('aiRegionStatus');
            if (selectionRect && selectionRect.w > 5) {
                regionMode = 'select';
                document.querySelectorAll('.ai-region-btn').forEach(b => b.classList.remove('active'));
                document.querySelector('.ai-region-btn[data-region="select"]')?.classList.add('active');
                if (statusEl) statusEl.textContent = `Sẽ áp dụng cho vùng đã chọn (${Math.round(selectionRect.w)}×${Math.round(selectionRect.h)}px)`;
            } else {
                regionMode = 'full';
                document.querySelectorAll('.ai-region-btn').forEach(b => b.classList.remove('active'));
                document.querySelector('.ai-region-btn[data-region="full"]')?.classList.add('active');
                if (statusEl) statusEl.textContent = 'Sẽ áp dụng cho toàn bộ canvas';
            }
            showAiDetailView();
        });
    });

    document.getElementById('btnBackToFeatureList')?.addEventListener('click', showFeatureListView);
    document.getElementById('btnAiResultBack')?.addEventListener('click', showFeatureListView);

    document.querySelectorAll('.ai-region-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.ai-region-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            regionMode = btn.dataset.region;
            const statusEl = document.getElementById('aiRegionStatus');
            if (!statusEl) return;
            if (regionMode === 'full') {
                statusEl.textContent = 'Sẽ áp dụng cho toàn bộ canvas';
            } else if (selectionRect && selectionRect.w > 5) {
                statusEl.textContent = `Sẽ áp dụng cho vùng đã chọn (${Math.round(selectionRect.w)}×${Math.round(selectionRect.h)}px)`;
            } else {
                statusEl.textContent = 'Chưa có vùng nào — đóng modal, chọn tool "Chọn vùng" rồi kéo trên canvas';
            }
        });
    });

    document.getElementById('btnRunAi')?.addEventListener('click', async () => {
        if (!selectedFeature) return;
        lastUsedRegion = {
            mode: regionMode,
            rect: regionMode === 'select' ? { ...selectionRect } : null,
            shape: selectionShape,
            path: (regionMode === 'select' && selectionShape === 'free') ? selectionPath.map(p => ({ ...p })) : null
        };
        const promptVal = document.getElementById('aiPromptInput')?.value || '';
        const imageBase64 = canvasRegionToBase64();
        const maskBase64 = generateMaskBase64();
        showAiLoadingView();
        try {
            const requestBody = { feature: selectedFeature.code, prompt: promptVal, imageBase64 };
            if (maskBase64) requestBody.maskBase64 = maskBase64;
            const response = await fetch('/api/ai/run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });
            const data = await response.json();
            renderAiResult(data);
        } catch (err) {
            renderAiResult({ status: 'error', message: 'Không thể kết nối tới server: ' + err.message });
        }
    });

    // ---- Lưu trang / Submission ----
    const btnSavePage = document.getElementById('btnSavePage');
    const pageId = btnSavePage?.dataset.pageId;
    if (btnSavePage) {
        btnSavePage.addEventListener('click', async () => {
            const base64 = flattenAllLayers().toDataURL('image/png');
            btnSavePage.disabled = true;
            btnSavePage.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang lưu...';
            try {
                const res = await fetch(`/api/page/${pageId}/savefile`, {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ imageBase64: base64 })
                });
                const data = await res.json();
                if (data.status === 'success') {
                    btnSavePage.innerHTML = '<i class="fa-solid fa-check"></i> Đã lưu!';
                    setTimeout(() => {
                        btnSavePage.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Lưu trang';
                        if (data.redirectUrl) window.location.href = data.redirectUrl;
                    }, 2000);
                } else {
                    alert('❌ ' + data.message);
                    btnSavePage.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Lưu trang';
                }
            } catch (err) {
                alert('❌ Lỗi: ' + err.message);
                btnSavePage.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Lưu trang';
            } finally {
                btnSavePage.disabled = false;
            }
        });
    }

    const btnSubmitSubmission = document.getElementById('btnSubmitSubmission');
    if (btnSubmitSubmission) {
        btnSubmitSubmission.addEventListener('click', async () => {
            const submissionId = btnSubmitSubmission.dataset.submissionId;
            const who = btnSubmitSubmission.dataset.who;
            const base64 = flattenAllLayers().toDataURL('image/png');
            btnSubmitSubmission.disabled = true;
            btnSubmitSubmission.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang nộp...';
            try {
                const res = await fetch(`/api/submission/${submissionId}/savefile`, {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ who, imageBase64: base64 })
                });
                const data = await res.json();
                if (data.status === 'success') {
                    btnSubmitSubmission.innerHTML = '<i class="fa-solid fa-check"></i> Đã lưu';
                    setTimeout(() => { if (data.redirectUrl) window.location.href = data.redirectUrl; }, 1000);
                } else {
                    alert('❌ ' + data.message);
                }
            } catch (err) {
                alert('❌ ' + err.message);
            } finally {
                btnSubmitSubmission.disabled = false;
            }
        });
    }

    const btnLoadPage = document.getElementById('btnLoadPage');
    const inputLoadPage = document.getElementById('inputLoadPage');
    if (btnLoadPage && inputLoadPage) {
        btnLoadPage.addEventListener('click', () => inputLoadPage.click());
        inputLoadPage.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (ev) => {
                const img = new Image();
                img.onload = () => {
                    layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
                    layers[1].ctx.drawImage(img, 0, 0);
                    pushHistoryEntry('Load file thủ công');
                };
                img.src = ev.target.result;
            };
            reader.readAsDataURL(file);
        });
    }

    // Render layer list lần đầu
    renderLayerList();

    // Load ảnh đã lưu
    const savedPath = btnSavePage?.dataset.savedPath;
    if (savedPath && savedPath !== 'null' && savedPath !== '') {
        const img = new Image();
        img.onload = () => {
            layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
            layers[1].ctx.drawImage(img, 0, 0);
            pushHistoryEntry('Load trang đã lưu');
        };
        img.src = savedPath;
    }

    const savedPath1 = btnSubmitSubmission?.dataset.savedPath;
    if (savedPath1 && savedPath1 !== 'null' && savedPath1 !== '') {
        const img = new Image();
        img.onload = () => {
            layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
            layers[1].ctx.drawImage(img, 0, 0);
            pushHistoryEntry('Load submission đã lưu');
        };
        img.src = savedPath1;
    }
});