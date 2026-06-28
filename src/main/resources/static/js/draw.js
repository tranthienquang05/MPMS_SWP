const CANVAS_W = 400, CANVAS_H = 600;
const canvasStack = document.getElementById('canvasStack');

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
    layerCounter++;
    const canvasEl = createLayerCanvas(layers.length + 1);
    canvasStack.insertBefore(canvasEl, document.getElementById('regionSelectBox'));
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

// Layer nền trắng (luôn có, không xoá được)
const baseLayer = addLayer('Nền (trắng)', true);
baseLayer.isBase = true;
// Layer vẽ chính, đang active
const mainLayer = addLayer('Layer 1', false);
activeLayerIndex = layers.length - 1;

function getActiveLayer() {
    return layers[activeLayerIndex];
}

function renderLayerList() {
    const listEl = document.getElementById('layerList');
    listEl.innerHTML = '';
    // hiện từ layer trên cùng (cuối array) xuống dưới
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

document.getElementById('btnAddLayer').addEventListener('click', () => {
    const newLayer = addLayer('Layer ' + (layerCounter + 1), false);
    activeLayerIndex = layers.length - 1;
    renderLayerList();
    pushHistoryEntry('Thêm layer mới');
});

// ========================================================
// PHẦN 2: Vẽ trên layer đang active (vẽ trực tiếp vào canvas của layer đó)
// ========================================================
// Để đơn giản hoá việc bắt sự kiện chuột, ta dùng 1 lớp "input layer" trong suốt
// nằm trên cùng để nhận event, rồi vẽ vào canvas của activeLayer.
const inputLayer = document.getElementById('drawCanvas');
inputLayer.style.zIndex = 999;
inputLayer.style.pointerEvents = 'auto';

let isDrawing = false;
let currentTool = 'pencil';
let history = [];
let shapeStart = null;
let shapeFillMode = 'outline';

const historyListEl = document.getElementById('historyList');

function snapshotAllLayers() {
    // Lưu trạng thái toàn bộ layer hiện có (đơn giản hoá: lưu canvas của activeLayer)
    return { idx: activeLayerIndex, data: getActiveLayer().canvas.toDataURL() };
}

function pushHistoryEntry(label) {
    history.push(snapshotAllLayers());
    if (history.length > 30) history.shift();
    const item = document.createElement('div');
    item.className = 'history-item current';
    item.textContent = label;
    Array.from(historyListEl.children).forEach(c => c.classList.remove('current'));
    historyListEl.appendChild(item);
    historyListEl.scrollTop = historyListEl.scrollHeight;
}
pushHistoryEntry('Tạo canvas mới');

function getPos(e) {
    const rect = inputLayer.getBoundingClientRect();
    const src = e.touches ? e.touches[0] : e;
    return {
        x: (src.clientX - rect.left) * (CANVAS_W / rect.width),
        y: (src.clientY - rect.top) * (CANVAS_H / rect.height)
    };
}

function getFgColor() {
    return document.getElementById('penColor').value;
}

function getOpacity() {
    return document.getElementById('penOpacity').value / 100;
}

function getSize() {
    return document.getElementById('penSize').value;
}

// ---- Bucket fill (flood fill) ----
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

// ========================================================
// TEXT TOOL — Canva-style (fixed + enhanced)
// ========================================================
let activeTextBox = null;

const FONTS = ['Arial', 'Georgia', 'Courier New', 'Impact', 'Comic Sans MS', 'Trebuchet MS'];

function createTextBox(x, y) {
    if (activeTextBox) commitTextBox();

    const viewport = document.getElementById('canvasViewport');
    const canvasRect = document.getElementById('canvasStack').getBoundingClientRect();
    const viewportRect = viewport.getBoundingClientRect();

    const scaleX = canvasRect.width / CANVAS_W;
    const scaleY = canvasRect.height / CANVAS_H;

    const screenX = canvasRect.left - viewportRect.left + x * scaleX;
    const screenY = canvasRect.top - viewportRect.top + y * scaleY;

    const initFontSize = parseInt(document.getElementById('textSize').value) * scaleX;
    const color = getFgColor();

    // ---- Wrapper ----
    const box = document.createElement('div');
    box.style.cssText = `
        position: absolute;
        left: ${screenX}px;
        top: ${screenY}px;
        width: 200px;
        min-height: 40px;
        border: 2px solid #3d8eff;
        border-radius: 2px;
        cursor: move;
        z-index: 1000;
        box-sizing: border-box;
        background: transparent;
    `;

    // ---- Toolbar (font, bold, italic, align, delete) ----
    const toolbar = document.createElement('div');
    toolbar.style.cssText = `
        position: absolute;
        top: -38px;
        left: 0;
        display: flex;
        align-items: center;
        gap: 4px;
        background: #1e1e1e;
        border-radius: 6px;
        padding: 4px 8px;
        white-space: nowrap;
        z-index: 20;
        box-shadow: 0 2px 8px rgba(0,0,0,0.4);
        pointer-events: auto;
    `;

    // Font selector
    const fontSelect = document.createElement('select');
    fontSelect.style.cssText = `
        background:#2a2a2a; color:#fff; border:1px solid #555;
        border-radius:4px; font-size:11px; padding:2px 4px; cursor:pointer;
    `;
    FONTS.forEach(f => {
        const opt = document.createElement('option');
        opt.value = f;
        opt.textContent = f;
        opt.style.fontFamily = f;
        fontSelect.appendChild(opt);
    });

    // Bold button
    const btnBold = makeToolbarBtn('<b>B</b>', 'font-weight:bold;');
    let isBold = false;
    btnBold.addEventListener('mousedown', e => {
        e.preventDefault(); e.stopPropagation();
        isBold = !isBold;
        ta.style.fontWeight = isBold ? 'bold' : 'normal';
        btnBold.style.background = isBold ? '#3d8eff' : '#2a2a2a';
    });

    // Italic button
    const btnItalic = makeToolbarBtn('<i>I</i>', 'font-style:italic;');
    let isItalic = false;
    btnItalic.addEventListener('mousedown', e => {
        e.preventDefault(); e.stopPropagation();
        isItalic = !isItalic;
        ta.style.fontStyle = isItalic ? 'italic' : 'normal';
        btnItalic.style.background = isItalic ? '#3d8eff' : '#2a2a2a';
    });

    // Align buttons
    let textAlign = 'left';
    const alignBtns = ['left', 'center', 'right'].map(align => {
        const icons = { left: '≡', center: '☰', right: '≣' };
        const btn = makeToolbarBtn(icons[align]);
        btn.title = align;
        btn.addEventListener('mousedown', e => {
            e.preventDefault(); e.stopPropagation();
            textAlign = align;
            ta.style.textAlign = align;
            alignBtns.forEach(b => b.style.background = '#2a2a2a');
            btn.style.background = '#3d8eff';
        });
        if (align === 'left') btn.style.background = '#3d8eff';
        return btn;
    });

    // Delete button
    const btnDelete = makeToolbarBtn('✕', 'color:#ff5c5c;');
    btnDelete.addEventListener('mousedown', e => {
        e.preventDefault(); e.stopPropagation();
        box.remove();
        activeTextBox = null;
        document.removeEventListener('mousedown', onOutsideClick);
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
    });

    toolbar.appendChild(fontSelect);
    toolbar.appendChild(btnBold);
    toolbar.appendChild(btnItalic);
    alignBtns.forEach(b => toolbar.appendChild(b));
    toolbar.appendChild(btnDelete);
    box.appendChild(toolbar);

    // ---- Textarea ----
    const ta = document.createElement('textarea');
    ta.style.cssText = `
        width: 100%;
        min-height: 36px;
        background: transparent;
        border: none;
        outline: none;
        resize: none;
        font-size: ${initFontSize}px;
        color: ${color};
        font-family: Arial, sans-serif;
        cursor: text;
        overflow: hidden;
        line-height: 1.2;
        padding: 4px;
        box-sizing: border-box;
        text-align: left;
    `;
    ta.placeholder = 'Nhập chữ...';

    // Sync font family
    fontSelect.addEventListener('change', () => {
        ta.style.fontFamily = fontSelect.value;
    });

    // Auto height
    ta.addEventListener('input', () => {
        ta.style.height = 'auto';
        ta.style.height = ta.scrollHeight + 'px';
        box.style.height = 'auto';
    });

    // ---- 8 resize handles ----
    const handlePositions = [
        { id: 'nw', style: 'top:-6px;left:-6px;cursor:nw-resize;' },
        { id: 'n', style: 'top:-6px;left:50%;transform:translateX(-50%);cursor:n-resize;' },
        { id: 'ne', style: 'top:-6px;right:-6px;cursor:ne-resize;' },
        { id: 'e', style: 'top:50%;right:-6px;transform:translateY(-50%);cursor:e-resize;' },
        { id: 'se', style: 'bottom:-6px;right:-6px;cursor:se-resize;' },
        { id: 's', style: 'bottom:-6px;left:50%;transform:translateX(-50%);cursor:s-resize;' },
        { id: 'sw', style: 'bottom:-6px;left:-6px;cursor:sw-resize;' },
        { id: 'w', style: 'top:50%;left:-6px;transform:translateY(-50%);cursor:w-resize;' },
    ];

    handlePositions.forEach(h => {
        const handle = document.createElement('div');
        handle.dataset.handle = h.id;
        handle.style.cssText = `
            position:absolute;width:12px;height:12px;
            background:#fff;border:2px solid #3d8eff;
            border-radius:2px;z-index:10;${h.style}
        `;
        box.appendChild(handle);
    });

    // ---- Rotate handle ----
    const rotateHandle = document.createElement('div');
    rotateHandle.style.cssText = `
        position:absolute;bottom:-28px;left:50%;
        transform:translateX(-50%);
        width:16px;height:16px;
        background:#3d8eff;border-radius:50%;
        cursor:grab;z-index:10;
        display:flex;align-items:center;justify-content:center;
        color:white;font-size:10px;user-select:none;
    `;
    rotateHandle.innerHTML = '↻';
    box.appendChild(rotateHandle);

    box.appendChild(ta);
    viewport.style.position = 'relative';
    viewport.appendChild(box);
    ta.focus();

    let rotation = 0;
    activeTextBox = {
        box, ta, fontSelect, scaleX, scaleY, rotation,
        originX: x, originY: y,
        isBold: () => isBold, isItalic: () => isItalic, getAlign: () => textAlign
    };

    // ---- Drag move ----
    let dragging = false, dragStartX, dragStartY, boxStartX, boxStartY;

    box.addEventListener('mousedown', (e) => {
        if (e.target.dataset.handle || e.target === rotateHandle || e.target === ta || toolbar.contains(e.target)) return;
        dragging = true;
        dragStartX = e.clientX;
        dragStartY = e.clientY;
        boxStartX = parseInt(box.style.left);
        boxStartY = parseInt(box.style.top);
        e.preventDefault();
    });

    // ---- Resize ----
    let resizing = false, activeHandle = null;
    let resizeStartX, resizeStartY, resizeStartW, resizeStartH, resizeStartL, resizeStartT, resizeStartFontSize;

    box.querySelectorAll('[data-handle]').forEach(h => {
        h.addEventListener('mousedown', (e) => {
            resizing = true;
            activeHandle = h.dataset.handle;
            resizeStartX = e.clientX;
            resizeStartY = e.clientY;
            resizeStartW = box.offsetWidth;
            resizeStartH = box.offsetHeight;
            resizeStartL = parseInt(box.style.left);
            resizeStartT = parseInt(box.style.top);
            resizeStartFontSize = parseFloat(ta.style.fontSize);
            e.preventDefault();
            e.stopPropagation();
        });
    });

    // ---- Rotate ----
    let rotating = false;
    rotateHandle.addEventListener('mousedown', (e) => {
        rotating = true;
        e.preventDefault();
        e.stopPropagation();
    });

    function onMouseMove(e) {
        if (dragging) {
            box.style.left = (boxStartX + e.clientX - dragStartX) + 'px';
            box.style.top = (boxStartY + e.clientY - dragStartY) + 'px';
        }

        if (resizing) {
            const dx = e.clientX - resizeStartX;
            const dy = e.clientY - resizeStartY;

            if (activeHandle.includes('e')) {
                box.style.width = Math.max(80, resizeStartW + dx) + 'px';
            }
            if (activeHandle.includes('w')) {
                const newW = Math.max(80, resizeStartW - dx);
                box.style.width = newW + 'px';
                box.style.left = (resizeStartL + resizeStartW - newW) + 'px';
            }
            // Scale font proportionally for corner handles
            if (activeHandle === 'se' || activeHandle === 'sw') {
                const scale = Math.max(0.1, (resizeStartH + dy) / resizeStartH);
                ta.style.fontSize = Math.max(8, resizeStartFontSize * scale) + 'px';
            }
            if (activeHandle === 'ne' || activeHandle === 'nw') {
                const newH = Math.max(40, resizeStartH - dy);
                box.style.top = (resizeStartT + resizeStartH - newH) + 'px';
                const scale = Math.max(0.1, newH / resizeStartH);
                ta.style.fontSize = Math.max(8, resizeStartFontSize * scale) + 'px';
            }
            if (activeHandle === 'n') {
                const newH = Math.max(40, resizeStartH - dy);
                box.style.top = (resizeStartT + resizeStartH - newH) + 'px';
            }
            if (activeHandle === 's') {
                // no-op height — auto height from content
            }
        }

        if (rotating) {
            const rect = box.getBoundingClientRect();
            const cx = rect.left + rect.width / 2;
            const cy = rect.top + rect.height / 2;
            const angle = Math.atan2(e.clientY - cy, e.clientX - cx) * 180 / Math.PI + 90;
            rotation = angle;
            activeTextBox.rotation = angle;
            box.style.transform = `rotate(${angle}deg)`;
        }
    }

    function onMouseUp() {
        dragging = false;
        resizing = false;
        rotating = false;
    }

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);

    // ---- Click ngoài → commit ----
    setTimeout(() => {
        document.addEventListener('mousedown', onOutsideClick);
    }, 100);

    function onOutsideClick(e) {
        if (!box.contains(e.target)) {
            commitTextBox();
            document.removeEventListener('mousedown', onOutsideClick);
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
        }
    }
}

// ---- Helper: tạo toolbar button ----
function makeToolbarBtn(html, extraStyle = '') {
    const btn = document.createElement('button');
    btn.innerHTML = html;
    btn.style.cssText = `
        background:#2a2a2a; color:#fff; border:1px solid #555;
        border-radius:4px; font-size:12px; padding:2px 7px;
        cursor:pointer; min-width:24px; line-height:1.4;
        ${extraStyle}
    `;
    return btn;
}

// ---- Commit: vẽ text lên canvas ----
function commitTextBox() {
    if (!activeTextBox) return;
    const { box, ta, fontSelect, scaleX, scaleY, rotation, isBold, isItalic, getAlign } = activeTextBox;
    const text = ta.value.trim();

    if (text) {
        const viewport = document.getElementById('canvasViewport');
        const canvasEl = document.getElementById('canvasStack');
        const canvasRect = canvasEl.getBoundingClientRect();
        const viewportRect = viewport.getBoundingClientRect();

        const boxLeft = parseInt(box.style.left);
        const boxTop = parseInt(box.style.top);
        const boxW = box.offsetWidth;

        // box.style.left/top relative to viewport; canvasStack offset cũng tính từ viewport
        const canvasOffX = canvasRect.left - viewportRect.left;
        const canvasOffY = canvasRect.top - viewportRect.top;
        const canvasX = (boxLeft - canvasOffX) / scaleX;
        const canvasY = (boxTop - canvasOffY) / scaleY;
        const fontSize = parseFloat(ta.style.fontSize) / scaleX;
        const canvasBoxW = boxW / scaleX;
        const fontFamily = fontSelect ? fontSelect.value : 'Arial';
        const fontWeight = isBold() ? 'bold' : 'normal';
        const fontStyle = isItalic() ? 'italic' : 'normal';
        const align = getAlign();

        const layerCtx = getActiveLayer().ctx;
        layerCtx.save();

        if (rotation !== 0) {
            const cx = canvasX + canvasBoxW / 2;
            const cy = canvasY + fontSize / 2;
            layerCtx.translate(cx, cy);
            layerCtx.rotate(rotation * Math.PI / 180);
            layerCtx.translate(-cx, -cy);
        }

        layerCtx.globalAlpha = getOpacity();
        layerCtx.fillStyle = ta.style.color;
        layerCtx.font = `${fontStyle} ${fontWeight} ${fontSize}px ${fontFamily}`;
        layerCtx.textBaseline = 'top';
        layerCtx.textAlign = 'left'; // luôn dùng left, tự tính x bên dưới

        const padding = 4;
        const maxW = canvasBoxW - padding * 2;

        // Tính lines trước (word-wrap)
        const allLines = [];
        text.split('\n').forEach(paragraph => {
            if (paragraph.trim() === '') { allLines.push(''); return; }
            const words = paragraph.split(' ');
            let current = '';
            words.forEach(word => {
                const test = current ? current + ' ' + word : word;
                if (layerCtx.measureText(test).width > maxW && current) {
                    allLines.push(current);
                    current = word;
                } else {
                    current = test;
                }
            });
            if (current) allLines.push(current);
        });

        // Vẽ từng dòng với x tính đúng theo align
        let lineY = canvasY + padding;
        allLines.forEach(line => {
            let drawX;
            if (align === 'center') {
                const lineW = layerCtx.measureText(line).width;
                drawX = canvasX + (canvasBoxW - lineW) / 2;
            } else if (align === 'right') {
                const lineW = layerCtx.measureText(line).width;
                drawX = canvasX + canvasBoxW - lineW - padding;
            } else {
                drawX = canvasX + padding;
            }
            layerCtx.fillText(line, drawX, lineY);
            lineY += fontSize * 1.2;
        });

        layerCtx.restore(); // ← FIX: restore canvas state
    }

    box.remove();
    activeTextBox = null;
}

function placeTextAt(pos) {
    createTextBox(pos.x, pos.y);
}

// ---- Mouse events chính ----
inputLayer.addEventListener('mousedown', (e) => {
    if (currentTool === 'select' || currentTool === 'select-oval' || currentTool === 'select-free') return;
    const p = getPos(e);
    const layerCtx = getActiveLayer().ctx;

    if (currentTool === 'pencil' || currentTool === 'brush' || currentTool === 'eraser') {
        isDrawing = true;
        layerCtx.beginPath();
        layerCtx.moveTo(p.x, p.y);
    } else if (currentTool === 'bucket') {
        floodFill(layerCtx, p.x, p.y, currentTool === 'eraser' ? '#ffffff' : getFgColor());
        pushHistoryEntry('Đổ màu');
    } else if (currentTool === 'line' || currentTool === 'rect' || currentTool === 'oval') {
        shapeStart = p;
        isDrawing = true;
    } else if (currentTool === 'text') {
        placeTextAt(p);
    }
});

inputLayer.addEventListener('mousemove', (e) => {
    if (!isDrawing) return;
    const p = getPos(e);
    const layerCtx = getActiveLayer().ctx;

    if (currentTool === 'pencil' || currentTool === 'brush' || currentTool === 'eraser') {
        layerCtx.lineWidth = getSize();
        layerCtx.lineCap = currentTool === 'brush' ? 'round' : 'round';
        layerCtx.lineJoin = 'round';
        layerCtx.globalAlpha = currentTool === 'brush' ? getOpacity() * 0.6 : getOpacity();
        layerCtx.strokeStyle = currentTool === 'eraser' ? '#ffffff' : getFgColor();
        if (currentTool === 'eraser') {
            // Tẩy: vẽ lại màu trắng đè lên (đơn giản hoá, không dùng destination-out
            // để layer nền trắng không bị ảnh hưởng khi xoá layer trên)
            layerCtx.globalCompositeOperation = 'source-over';
        }
        layerCtx.lineTo(p.x, p.y);
        layerCtx.stroke();
    } else if (currentTool === 'line' || currentTool === 'rect' || currentTool === 'oval') {
        // Vẽ shape preview lên input layer (canvas tạm phía trên), xoá sau khi xong
        redrawShapePreview(shapeStart, p);
    }
});

window.addEventListener('mouseup', (e) => {
    if (!isDrawing) return;
    const layerCtx = getActiveLayer().ctx;

    if (currentTool === 'pencil' || currentTool === 'brush' || currentTool === 'eraser') {
        layerCtx.globalAlpha = 1;
        pushHistoryEntry(currentTool === 'eraser' ? 'Tẩy' : (currentTool === 'brush' ? 'Vẽ cọ mềm' : 'Vẽ nét'));
    } else if ((currentTool === 'line' || currentTool === 'rect' || currentTool === 'oval') && shapeStart) {
        const p = getPos(e);
        commitShape(shapeStart, p);
        clearShapePreview();
        pushHistoryEntry('Vẽ hình ' + currentTool);
        shapeStart = null;
    }
    isDrawing = false;
});

// ---- Shape preview (vẽ tạm lên input layer rồi commit vào layer thật) ----
const previewCtx = inputLayer.getContext('2d');

function clearShapePreview() {
    previewCtx.clearRect(0, 0, CANVAS_W, CANVAS_H);
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
    const layerCtx = getActiveLayer().ctx;
    drawShapeOnContext(layerCtx, start, end);
}

// ========================================================
// PHẦN 3: Toolbar — chọn tool, fill mode, eyedropper
// ========================================================
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
        document.getElementById('statusTool').textContent = 'Công cụ: ' + labels[currentTool];
        const isSelTool = currentTool === 'select' || currentTool === 'select-oval' || currentTool === 'select-free';
        const darkCrosshair = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 20 20'%3E%3Cline x1='10' y1='0' x2='10' y2='20' stroke='black' stroke-width='1.5'/%3E%3Cline x1='0' y1='10' x2='20' y2='10' stroke='black' stroke-width='1.5'/%3E%3C/svg%3E\") 10 10, crosshair";
        const textCursor = "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='24' viewBox='0 0 20 24'%3E%3Ctext x='10' y='18' text-anchor='middle' font-size='20' fill='black'%3EI%3C/text%3E%3C/svg%3E\") 10 12, text";
        inputLayer.style.cursor = isSelTool ? 'cell' : (currentTool === 'text' ? 'textCursor' : darkCrosshair);

        // Hiện/ẩn property phù hợp
        const isShape = ['line', 'rect', 'oval'].includes(currentTool);
        document.getElementById('shapeFillGroup').style.display = isShape ? 'flex' : 'none';
        document.getElementById('textSizeGroup').style.display = currentTool === 'text' ? 'flex' : 'none';
    });
});

document.querySelectorAll('.shape-fill-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.shape-fill-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        shapeFillMode = btn.dataset.fillmode;
    });
});

document.getElementById('toolEyedrop').addEventListener('click', () => {
    const handler = (e) => {
        const p = getPos(e);
        const layerCtx = getActiveLayer().ctx;
        const pixel = layerCtx.getImageData(p.x, p.y, 1, 1).data;
        const hex = '#' + [pixel[0], pixel[1], pixel[2]].map(v => v.toString(16).padStart(2, '0')).join('');
        document.getElementById('penColor').value = hex;
        inputLayer.removeEventListener('click', handler);
    };
    inputLayer.addEventListener('click', handler);
});

// Palette nhanh
document.querySelectorAll('.palette-swatch').forEach(sw => {
    sw.addEventListener('click', () => {
        document.getElementById('penColor').value = sw.dataset.color;
    });
});

// Slider hiển thị giá trị
const penSizeEl = document.getElementById('penSize');
penSizeEl.addEventListener('input', () => document.getElementById('penSizeValue').textContent = penSizeEl.value);
const penOpacityEl = document.getElementById('penOpacity');
penOpacityEl.addEventListener('input', () => document.getElementById('penOpacityValue').textContent = penOpacityEl.value + '%');
const textSizeEl = document.getElementById('textSize');
textSizeEl.addEventListener('input', () => document.getElementById('textSizeValue').textContent = textSizeEl.value);

// Undo / Clear
document.getElementById('toolUndo').addEventListener('click', () => {
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
    if (historyListEl.lastChild) historyListEl.removeChild(historyListEl.lastChild);
});

document.getElementById('toolClear').addEventListener('click', () => {
    if (!confirm('Xóa toàn bộ nội dung của layer đang chọn?')) return;
    const layer = getActiveLayer();
    layer.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
    if (layer.isBase) {
        layer.ctx.fillStyle = '#ffffff';
        layer.ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
    }
    pushHistoryEntry('Xóa layer "' + layer.name + '"');
});

// ========================================================
// PHẦN 4: Region selection
// ========================================================
let selectionRect = null;
let selectionShape = 'rect'; // 'rect' | 'oval' | 'free'
let selectionPath = [];      // array of {x, y} for freehand selection
let isSelecting = false;
let selStart = null;
const regionBox = document.getElementById('regionSelectBox');
const canvasViewport = document.getElementById('canvasViewport');

function isSelectTool(tool) {
    return tool === 'select' || tool === 'select-oval' || tool === 'select-free';
}

inputLayer.addEventListener('mousedown', (e) => {
    if (!isSelectTool(currentTool)) return;
    isSelecting = true;
    selStart = getPos(e);
    clearShapePreview(); // clear any previous freehand preview

    if (currentTool === 'select') {
        selectionShape = 'rect';
        regionBox.style.borderRadius = '0';
        regionBox.style.display = 'block';
    } else if (currentTool === 'select-oval') {
        selectionShape = 'oval';
        regionBox.style.borderRadius = '50%';
        regionBox.style.display = 'block';
    } else if (currentTool === 'select-free') {
        selectionShape = 'free';
        selectionPath = [selStart];
        regionBox.style.display = 'none';
    }
});

inputLayer.addEventListener('mousemove', (e) => {
    if (!isSelectTool(currentTool) || !isSelecting) return;
    const p = getPos(e);
    const rectCanvas = inputLayer.getBoundingClientRect();
    const scaleX = rectCanvas.width / CANVAS_W;
    const scaleY = rectCanvas.height / CANVAS_H;

    if (selectionShape === 'rect' || selectionShape === 'oval') {
        selectionRect = {
            x: Math.min(selStart.x, p.x),
            y: Math.min(selStart.y, p.y),
            w: Math.abs(p.x - selStart.x),
            h: Math.abs(p.y - selStart.y)
        };

        regionBox.style.left = (selectionRect.x * scaleX) + 'px';
        regionBox.style.top = (selectionRect.y * scaleY) + 'px';
        regionBox.style.width = (selectionRect.w * scaleX) + 'px';
        regionBox.style.height = (selectionRect.h * scaleY) + 'px';
    } else if (selectionShape === 'free') {
        selectionPath.push(p);
        // Draw freehand selection preview on input layer
        clearShapePreview();
        previewCtx.save();
        previewCtx.setLineDash([6, 4]);
        previewCtx.strokeStyle = getComputedStyle(document.documentElement)
            .getPropertyValue('--ps-accent').trim() || '#3d8eff';
        previewCtx.lineWidth = 1.5;
        previewCtx.beginPath();
        previewCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
        for (let i = 1; i < selectionPath.length; i++) {
            previewCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
        }
        previewCtx.stroke();
        previewCtx.restore();
    }
});

window.addEventListener('mouseup', () => {
    if (!isSelectTool(currentTool) || !isSelecting) {
        isSelecting = false;
        return;
    }

    if (selectionShape === 'free' && selectionPath.length > 2) {
        // Close the freehand path visually
        clearShapePreview();
        previewCtx.save();
        previewCtx.setLineDash([6, 4]);
        previewCtx.strokeStyle = getComputedStyle(document.documentElement)
            .getPropertyValue('--ps-accent').trim() || '#3d8eff';
        previewCtx.lineWidth = 1.5;
        previewCtx.beginPath();
        previewCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
        for (let i = 1; i < selectionPath.length; i++) {
            previewCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
        }
        previewCtx.closePath();
        previewCtx.stroke();
        previewCtx.restore();

        // Compute bounding box from selectionPath
        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        for (let i = 0; i < selectionPath.length; i++) {
            const pt = selectionPath[i];
            if (pt.x < minX) minX = pt.x;
            if (pt.y < minY) minY = pt.y;
            if (pt.x > maxX) maxX = pt.x;
            if (pt.y > maxY) maxY = pt.y;
        }
        selectionRect = { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
    }

    const shapeLabels = { rect: 'rect', oval: 'oval', free: 'freehand' };
    if (selectionRect && selectionRect.w > 5) {
        document.getElementById('statusRegion').textContent =
            `Vùng AI (${shapeLabels[selectionShape]}): ${Math.round(selectionRect.w)}×${Math.round(selectionRect.h)}px`;
    }
    isSelecting = false;
});

// ========================================================
// PHẦN 5: Zoom canvas (CSS transform scale đơn giản)
// ========================================================
let zoomLevel = 1;

function applyZoom() {
    canvasStack.style.transform = `scale(${zoomLevel})`;
    canvasStack.style.transformOrigin = 'center center';
    document.getElementById('zoomValue').textContent = Math.round(zoomLevel * 100) + '%';
}

document.getElementById('zoomIn').addEventListener('click', () => {
    zoomLevel = Math.min(zoomLevel + 0.25, 3);
    applyZoom();
});
document.getElementById('zoomOut').addEventListener('click', () => {
    zoomLevel = Math.max(zoomLevel - 0.25, 0.25);
    applyZoom();
});
document.getElementById('zoomReset').addEventListener('click', () => {
    zoomLevel = 1;
    applyZoom();
});
canvasViewport.addEventListener('wheel', (e) => {
    if (!e.ctrlKey) return;
    e.preventDefault();
    zoomLevel = Math.min(Math.max(zoomLevel + (e.deltaY < 0 ? 0.1 : -0.1), 0.25), 3);
    applyZoom();
});

// ========================================================
// PHẦN 6: Modal AI Support
// ========================================================
const aiModalOverlay = document.getElementById('aiModalOverlay');
const aiFeatureListView = document.getElementById('aiFeatureListView');
const aiDetailView = document.getElementById('aiDetailView');
const aiLoadingView = document.getElementById('aiLoadingView');
const aiResultView = document.getElementById('aiResultView');

let selectedFeature = null;
let regionMode = 'full';
let lastUsedRegion = null;

function openAiModal() { aiModalOverlay.style.display = 'flex'; }
function closeAiModal() { aiModalOverlay.style.display = 'none'; }

function showFeatureListView() {
    aiFeatureListView.style.display = 'block';
    aiDetailView.style.display = 'none';
    aiLoadingView.style.display = 'none';
    aiResultView.style.display = 'none';
}
function showAiDetailView() {
    aiFeatureListView.style.display = 'none';
    aiDetailView.style.display = 'block';
    aiLoadingView.style.display = 'none';
    aiResultView.style.display = 'none';
}
function showAiLoadingView() {
    aiFeatureListView.style.display = 'none';
    aiDetailView.style.display = 'none';
    aiLoadingView.style.display = 'block';
    aiResultView.style.display = 'none';
}
function showAiResultView() {
    aiFeatureListView.style.display = 'none';
    aiDetailView.style.display = 'none';
    aiLoadingView.style.display = 'none';
    aiResultView.style.display = 'block';
}

document.getElementById('btnAiSupport').addEventListener('click', () => {
    showFeatureListView();
    openAiModal();
});
document.getElementById('btnCloseAiModal').addEventListener('click', closeAiModal);
aiModalOverlay.addEventListener('click', (e) => { if (e.target === aiModalOverlay) closeAiModal(); });

document.querySelectorAll('.ai-feature-card').forEach(card => {
    card.addEventListener('click', () => {
        selectedFeature = {
            code: card.dataset.feature,
            name: card.dataset.name,
            type: card.dataset.type
        };
        document.getElementById('aiDetailTitle').textContent = selectedFeature.name;
        document.getElementById('aiPromptInput').value = '';

        // BUG 1 FIX: auto-detect existing selection
        const statusEl = document.getElementById('aiRegionStatus');
        if (selectionRect && selectionRect.w > 5) {
            regionMode = 'select';
            document.querySelectorAll('.ai-region-btn').forEach(b => b.classList.remove('active'));
            document.querySelector('.ai-region-btn[data-region="select"]').classList.add('active');
            statusEl.textContent = `Sẽ áp dụng cho vùng đã chọn (${Math.round(selectionRect.w)}×${Math.round(selectionRect.h)}px)`;
        } else {
            regionMode = 'full';
            document.querySelectorAll('.ai-region-btn').forEach(b => b.classList.remove('active'));
            document.querySelector('.ai-region-btn[data-region="full"]').classList.add('active');
            statusEl.textContent = 'Sẽ áp dụng cho toàn bộ canvas';
        }

        showAiDetailView();
    });
});

document.getElementById('btnBackToFeatureList').addEventListener('click', showFeatureListView);
document.getElementById('btnAiResultBack').addEventListener('click', showFeatureListView);

document.querySelectorAll('.ai-region-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.ai-region-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        regionMode = btn.dataset.region;
        const statusEl = document.getElementById('aiRegionStatus');
        if (regionMode === 'full') {
            statusEl.textContent = 'Sẽ áp dụng cho toàn bộ canvas';
        } else if (selectionRect && selectionRect.w > 5) {
            statusEl.textContent = `Sẽ áp dụng cho vùng đã chọn (${Math.round(selectionRect.w)}×${Math.round(selectionRect.h)}px)`;
        } else {
            statusEl.textContent = 'Chưa có vùng nào — đóng modal, chọn tool "Chọn vùng" rồi kéo trên canvas';
        }
    });
});

// ========================================================
// PHẦN 7: Gộp tất cả layer thành 1 ảnh rồi gọi /api/ai/run
// ========================================================
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
    const flat = flattenAllLayers();
    // Always send full canvas — mask will tell AI which area to edit
    return flat.toDataURL('image/png').split(',')[1];
}

/**
 * Generate a mask PNG for OpenAI /images/edits.
 * Transparent pixels (alpha=0) = area AI should edit.
 * Opaque white pixels = area to keep unchanged.
 * Returns base64 string (no data: prefix), or null if no selection.
 */
function generateMaskBase64() {
    if (regionMode !== 'select' || !selectionRect || selectionRect.w <= 5) {
        return null;
    }

    const mask = document.createElement('canvas');
    mask.width = CANVAS_W;
    mask.height = CANVAS_H;
    const mCtx = mask.getContext('2d');

    // Fill entire canvas with opaque white (keep area)
    mCtx.fillStyle = '#ffffff';
    mCtx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    // Cut out the selection region (make it transparent = edit area)
    mCtx.globalCompositeOperation = 'destination-out';
    mCtx.fillStyle = '#000000';

    if (selectionShape === 'oval') {
        const cx = selectionRect.x + selectionRect.w / 2;
        const cy = selectionRect.y + selectionRect.h / 2;
        const rx = selectionRect.w / 2;
        const ry = selectionRect.h / 2;
        mCtx.beginPath();
        mCtx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
        mCtx.fill();
    } else if (selectionShape === 'free' && selectionPath.length > 2) {
        mCtx.beginPath();
        mCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
        for (let i = 1; i < selectionPath.length; i++) {
            mCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
        }
        mCtx.closePath();
        mCtx.fill();
    } else {
        // Rectangle selection
        mCtx.fillRect(selectionRect.x, selectionRect.y, selectionRect.w, selectionRect.h);
    }

    mCtx.globalCompositeOperation = 'source-over';
    return mask.toDataURL('image/png').split(',')[1];
}

document.getElementById('btnRunAi').addEventListener('click', async () => {
    if (!selectedFeature) return;

    // Store the region info at the moment "Run AI" is clicked
    lastUsedRegion = {
        mode: regionMode,
        rect: regionMode === 'select' ? { ...selectionRect } : null,
        shape: selectionShape,
        path: (regionMode === 'select' && selectionShape === 'free')
            ? selectionPath.map(p => ({ ...p }))
            : null
    };

    const promptVal = document.getElementById('aiPromptInput').value;
    const imageBase64 = canvasRegionToBase64();
    const maskBase64 = generateMaskBase64();

    showAiLoadingView();

    try {
        const requestBody = {
            feature: selectedFeature.code,
            prompt: promptVal,
            imageBase64: imageBase64
        };
        // Only include mask when user selected a region
        if (maskBase64) {
            requestBody.maskBase64 = maskBase64;
        }

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

// BUG 2 FIX: draw AI result image back onto the canvas at the correct position
function applyAiResultToCanvas(base64) {
    const img = new Image();
    img.onload = () => {
        const layerCtx = getActiveLayer().ctx;

        if (lastUsedRegion && lastUsedRegion.mode === 'select' && lastUsedRegion.rect) {
            const r = lastUsedRegion.rect;
            layerCtx.save();
            layerCtx.beginPath();

            if (lastUsedRegion.shape === 'oval') {
                const cx = r.x + r.w / 2;
                const cy = r.y + r.h / 2;
                layerCtx.ellipse(cx, cy, r.w / 2, r.h / 2, 0, 0, Math.PI * 2);
            } else if (lastUsedRegion.shape === 'free' && lastUsedRegion.path && lastUsedRegion.path.length > 2) {
                layerCtx.moveTo(lastUsedRegion.path[0].x, lastUsedRegion.path[0].y);
                for (let i = 1; i < lastUsedRegion.path.length; i++) {
                    layerCtx.lineTo(lastUsedRegion.path[i].x, lastUsedRegion.path[i].y);
                }
                layerCtx.closePath();
            } else {
                // rect: bounding box chính là vùng chọn, không cần clip thêm
                layerCtx.rect(r.x, r.y, r.w, r.h);
            }

            layerCtx.clip();
            layerCtx.drawImage(img, r.x, r.y, r.w, r.h);
            layerCtx.restore();
        } else {
            layerCtx.drawImage(img, 0, 0, CANVAS_W, CANVAS_H);
        }

        pushHistoryEntry('AI: ' + (selectedFeature ? selectedFeature.name : ''));
        closeAiModal();
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
            imgEl.src = data.type === 'image_base64'
                ? 'data:image/png;base64,' + data.result
                : data.result;
            imgEl.style.display = 'block';

            // BUG 2 FIX: show "Apply to canvas" button for image results
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
// Lưu trang
// ========================================================
// PHẦN 8: Lưu trang & Submission
// ========================================================
const btnSavePage = document.getElementById('btnSavePage');
const pageId = btnSavePage?.dataset.pageId;

// Lưu trang
if (btnSavePage) {
    btnSavePage.addEventListener('click', async () => {
        const base64 = flattenAllLayers().toDataURL('image/png');

        btnSavePage.disabled = true;
        btnSavePage.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang lưu...';

        try {
            const res = await fetch(`/api/page/${pageId}/savefile`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ imageBase64: base64 })
            });
            const data = await res.json();
            if (data.status === 'success') {
                btnSavePage.innerHTML = '<i class="fa-solid fa-check"></i> Đã lưu!';
                setTimeout(() => {
                    btnSavePage.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Lưu trang';
                    // Thêm dòng này để xử lý redirectUrl trả về từ backend
                    if (data.redirectUrl) {
                        window.location.href = data.redirectUrl;
                    }
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
const btnSubmitSubmission =
    document.getElementById('btnSubmitSubmission');





if (btnSubmitSubmission) {

    btnSubmitSubmission.addEventListener('click', async () => {

        const submissionId =
            btnSubmitSubmission.dataset.submissionId;

        const base64 =
            flattenAllLayers().toDataURL('image/png');

        btnSubmitSubmission.disabled = true;

        btnSubmitSubmission.innerHTML =
            '<i class="fa-solid fa-spinner fa-spin"></i> Đang nộp...';

        try {

            const res = await fetch(
                `/api/submission/${submissionId}/savefile`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        imageBase64: base64,

                    })
                }
            );

            const data = await res.json();

            if (data.status === 'success') {

                btnSubmitSubmission.innerHTML =
                    '<i class="fa-solid fa-check"></i> Đã nộp';

                setTimeout(() => {

                    if (data.redirectUrl) {
                        window.location.href =
                            data.redirectUrl;
                    }

                }, 1000);

            } else {

                alert('❌ ' + data.message);

                btnSubmitSubmission.innerHTML =
                    '<i class="fa-solid fa-paper-plane"></i> Nộp bài';
            }

        } catch (err) {

            alert('❌ ' + err.message);

            btnSubmitSubmission.innerHTML =
                '<i class="fa-solid fa-paper-plane"></i> Nộp bài';

        } finally {

            btnSubmitSubmission.disabled = false;
        }
    });
}





// Đặt toàn bộ vào một khối IIFE độc lập để tránh xung đột biến hệ thống
(() => {
    const btnToolbar = document.getElementById('btnEditSubmission');
    const btnModalConfirm = document.getElementById('btnConfirmEdit');
    const modalForm = document.getElementById('editSubmissionModal');

    // HÀNH ĐỘNG 1: Nhấn nút ở ngoài thanh công cụ -> CHỈ HIỆN MODAL, không tự ý gửi dữ liệu
    if (btnToolbar) {
        btnToolbar.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation(); // Ngăn chặn bong bóng sự kiện kích hoạt các hàm xử lý khác

            if (modalForm) {
                modalForm.style.display = 'flex'; // Hiện form lên cho người dùng nhập thông tin
            } else {
                alert("Không tìm thấy cấu trúc Modal 'editSubmissionModal' trên HTML!");
            }
        });
    }

    // HÀNH ĐỘNG 2: Chỉ khi người dùng điền xong và nhấn "Xác nhận Lưu" trong Modal -> Mới bắt đầu gom data gửi đi
    if (btnModalConfirm && btnToolbar) {
        btnModalConfirm.addEventListener('click', async (e) => {
            e.preventDefault();

            // Lấy dữ liệu ID từ dataset của nút bấm ngoài toolbar
            const submissionId = btnToolbar.dataset.submissionId;
            if (!submissionId) {
                alert("Không tìm thấy mã số bài nộp (submissionId)!");
                return;
            }

            // Thu thập dữ liệu từ các input/select nằm bên trong cấu trúc Modal
            const statusElement = document.getElementById('subStatus');
            const commentElement = document.getElementById('subComment');

            const statusValue = statusElement ? statusElement.value : '';
            const commentValue = commentElement ? commentElement.value : '';

            // Gộp các layer nét vẽ thành một chuỗi hình ảnh Base64 duy nhất
            const base64 = flattenAllLayers().toDataURL('image/png');

            // Tạo trạng thái Loading đóng băng nút bấm tránh việc người dùng nhấn liên tục (Double Click)
            btnModalConfirm.disabled = true;
            btnModalConfirm.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang cập nhật...';

            try {
                // Gửi request dữ liệu bằng cấu trúc AJAX (fetch async/await) lên API backend
                const res = await fetch(`/api/submission/${submissionId}/edit`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        imageBase64: base64,
                        status: statusValue,
                        comment: commentValue,
                        who: "mangaka" // Gửi kèm thông tin định danh vai trò để Backend điều hướng link chính xác
                    })
                });

                const data = await res.json();

                // Xử lý phản hồi trả về từ máy chủ sau khi lưu file vật lý thành công
                if (data.status === 'success') {
                    btnModalConfirm.innerHTML = '<i class="fa-solid fa-check"></i> Đã cập nhật';

                    // Ẩn modal ngay sau khi hệ thống xử lý hoàn tất thành công
                    if (modalForm) modalForm.style.display = 'none';

                    // Chờ 1 giây tạo hiệu ứng thị giác mượt mà rồi tiến hành điều hướng trang (Redirect)
                    setTimeout(() => {
                        if (data.redirectUrl) {
                            window.location.href = data.redirectUrl;
                        } else {
                            window.location.href = '/manga/mangaka';
                        }
                    }, 1000);

                } else {
                    alert('❌ Lỗi xử lý: ' + data.message);
                    btnModalConfirm.innerHTML = 'Xác nhận Lưu';
                    btnModalConfirm.disabled = false;
                }

            } catch (err) {
                alert('❌ Lỗi đường truyền hệ thống: ' + err.message);
                btnModalConfirm.innerHTML = 'Xác nhận Lưu';
                btnModalConfirm.disabled = false;
            }
        });
    }
})();

// Hàm hỗ trợ đóng modal nhanh khi người dùng bấm nút Hủy
function closeEditSubmissionModal() {
    const modal = document.getElementById('editSubmissionModal');
    if (modal) modal.style.display = 'none';
}

// --- LOGIC XỬ LÝ ĐỌC FILE LÊN KHÔNG GIAN VẼ THỦ CÔNG ---
const btnLoadPage = document.getElementById('btnLoadPage');
const inputLoadPage = document.getElementById('inputLoadPage');

if (btnLoadPage && inputLoadPage) {
    btnLoadPage.addEventListener('click', () => {
        inputLoadPage.click(); // Kích hoạt sự kiện click giả lập vào thẻ input file ẩn
    });

    inputLoadPage.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = (ev) => {
            const img = new Image();
            img.onload = () => {
                // Xóa vùng canvas cũ trên layer chỉ định và vẽ đè file mới tải lên lên
                layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
                layers[1].ctx.drawImage(img, 0, 0);
                pushHistoryEntry('Load file thủ công');
            };
            img.src = ev.target.result;
        };
        reader.readAsDataURL(file);
    });
}
// Khởi tạo layer list lần đầu
renderLayerList();
// Load ảnh đã lưu lên canvas khi mở trang edit
const savedPath = btnSavePage ? btnSavePage.dataset.savedPath : null;
if (savedPath && savedPath !== 'null' && savedPath !== '') {
    const img = new Image();
    img.onload = () => {
        layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
        layers[1].ctx.drawImage(img, 0, 0);
        pushHistoryEntry('Load trang đã lưu');
    };
    img.src = savedPath; // VD: /MangaPage/MGP001.png
}


const savedPath1 = btnSubmitSubmission?.dataset.savedPath;

// giống hệt page save
if (savedPath1 && savedPath1 !== 'null' && savedPath1 !== '') {

    const img = new Image();

    img.onload = () => {

        layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
        layers[1].ctx.drawImage(img, 0, 0);

        pushHistoryEntry('Load submission đã lưu');
    };

    img.src = savedPath1;
}
const savedPath2 = document.getElementById('btnEditSubmission')?.dataset.savedPath;

// giống hệt page save
if (savedPath2 && savedPath2 !== 'null' && savedPath2 !== '') {

    const img = new Image();

    img.onload = () => {

        layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
        layers[1].ctx.drawImage(img, 0, 0);

        pushHistoryEntry('Load submission đã lưu');
    };

    img.src = savedPath2;
}

// ========================================
// DOWNLOAD MỌI Layer thành 1 file ảnh
// ========================================
document.getElementById('btnDownload')?.addEventListener('click', () => {
    // Gộp tất cả layer thành 1 ảnh
    const flatCanvas = flattenAllLayers();

    // Tạo link download tự động
    const link = document.createElement('a');
    link.download = 'manga-page-' + Date.now() + '.png';
    link.href = flatCanvas.toDataURL('image/png');
    link.click();
});