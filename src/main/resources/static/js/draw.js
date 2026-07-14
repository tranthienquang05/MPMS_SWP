const CANVAS_W = 400,
  CANVAS_H = 600;
const canvasStack = document.getElementById("canvasStack");

function normalizeDrawCanvasStack() {
  if (!canvasStack) return;
  const input = document.getElementById("drawCanvas");
  const region = document.getElementById("regionSelectBox");
  canvasStack.querySelectorAll("canvas").forEach((canvas) => {
    if (canvas !== input) canvas.remove();
  });
  if (input && region && input.nextElementSibling !== region) {
    canvasStack.insertBefore(input, region);
  }
}

normalizeDrawCanvasStack();

function updateRangeProgress(range) {
  if (!range) return;
  const min = Number(range.min || 0);
  const max = Number(range.max || 100);
  const value = Number(range.value);
  const progress = max === min ? 0 : ((value - min) / (max - min)) * 100;
  range.style.setProperty("--range-progress", progress + "%");
}

function bindRangeNumber(rangeId, numberId) {
  const range = document.getElementById(rangeId);
  const number = document.getElementById(numberId);
  if (!range || !number) return;

  const min = Number(range.min);
  const max = Number(range.max);
  const step = Number(range.step || 1);

  const applyValue = (rawValue, restoreEmpty) => {
    if (rawValue === "" && !restoreEmpty) return;
    const parsed = Number(rawValue);
    const fallback = Number(range.value);
    const safeValue = Number.isFinite(parsed) ? parsed : fallback;
    const clamped = Math.min(max, Math.max(min, safeValue));
    const stepped = Math.round(clamped / step) * step;
    range.value = stepped;
    number.value = stepped;
    updateRangeProgress(range);
  };

  range.addEventListener("input", () => {
    number.value = range.value;
    updateRangeProgress(range);
  });
  number.addEventListener("input", () => {
    if (number.value === "") return;
    const parsed = Number(number.value);
    if (!Number.isFinite(parsed) || parsed < min || parsed > max) return;
    range.value = parsed;
    updateRangeProgress(range);
  });
  number.addEventListener("change", () => applyValue(number.value, true));
  updateRangeProgress(range);
}

// Cấu trúc layer: { id, name, canvas, ctx, visible, opacity }
let layers = [];
let activeLayerIndex = 0;
let layerCounter = 0;

function createLayerCanvas(zIndex) {
  const c = document.createElement("canvas");
  c.width = CANVAS_W;
  c.height = CANVAS_H;
  c.style.position = "absolute";
  c.style.top = "0";
  c.style.left = "0";
  c.style.zIndex = zIndex;
  c.style.pointerEvents = "none";
  return c;
}

function addLayer(name, isBackground) {
  layerCounter++;
  const canvasEl = createLayerCanvas(layers.length + 1);
  canvasStack.insertBefore(
    canvasEl,
    document.getElementById("regionSelectBox"),
  );
  const ctxL = canvasEl.getContext("2d");
  if (isBackground) {
    ctxL.fillStyle = "#ffffff";
    ctxL.fillRect(0, 0, CANVAS_W, CANVAS_H);
  }
  const layer = {
    id: layerCounter,
    name: name || "Layer " + layerCounter,
    canvas: canvasEl,
    ctx: ctxL,
    visible: true,
    opacity: 100,
  };
  layers.push(layer);
  renderLayerList();
  return layer;
}

// Layer nền trắng (luôn có, không xoá được)
const baseLayer = addLayer("Nền (trắng)", true);
baseLayer.isBase = true;
// Layer vẽ chính, đang active
const mainLayer = addLayer("Layer 1", false);
activeLayerIndex = layers.length - 1;

function getActiveLayer() {
  return layers[activeLayerIndex];
}

function renderLayerList() {
  const listEl = document.getElementById("layerList");
  listEl.innerHTML = "";
  // hiện từ layer trên cùng (cuối array) xuống dưới
  for (let i = layers.length - 1; i >= 0; i--) {
    const layer = layers[i];
    const item = document.createElement("div");
    item.className = "layer-item" + (i === activeLayerIndex ? " selected" : "");
    item.innerHTML = `
                    <button type="button" class="layer-toggle-visible ${layer.visible ? "" : "hidden-layer"}" data-idx="${i}">
                        <i class="fa-solid ${layer.visible ? "fa-eye" : "fa-eye-slash"}"></i>
                    </button>
                    <div class="layer-thumb"></div>
                    <span class="layer-name">${layer.name}</span>
                `;
    item.addEventListener("click", (e) => {
      if (e.target.closest(".layer-toggle-visible")) return;
      activeLayerIndex = i;
      renderLayerList();
    });
    listEl.appendChild(item);

    if (i === activeLayerIndex) {
      const opacityRow = document.createElement("div");
      opacityRow.className = "layer-opacity-row";
      opacityRow.innerHTML = `
                        <span style="font-size:10px;color:var(--ps-text-dim);">Opacity</span>
                        <input type="range" min="0" max="100" value="${layer.opacity}" data-idx="${i}" class="layer-opacity-slider">
                        <span>${layer.opacity}%</span>
                    `;
      listEl.appendChild(opacityRow);
    }
  }

  listEl.querySelectorAll(".layer-toggle-visible").forEach((btn) => {
    btn.addEventListener("click", () => {
      const idx = parseInt(btn.dataset.idx);
      layers[idx].visible = !layers[idx].visible;
      layers[idx].canvas.style.display = layers[idx].visible ? "block" : "none";
      renderLayerList();
    });
  });

  listEl.querySelectorAll(".layer-opacity-slider").forEach((slider) => {
    updateRangeProgress(slider);
    slider.addEventListener("input", () => {
      const idx = parseInt(slider.dataset.idx);
      layers[idx].opacity = slider.value;
      layers[idx].canvas.style.opacity = slider.value / 100;
      slider.nextElementSibling.textContent = slider.value + "%";
      updateRangeProgress(slider);
    });
  });
}

document.getElementById("btnAddLayer").addEventListener("click", () => {
  const newLayer = addLayer("Layer " + (layerCounter + 1), false);
  activeLayerIndex = layers.length - 1;
  renderLayerList();
  pushHistoryEntry("Thêm layer mới");
});

// ========================================================
// PHẦN 2: Vẽ trên layer đang active (vẽ trực tiếp vào canvas của layer đó)
// ========================================================
// Để đơn giản hoá việc bắt sự kiện chuột, ta dùng 1 lớp "input layer" trong suốt
// nằm trên cùng để nhận event, rồi vẽ vào canvas của activeLayer.
const inputLayer = document.getElementById("drawCanvas");
inputLayer.style.zIndex = 999;
inputLayer.style.pointerEvents = "auto";

let isDrawing = false;
let currentTool = "pencil";
let history = [];
let shapeStart = null;
let shapeFillMode = "outline";

const historyListEl =
  document.getElementById("historyList") || document.createElement("div");

function decorateDrawIconButtons() {
  const icons = {
    pencil: "fa-pen",
    brush: "fa-paintbrush",
    eraser: "fa-eraser",
    bucket: "fa-fill-drip",
    line: "fa-minus",
    rect: "fa-square",
    oval: "fa-circle",
    text: "fa-font",
    select: "fa-vector-square",
    "select-oval": "fa-circle-dot",
    "select-free": "fa-crop-simple",
    toolEyedrop: "fa-eye-dropper",
    toolUndo: "fa-rotate-left",
    toolClear: "fa-trash",
    btnAddLayer: "fa-plus",
    zoomOut: "fa-magnifying-glass-minus",
    zoomIn: "fa-magnifying-glass-plus",
  };

  document.querySelectorAll(".lt-btn[data-tool]").forEach((button) => {
    if (!button.querySelector("i")) {
      button.innerHTML =
        '<i class="fa-solid ' +
        (icons[button.dataset.tool] || "fa-circle") +
        '"></i>';
    }
  });

  Object.entries(icons).forEach(([id, icon]) => {
    const button = document.getElementById(id);
    if (button && !button.querySelector("i") && !button.textContent.trim()) {
      button.innerHTML = '<i class="fa-solid ' + icon + '"></i>';
    }
  });
}

decorateDrawIconButtons();

function snapshotAllLayers() {
  // Lưu trạng thái toàn bộ layer hiện có (đơn giản hoá: lưu canvas của activeLayer)
  return { idx: activeLayerIndex, data: getActiveLayer().canvas.toDataURL() };
}

function pushHistoryEntry(label) {
  history.push(snapshotAllLayers());
  if (history.length > 30) history.shift();
  const item = document.createElement("div");
  item.className = "history-item current";
  item.textContent = label;
  if (historyListEl.parentNode) {
    Array.from(historyListEl.children).forEach((c) =>
      c.classList.remove("current"),
    );
    historyListEl.appendChild(item);
    historyListEl.scrollTop = historyListEl.scrollHeight;
  }
}
pushHistoryEntry("Tạo canvas mới");

function getPos(e) {
  const rect = inputLayer.getBoundingClientRect();
  const src = e.touches ? e.touches[0] : e;
  return {
    x: (src.clientX - rect.left) * (CANVAS_W / rect.width),
    y: (src.clientY - rect.top) * (CANVAS_H / rect.height),
  };
}

function getFgColor() {
  return document.getElementById("penColor").value;
}

function getOpacity() {
  return document.getElementById("penOpacity").value / 100;
}

function getSize() {
  return document.getElementById("penSize").value;
}

// ---- Bucket fill (flood fill) ----
function floodFill(ctxTarget, startX, startY, fillColorHex) {
  startX = Math.floor(startX);
  startY = Math.floor(startY);
  const imgData = ctxTarget.getImageData(0, 0, CANVAS_W, CANVAS_H);
  const data = imgData.data;

  const fillColor = hexToRgba(fillColorHex);
  const startIdx = (startY * CANVAS_W + startX) * 4;
  const targetColor = [
    data[startIdx],
    data[startIdx + 1],
    data[startIdx + 2],
    data[startIdx + 3],
  ];

  if (colorsMatch(targetColor, fillColor)) return;

  const stack = [[startX, startY]];
  const visited = new Uint8Array(CANVAS_W * CANVAS_H);

  while (stack.length) {
    const [x, y] = stack.pop();
    if (x < 0 || x >= CANVAS_W || y < 0 || y >= CANVAS_H) continue;
    const pos = y * CANVAS_W + x;
    if (visited[pos]) continue;

    const idx = pos * 4;
    const currentColor = [
      data[idx],
      data[idx + 1],
      data[idx + 2],
      data[idx + 3],
    ];
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
  return (
    Math.abs(a[0] - b[0]) < 12 &&
    Math.abs(a[1] - b[1]) < 12 &&
    Math.abs(a[2] - b[2]) < 12 &&
    Math.abs(a[3] - b[3]) < 12
  );
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

const FONTS = [
  "Arial",
  "Georgia",
  "Courier New",
  "Impact",
  "Comic Sans MS",
  "Trebuchet MS",
];

function createTextBox(x, y) {
  if (activeTextBox) commitTextBox();

  const viewport = document.getElementById("canvasViewport");
  const canvasRect = document
    .getElementById("canvasStack")
    .getBoundingClientRect();
  const viewportRect = viewport.getBoundingClientRect();

  const scaleX = canvasRect.width / CANVAS_W;
  const scaleY = canvasRect.height / CANVAS_H;

  const screenX = canvasRect.left - viewportRect.left + x * scaleX;
  const screenY = canvasRect.top - viewportRect.top + y * scaleY;

  const initFontSize =
    parseInt(document.getElementById("textSize").value) * scaleX;
  const color = getFgColor();

  // ---- Wrapper ----
  const box = document.createElement("div");
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
  const toolbar = document.createElement("div");
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
  const fontSelect = document.createElement("select");
  fontSelect.style.cssText = `
        background:#2a2a2a; color:#fff; border:1px solid #555;
        border-radius:4px; font-size:11px; padding:2px 4px; cursor:pointer;
    `;
  FONTS.forEach((f) => {
    const opt = document.createElement("option");
    opt.value = f;
    opt.textContent = f;
    opt.style.fontFamily = f;
    fontSelect.appendChild(opt);
  });

  // Bold button
  const btnBold = makeToolbarBtn("<b>B</b>", "font-weight:bold;");
  let isBold = false;
  btnBold.addEventListener("mousedown", (e) => {
    e.preventDefault();
    e.stopPropagation();
    isBold = !isBold;
    ta.style.fontWeight = isBold ? "bold" : "normal";
    btnBold.style.background = isBold ? "#3d8eff" : "#2a2a2a";
  });

  // Italic button
  const btnItalic = makeToolbarBtn("<i>I</i>", "font-style:italic;");
  let isItalic = false;
  btnItalic.addEventListener("mousedown", (e) => {
    e.preventDefault();
    e.stopPropagation();
    isItalic = !isItalic;
    ta.style.fontStyle = isItalic ? "italic" : "normal";
    btnItalic.style.background = isItalic ? "#3d8eff" : "#2a2a2a";
  });

  // Align buttons
  let textAlign = "left";
  const alignBtns = ["left", "center", "right"].map((align) => {
    const icons = { left: "≡", center: "☰", right: "≣" };
    const btn = makeToolbarBtn(icons[align]);
    btn.title = align;
    btn.addEventListener("mousedown", (e) => {
      e.preventDefault();
      e.stopPropagation();
      textAlign = align;
      ta.style.textAlign = align;
      alignBtns.forEach((b) => (b.style.background = "#2a2a2a"));
      btn.style.background = "#3d8eff";
    });
    if (align === "left") btn.style.background = "#3d8eff";
    return btn;
  });

  // Delete button
  const btnDelete = makeToolbarBtn("✕", "color:#ff5c5c;");
  btnDelete.addEventListener("mousedown", (e) => {
    e.preventDefault();
    e.stopPropagation();
    box.remove();
    activeTextBox = null;
    document.removeEventListener("mousedown", onOutsideClick);
    document.removeEventListener("mousemove", onMouseMove);
    document.removeEventListener("mouseup", onMouseUp);
  });

  toolbar.appendChild(fontSelect);
  toolbar.appendChild(btnBold);
  toolbar.appendChild(btnItalic);
  alignBtns.forEach((b) => toolbar.appendChild(b));
  toolbar.appendChild(btnDelete);
  box.appendChild(toolbar);

  // ---- Textarea ----
  const ta = document.createElement("textarea");
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
  ta.placeholder = "Nhập chữ...";

  // Sync font family
  fontSelect.addEventListener("change", () => {
    ta.style.fontFamily = fontSelect.value;
  });

  // Auto height
  ta.addEventListener("input", () => {
    ta.style.height = "auto";
    ta.style.height = ta.scrollHeight + "px";
    box.style.height = "auto";
  });

  // ---- 8 resize handles ----
  const handlePositions = [
    { id: "nw", style: "top:-6px;left:-6px;cursor:nw-resize;" },
    {
      id: "n",
      style: "top:-6px;left:50%;transform:translateX(-50%);cursor:n-resize;",
    },
    { id: "ne", style: "top:-6px;right:-6px;cursor:ne-resize;" },
    {
      id: "e",
      style: "top:50%;right:-6px;transform:translateY(-50%);cursor:e-resize;",
    },
    { id: "se", style: "bottom:-6px;right:-6px;cursor:se-resize;" },
    {
      id: "s",
      style: "bottom:-6px;left:50%;transform:translateX(-50%);cursor:s-resize;",
    },
    { id: "sw", style: "bottom:-6px;left:-6px;cursor:sw-resize;" },
    {
      id: "w",
      style: "top:50%;left:-6px;transform:translateY(-50%);cursor:w-resize;",
    },
  ];

  handlePositions.forEach((h) => {
    const handle = document.createElement("div");
    handle.dataset.handle = h.id;
    handle.style.cssText = `
            position:absolute;width:12px;height:12px;
            background:#fff;border:2px solid #3d8eff;
            border-radius:2px;z-index:10;${h.style}
        `;
    box.appendChild(handle);
  });

  // ---- Rotate handle ----
  const rotateHandle = document.createElement("div");
  rotateHandle.style.cssText = `
        position:absolute;bottom:-28px;left:50%;
        transform:translateX(-50%);
        width:16px;height:16px;
        background:#3d8eff;border-radius:50%;
        cursor:grab;z-index:10;
        display:flex;align-items:center;justify-content:center;
        color:white;font-size:10px;user-select:none;
    `;
  rotateHandle.innerHTML = "↻";
  box.appendChild(rotateHandle);

  box.appendChild(ta);
  viewport.style.position = "relative";
  viewport.appendChild(box);
  ta.focus();

  let rotation = 0;
  activeTextBox = {
    box,
    ta,
    fontSelect,
    scaleX,
    scaleY,
    rotation,
    originX: x,
    originY: y,
    isBold: () => isBold,
    isItalic: () => isItalic,
    getAlign: () => textAlign,
  };

  // ---- Drag move ----
  let dragging = false,
    dragStartX,
    dragStartY,
    boxStartX,
    boxStartY;

  box.addEventListener("mousedown", (e) => {
    if (
      e.target.dataset.handle ||
      e.target === rotateHandle ||
      e.target === ta ||
      toolbar.contains(e.target)
    )
      return;
    dragging = true;
    dragStartX = e.clientX;
    dragStartY = e.clientY;
    boxStartX = parseInt(box.style.left);
    boxStartY = parseInt(box.style.top);
    e.preventDefault();
  });

  // ---- Resize ----
  let resizing = false,
    activeHandle = null;
  let resizeStartX,
    resizeStartY,
    resizeStartW,
    resizeStartH,
    resizeStartL,
    resizeStartT,
    resizeStartFontSize;

  box.querySelectorAll("[data-handle]").forEach((h) => {
    h.addEventListener("mousedown", (e) => {
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
  rotateHandle.addEventListener("mousedown", (e) => {
    rotating = true;
    e.preventDefault();
    e.stopPropagation();
  });

  function onMouseMove(e) {
    if (dragging) {
      box.style.left = boxStartX + e.clientX - dragStartX + "px";
      box.style.top = boxStartY + e.clientY - dragStartY + "px";
    }

    if (resizing) {
      const dx = e.clientX - resizeStartX;
      const dy = e.clientY - resizeStartY;

      if (activeHandle.includes("e")) {
        box.style.width = Math.max(80, resizeStartW + dx) + "px";
      }
      if (activeHandle.includes("w")) {
        const newW = Math.max(80, resizeStartW - dx);
        box.style.width = newW + "px";
        box.style.left = resizeStartL + resizeStartW - newW + "px";
      }
      // Scale font proportionally for corner handles
      if (activeHandle === "se" || activeHandle === "sw") {
        const scale = Math.max(0.1, (resizeStartH + dy) / resizeStartH);
        ta.style.fontSize = Math.max(8, resizeStartFontSize * scale) + "px";
      }
      if (activeHandle === "ne" || activeHandle === "nw") {
        const newH = Math.max(40, resizeStartH - dy);
        box.style.top = resizeStartT + resizeStartH - newH + "px";
        const scale = Math.max(0.1, newH / resizeStartH);
        ta.style.fontSize = Math.max(8, resizeStartFontSize * scale) + "px";
      }
      if (activeHandle === "n") {
        const newH = Math.max(40, resizeStartH - dy);
        box.style.top = resizeStartT + resizeStartH - newH + "px";
      }
      if (activeHandle === "s") {
        // no-op height — auto height from content
      }
    }

    if (rotating) {
      const rect = box.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height / 2;
      const angle =
        (Math.atan2(e.clientY - cy, e.clientX - cx) * 180) / Math.PI + 90;
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

  document.addEventListener("mousemove", onMouseMove);
  document.addEventListener("mouseup", onMouseUp);

  // ---- Click ngoài → commit ----
  setTimeout(() => {
    document.addEventListener("mousedown", onOutsideClick);
  }, 100);

  function onOutsideClick(e) {
    if (!box.contains(e.target)) {
      commitTextBox();
      document.removeEventListener("mousedown", onOutsideClick);
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
    }
  }
}

// ---- Helper: tạo toolbar button ----
function makeToolbarBtn(html, extraStyle = "") {
  const btn = document.createElement("button");
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
  const {
    box,
    ta,
    fontSelect,
    scaleX,
    scaleY,
    rotation,
    isBold,
    isItalic,
    getAlign,
  } = activeTextBox;
  const text = ta.value.trim();

  if (text) {
    const viewport = document.getElementById("canvasViewport");
    const canvasEl = document.getElementById("canvasStack");
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
    const fontFamily = fontSelect ? fontSelect.value : "Arial";
    const fontWeight = isBold() ? "bold" : "normal";
    const fontStyle = isItalic() ? "italic" : "normal";
    const align = getAlign();

    const layerCtx = getActiveLayer().ctx;
    layerCtx.save();

    if (rotation !== 0) {
      const cx = canvasX + canvasBoxW / 2;
      const cy = canvasY + fontSize / 2;
      layerCtx.translate(cx, cy);
      layerCtx.rotate((rotation * Math.PI) / 180);
      layerCtx.translate(-cx, -cy);
    }

    layerCtx.globalAlpha = getOpacity();
    layerCtx.fillStyle = ta.style.color;
    layerCtx.font = `${fontStyle} ${fontWeight} ${fontSize}px ${fontFamily}`;
    layerCtx.textBaseline = "top";
    layerCtx.textAlign = "left"; // luôn dùng left, tự tính x bên dưới

    const padding = 4;
    const maxW = canvasBoxW - padding * 2;

    // Tính lines trước (word-wrap)
    const allLines = [];
    text.split("\n").forEach((paragraph) => {
      if (paragraph.trim() === "") {
        allLines.push("");
        return;
      }
      const words = paragraph.split(" ");
      let current = "";
      words.forEach((word) => {
        const test = current ? current + " " + word : word;
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
    allLines.forEach((line) => {
      let drawX;
      if (align === "center") {
        const lineW = layerCtx.measureText(line).width;
        drawX = canvasX + (canvasBoxW - lineW) / 2;
      } else if (align === "right") {
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
inputLayer.addEventListener("mousedown", (e) => {
  if (
    currentTool === "select" ||
    currentTool === "select-oval" ||
    currentTool === "select-free"
  )
    return;
  const p = getPos(e);
  const layerCtx = getActiveLayer().ctx;

  if (
    currentTool === "pencil" ||
    currentTool === "brush" ||
    currentTool === "eraser"
  ) {
    isDrawing = true;
    layerCtx.beginPath();
    layerCtx.moveTo(p.x, p.y);
  } else if (currentTool === "bucket") {
    floodFill(
      layerCtx,
      p.x,
      p.y,
      currentTool === "eraser" ? "#ffffff" : getFgColor(),
    );
    pushHistoryEntry("Đổ màu");
  } else if (
    currentTool === "line" ||
    currentTool === "rect" ||
    currentTool === "oval"
  ) {
    shapeStart = p;
    isDrawing = true;
  } else if (currentTool === "text") {
    placeTextAt(p);
  }
});

inputLayer.addEventListener("mousemove", (e) => {
  if (!isDrawing) return;
  const p = getPos(e);
  const layerCtx = getActiveLayer().ctx;

  if (
    currentTool === "pencil" ||
    currentTool === "brush" ||
    currentTool === "eraser"
  ) {
    layerCtx.lineWidth = getSize();
    layerCtx.lineCap = currentTool === "brush" ? "round" : "round";
    layerCtx.lineJoin = "round";
    layerCtx.globalAlpha =
      currentTool === "brush" ? getOpacity() * 0.6 : getOpacity();
    layerCtx.strokeStyle = currentTool === "eraser" ? "#ffffff" : getFgColor();
    if (currentTool === "eraser") {
      // Tẩy: vẽ lại màu trắng đè lên (đơn giản hoá, không dùng destination-out
      // để layer nền trắng không bị ảnh hưởng khi xoá layer trên)
      layerCtx.globalCompositeOperation = "source-over";
    }
    layerCtx.lineTo(p.x, p.y);
    layerCtx.stroke();
  } else if (
    currentTool === "line" ||
    currentTool === "rect" ||
    currentTool === "oval"
  ) {
    // Vẽ shape preview lên input layer (canvas tạm phía trên), xoá sau khi xong
    redrawShapePreview(shapeStart, p);
  }
});

window.addEventListener("mouseup", (e) => {
  if (!isDrawing) return;
  const layerCtx = getActiveLayer().ctx;

  if (
    currentTool === "pencil" ||
    currentTool === "brush" ||
    currentTool === "eraser"
  ) {
    layerCtx.globalAlpha = 1;
    pushHistoryEntry(
      currentTool === "eraser"
        ? "Tẩy"
        : currentTool === "brush"
          ? "Vẽ cọ mềm"
          : "Vẽ nét",
    );
  } else if (
    (currentTool === "line" ||
      currentTool === "rect" ||
      currentTool === "oval") &&
    shapeStart
  ) {
    const p = getPos(e);
    commitShape(shapeStart, p);
    clearShapePreview();
    pushHistoryEntry("Vẽ hình " + currentTool);
    shapeStart = null;
  }
  isDrawing = false;
});

// ---- Shape preview (vẽ tạm lên input layer rồi commit vào layer thật) ----
const previewCtx = inputLayer.getContext("2d");

function clearShapePreview() {
  previewCtx.clearRect(0, 0, CANVAS_W, CANVAS_H);
}

function drawShapeOnContext(ctxTarget, start, end) {
  ctxTarget.lineWidth = getSize();
  ctxTarget.strokeStyle = getFgColor();
  ctxTarget.fillStyle = getFgColor();
  ctxTarget.globalAlpha = getOpacity();
  ctxTarget.beginPath();

  if (currentTool === "line") {
    ctxTarget.moveTo(start.x, start.y);
    ctxTarget.lineTo(end.x, end.y);
    ctxTarget.stroke();
  } else if (currentTool === "rect") {
    const w = end.x - start.x,
      h = end.y - start.y;
    if (shapeFillMode === "filled") ctxTarget.fillRect(start.x, start.y, w, h);
    else ctxTarget.strokeRect(start.x, start.y, w, h);
  } else if (currentTool === "oval") {
    const cx = (start.x + end.x) / 2,
      cy = (start.y + end.y) / 2;
    const rx = Math.abs(end.x - start.x) / 2,
      ry = Math.abs(end.y - start.y) / 2;
    ctxTarget.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
    if (shapeFillMode === "filled") ctxTarget.fill();
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
document.querySelectorAll(".lt-btn[data-tool]").forEach((btn) => {
  btn.addEventListener("click", () => {
    document
      .querySelectorAll(".lt-btn[data-tool]")
      .forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    currentTool = btn.dataset.tool;

    const labels = {
      pencil: "Bút vẽ",
      brush: "Cọ mềm",
      eraser: "Tẩy",
      bucket: "Đổ màu",
      line: "Đường thẳng",
      rect: "Hình chữ nhật",
      oval: "Hình tròn/Oval",
      text: "Chèn chữ",
      select: "Chọn vùng",
      "select-oval": "Chọn vùng (oval)",
      "select-free": "Chọn vùng (tự do)",
    };
    document.getElementById("statusTool").textContent =
      "Công cụ: " + labels[currentTool];
    const isSelTool =
      currentTool === "select" ||
      currentTool === "select-oval" ||
      currentTool === "select-free";
    const darkCrosshair =
      "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 20 20'%3E%3Cline x1='10' y1='0' x2='10' y2='20' stroke='black' stroke-width='1.5'/%3E%3Cline x1='0' y1='10' x2='20' y2='10' stroke='black' stroke-width='1.5'/%3E%3C/svg%3E\") 10 10, crosshair";
    const textCursor =
      "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='24' viewBox='0 0 20 24'%3E%3Ctext x='10' y='18' text-anchor='middle' font-size='20' fill='black'%3EI%3C/text%3E%3C/svg%3E\") 10 12, text";
    inputLayer.style.cursor = isSelTool
      ? "cell"
      : currentTool === "text"
        ? textCursor
        : darkCrosshair;

    // Hiện/ẩn property phù hợp
    const isShape = ["line", "rect", "oval"].includes(currentTool);
    document.getElementById("shapeFillGroup").style.display = isShape
      ? "flex"
      : "none";
    document.getElementById("textSizeGroup").style.display =
      currentTool === "text" ? "flex" : "none";
  });
});

document.querySelectorAll(".shape-fill-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document
      .querySelectorAll(".shape-fill-btn")
      .forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    shapeFillMode = btn.dataset.fillmode;
  });
});

document.getElementById("toolEyedrop").addEventListener("click", () => {
  const handler = (e) => {
    const p = getPos(e);
    const layerCtx = getActiveLayer().ctx;
    const pixel = layerCtx.getImageData(p.x, p.y, 1, 1).data;
    const hex =
      "#" +
      [pixel[0], pixel[1], pixel[2]]
        .map((v) => v.toString(16).padStart(2, "0"))
        .join("");
    document.getElementById("penColor").value = hex;
    inputLayer.removeEventListener("click", handler);
  };
  inputLayer.addEventListener("click", handler);
});

// Palette nhanh
document.querySelectorAll(".palette-swatch").forEach((sw) => {
  sw.addEventListener("click", () => {
    document.getElementById("penColor").value = sw.dataset.color;
  });
});

// Đồng bộ hai chiều giữa thanh kéo và ô nhập số.
bindRangeNumber("penSize", "penSizeValue");
bindRangeNumber("penOpacity", "penOpacityValue");
bindRangeNumber("textSize", "textSizeValue");

// Undo / Clear
document.getElementById("toolUndo").addEventListener("click", () => {
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
  if (historyListEl.lastChild)
    historyListEl.removeChild(historyListEl.lastChild);
});

document.getElementById("toolClear").addEventListener("click", () => {
  if (!confirm("Xóa toàn bộ nội dung của layer đang chọn?")) return;
  const layer = getActiveLayer();
  layer.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
  if (layer.isBase) {
    layer.ctx.fillStyle = "#ffffff";
    layer.ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
  }
  pushHistoryEntry('Xóa layer "' + layer.name + '"');
});

// ========================================================
// PHẦN 4: Region selection
// ========================================================
let selectionRect = null;
let selectionShape = "rect"; // 'rect' | 'oval' | 'free'
let selectionPath = []; // array of {x, y} for freehand selection
let isSelecting = false;
let selStart = null;
const regionBox = document.getElementById("regionSelectBox");
const canvasViewport = document.getElementById("canvasViewport");

function isSelectTool(tool) {
  return tool === "select" || tool === "select-oval" || tool === "select-free";
}

inputLayer.addEventListener("mousedown", (e) => {
  if (!isSelectTool(currentTool)) return;
  isSelecting = true;
  selStart = getPos(e);
  clearShapePreview(); // clear any previous freehand preview

  if (currentTool === "select") {
    selectionShape = "rect";
    regionBox.style.borderRadius = "0";
    regionBox.style.display = "block";
  } else if (currentTool === "select-oval") {
    selectionShape = "oval";
    regionBox.style.borderRadius = "50%";
    regionBox.style.display = "block";
  } else if (currentTool === "select-free") {
    selectionShape = "free";
    selectionPath = [selStart];
    regionBox.style.display = "none";
  }
});

inputLayer.addEventListener("mousemove", (e) => {
  if (!isSelectTool(currentTool) || !isSelecting) return;
  const p = getPos(e);
  const rectCanvas = inputLayer.getBoundingClientRect();
  const scaleX = rectCanvas.width / CANVAS_W;
  const scaleY = rectCanvas.height / CANVAS_H;

  if (selectionShape === "rect" || selectionShape === "oval") {
    selectionRect = {
      x: Math.min(selStart.x, p.x),
      y: Math.min(selStart.y, p.y),
      w: Math.abs(p.x - selStart.x),
      h: Math.abs(p.y - selStart.y),
    };

    regionBox.style.left = selectionRect.x * scaleX + "px";
    regionBox.style.top = selectionRect.y * scaleY + "px";
    regionBox.style.width = selectionRect.w * scaleX + "px";
    regionBox.style.height = selectionRect.h * scaleY + "px";
  } else if (selectionShape === "free") {
    selectionPath.push(p);
    // Draw freehand selection preview on input layer
    clearShapePreview();
    previewCtx.save();
    previewCtx.setLineDash([6, 4]);
    previewCtx.strokeStyle =
      getComputedStyle(document.documentElement)
        .getPropertyValue("--ps-accent")
        .trim() || "#3d8eff";
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

window.addEventListener("mouseup", () => {
  if (!isSelectTool(currentTool) || !isSelecting) {
    isSelecting = false;
    return;
  }

  if (selectionShape === "free" && selectionPath.length > 2) {
    // Close the freehand path visually
    clearShapePreview();
    previewCtx.save();
    previewCtx.setLineDash([6, 4]);
    previewCtx.strokeStyle =
      getComputedStyle(document.documentElement)
        .getPropertyValue("--ps-accent")
        .trim() || "#3d8eff";
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
    let minX = Infinity,
      minY = Infinity,
      maxX = -Infinity,
      maxY = -Infinity;
    for (let i = 0; i < selectionPath.length; i++) {
      const pt = selectionPath[i];
      if (pt.x < minX) minX = pt.x;
      if (pt.y < minY) minY = pt.y;
      if (pt.x > maxX) maxX = pt.x;
      if (pt.y > maxY) maxY = pt.y;
    }
    selectionRect = { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
  }

  const shapeLabels = { rect: "rect", oval: "oval", free: "freehand" };
  if (selectionRect && selectionRect.w > 5) {
    document.getElementById("statusRegion").textContent =
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
  canvasStack.style.transformOrigin = "center center";
  document.getElementById("zoomValue").textContent =
    Math.round(zoomLevel * 100) + "%";
}

document.getElementById("zoomIn").addEventListener("click", () => {
  zoomLevel = Math.min(zoomLevel + 0.25, 3);
  applyZoom();
});
document.getElementById("zoomOut").addEventListener("click", () => {
  zoomLevel = Math.max(zoomLevel - 0.25, 0.25);
  applyZoom();
});
document.getElementById("zoomReset").addEventListener("click", () => {
  zoomLevel = 1;
  applyZoom();
});
canvasViewport.addEventListener("wheel", (e) => {
  if (!e.ctrlKey) return;
  e.preventDefault();
  zoomLevel = Math.min(
    Math.max(zoomLevel + (e.deltaY < 0 ? 0.1 : -0.1), 0.25),
    3,
  );
  applyZoom();
});

// ========================================================
// MANGA CHATBOX LOGIC
// ========================================================
let currentChatImageFile = null;

async function loadChatHistory() {
  try {
    const res = await fetch("/api/chat/history");
    const history = await res.json();
    const list = document.getElementById("chatHistoryList");
    if (!list) return;
    list.innerHTML = "";
    history.forEach((msg) => {
      appendChatMessage(msg.role, msg.content, msg.isImage);
    });
    list.scrollTop = list.scrollHeight;
  } catch (e) {
    console.error("Lỗi tải lịch sử chat", e);
  }
}

function previewChatImage(event) {
  const file = event.target.files[0];
  if (file) {
    currentChatImageFile = file;
    const reader = new FileReader();
    reader.onload = (e) => {
      document.getElementById("chatImgPrevSrc").src = e.target.result;
      document.getElementById("chatImagePreview").style.display = "block";
    };
    reader.readAsDataURL(file);
  }
}

function removeChatImage() {
  currentChatImageFile = null;
  document.getElementById("chatImageInput").value = "";
  document.getElementById("chatImagePreview").style.display = "none";
}

async function sendChatMessage() {
  const input = document.getElementById("chatTextInput");
  const msg = input.value.trim();
  if (!msg && !currentChatImageFile) return;

  // Show user msg
  let userDisplay = msg;
  if (currentChatImageFile) userDisplay += " [Đã đính kèm ảnh]";
  appendChatMessage("user", userDisplay, false);

  input.value = "";
  const btnSend = document.getElementById("btnSendChat");
  btnSend.disabled = true;
  btnSend.innerHTML = "Đang xử lý...";

  const formData = new FormData();
  formData.append("message", msg);
  if (currentChatImageFile) {
    formData.append("image", currentChatImageFile);
  }

  // Hide preview
  removeChatImage();

  try {
    const res = await fetch("/api/chat/message", {
      method: "POST",
      body: formData,
    });
    const data = await res.json();
    appendChatMessage("ai", data.content, data.type === "image");
  } catch (e) {
    appendChatMessage("ai", "Lỗi: " + e.message, false);
  } finally {
    btnSend.disabled = false;
    btnSend.innerHTML = '<i class="fa-solid fa-paper-plane"></i>';
  }
}

function appendChatMessage(role, content, isImage) {
  const list = document.getElementById("chatHistoryList");
  if (!list) return;
  const msgDiv = document.createElement("div");
  msgDiv.className = "manga-chat-message";

  if (role === "user") {
    msgDiv.classList.add("is-user");
    msgDiv.textContent = content;
  } else {
    msgDiv.classList.add("is-ai");

    if (isImage) {
      msgDiv.innerHTML = `<img src="${content}" style="max-width:100%; border-radius:4px; margin-bottom:8px;" />
                                <button type="button" class="btn-submit" onclick="applyChatImageToCanvas('${content}')" style="width:100%; padding:4px;">Đưa vào Canvas</button>`;
    } else {
      msgDiv.innerHTML = content.replace(/\\n/g, "<br/>");
    }
  }

  list.appendChild(msgDiv);
  list.scrollTop = list.scrollHeight;
}

function applyChatImageToCanvas(base64DataUrl) {
  const img = new Image();
  img.onload = () => {
    const layerCtx = getActiveLayer().ctx;
    layerCtx.drawImage(img, 0, 0, CANVAS_W, CANVAS_H);
    pushHistoryEntry("AI Chat: Thêm ảnh");
  };
  img.src = base64DataUrl;
}

document.addEventListener("DOMContentLoaded", loadChatHistory);

// ========================================================
// PHẦN 7: Gộp tất cả layer thành 1 ảnh rồi gọi /api/ai/run
// ========================================================
function flattenAllLayers() {
  const flat = document.createElement("canvas");
  flat.width = CANVAS_W;
  flat.height = CANVAS_H;
  const flatCtx = flat.getContext("2d");
  layers.forEach((layer) => {
    if (!layer.visible) return;
    flatCtx.globalAlpha = layer.opacity / 100;
    flatCtx.drawImage(layer.canvas, 0, 0);
  });
  flatCtx.globalAlpha = 1;
  return flat;
}

// Lưu trang
// ========================================================
// PHẦN 8: Lưu trang & Submission
// ========================================================
const btnSavePage = document.getElementById("btnSavePage");

// Lưu trang
if (btnSavePage) {
  btnSavePage.addEventListener("click", async () => {
    const pageId = btnSavePage.dataset.pageId; // 👈 đọc tại thời điểm click, không đọc lúc load file
    if (!pageId) {
      alert("Không tìm thấy pageId, vui lòng tải lại trang!");
      return;
    }

    const base64 = flattenAllLayers().toDataURL("image/png");

    btnSavePage.disabled = true;
    btnSavePage.innerHTML = "Đang lưu...";

    try {
      const res = await fetch(`/api/page/${pageId}/savefile`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageBase64: base64 }),
      });
      const data = await res.json();
      if (data.status === "success") {
        btnSavePage.innerHTML = "Đã lưu!";
        setTimeout(() => {
          btnSavePage.innerHTML = "Lưu trang";
          if (data.redirectUrl) {
            window.location.href = data.redirectUrl;
          }
        }, 2000);
      } else {
        alert("" + data.message);
        btnSavePage.innerHTML = "Lưu trang";
      }
    } catch (err) {
      alert("Lỗi: " + err.message);
      btnSavePage.innerHTML = "Lưu trang";
    } finally {
      btnSavePage.disabled = false;
    }
  });
}
const btnSubmitSubmission = document.getElementById("btnSubmitSubmission");

if (btnSubmitSubmission) {
  btnSubmitSubmission.addEventListener("click", async () => {
    const submissionId = btnSubmitSubmission.dataset.submissionId;

    const base64 = flattenAllLayers().toDataURL("image/png");

    btnSubmitSubmission.disabled = true;

    btnSubmitSubmission.innerHTML = "Đang nộp...";

    try {
      const res = await fetch(`/api/submission/${submissionId}/savefile`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          imageBase64: base64,
        }),
      });

      const data = await res.json();

      if (data.status === "success") {
        btnSubmitSubmission.innerHTML = "Đã nộp";

        setTimeout(() => {
          if (data.redirectUrl) {
            window.location.href = data.redirectUrl;
          }
        }, 1000);
      } else {
        alert("" + data.message);

        btnSubmitSubmission.innerHTML = "Nộp bài";
      }
    } catch (err) {
      alert("" + err.message);

      btnSubmitSubmission.innerHTML = "Nộp bài";
    } finally {
      btnSubmitSubmission.disabled = false;
    }
  });
}

// Đặt toàn bộ vào một khối IIFE độc lập để tránh xung đột biến hệ thống
// Khối tác vụ trang: mở modal, lưu đúng page/submission và tránh lỗi thiếu submissionId.
(() => {
  const actionButton = document.getElementById("btnEditSubmission");
  const btnModalLoadPage = document.getElementById("btnModalLoadPage");
  const btnModalDownload = document.getElementById("btnModalDownload");
  const btnModalSaveArtwork = document.getElementById("btnModalSaveArtwork");
  const btnConfirmEdit = document.getElementById("btnConfirmEdit");
  const btnLoadPageLegacy = document.getElementById("btnLoadPage");
  const btnDownloadLegacy = document.getElementById("btnDownload");

  function getDrawContext() {
    const pageId =
      btnSavePage?.dataset.pageId || actionButton?.dataset.pageId || "";
    const submissionId =
      actionButton?.dataset.submissionId ||
      btnSavePage?.dataset.submissionId ||
      "";
    const returnUrl =
      actionButton?.dataset.returnUrl || btnSavePage?.dataset.returnUrl || "";

    return { pageId, submissionId, returnUrl };
  }

  function setButtonLoading(button, loading, label) {
    if (!button) return;
    button.disabled = loading;
    if (loading) {
      button.dataset.originalHtml = button.innerHTML;
      button.innerHTML = "Đang xử lý...";
    } else {
      button.innerHTML =
        label || button.dataset.originalHtml || button.innerHTML;
    }
  }

  async function saveArtwork(button, options = {}) {
    const { pageId, submissionId, returnUrl } = getDrawContext();

    if (!pageId && !submissionId) {
      alert("Không tìm thấy mã trang hoặc mã bài nộp để lưu bản vẽ!");
      return null;
    }

    const endpoint = submissionId
      ? `/api/submission/${submissionId}/savefile`
      : `/api/page/${pageId}/savefile`;
    const base64 = flattenAllLayers().toDataURL("image/png");

    setButtonLoading(button, true);

    try {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageBase64: base64 }),
      });
      const data = await res.json();

      if (data.status !== "success") {
        throw new Error(data.message || "Không thể lưu bản vẽ.");
      }

      if (button) {
        button.innerHTML = "Đã lưu";
      }

      if (options.redirect !== false) {
        const nextUrl = returnUrl || data.redirectUrl;
        if (nextUrl) {
          setTimeout(() => {
            window.location.href = nextUrl;
          }, 700);
        }
      }

      return data;
    } catch (err) {
      alert("Lỗi: " + err.message);
      return null;
    } finally {
      setTimeout(() => setButtonLoading(button, false), 700);
    }
  }

  async function updateSubmissionMeta(button) {
    const { submissionId } = getDrawContext();
    if (!submissionId) return { status: "skipped" };

    const status = document.getElementById("subStatus")?.value || "unfinish";
    const comment = document.getElementById("subComment")?.value || "";
    const body = new URLSearchParams({ status, comment });

    const res = await fetch(
      `/manga/mangaka/submission/${submissionId}/submit/data`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        },
        body,
      },
    );
    const data = await res.json();

    if (data.status !== "success") {
      throw new Error(data.message || "Không thể cập nhật trạng thái bài nộp.");
    }

    return data;
  }

  function openActionModal(e) {
    e?.preventDefault();
    e?.stopPropagation();
    const modal = document.getElementById("editSubmissionModal");
    if (modal) {
      modal.style.display = "flex";
    }
  }

  actionButton?.addEventListener("click", openActionModal);

  btnModalLoadPage?.addEventListener("click", () => {
    const input = document.getElementById("inputLoadPage");
    if (input) {
      input.value = "";
      input.click();
      return;
    }
    btnLoadPageLegacy?.click();
  });
  btnModalDownload?.addEventListener("click", () => btnDownloadLegacy?.click());
  btnModalSaveArtwork?.addEventListener("click", async (e) => {
    const saved = await saveArtwork(e.currentTarget, { redirect: false });
    if (saved) closeEditSubmissionModal();
  });

  btnConfirmEdit?.addEventListener("click", async (e) => {
    e.preventDefault();
    const button = e.currentTarget;
    const saved = await saveArtwork(button, { redirect: false });
    if (!saved) return;

    try {
      if (document.getElementById("subStatus")) {
        await updateSubmissionMeta(button);
      }
      button.innerHTML = "Hoàn tất";
      closeEditSubmissionModal();

      const { returnUrl } = getDrawContext();
      const nextUrl = returnUrl || saved.redirectUrl;
      if (nextUrl) {
        setTimeout(() => {
          window.location.href = nextUrl;
        }, 650);
      }
    } catch (err) {
      alert("Lỗi: " + err.message);
    }
  });

  document
    .getElementById("editSubmissionModal")
    ?.addEventListener("click", (e) => {
      if (e.target.id === "editSubmissionModal") {
        closeEditSubmissionModal();
      }
    });
})();

// Hàm hỗ trợ đóng modal nhanh khi người dùng bấm nút Hủy
function closeEditSubmissionModal() {
  const modal = document.getElementById("editSubmissionModal");
  if (modal) modal.style.display = "none";
}

// --- LOGIC XỬ LÝ ĐỌC FILE LÊN KHÔNG GIAN VẼ THỦ CÔNG ---
const btnLoadPage = document.getElementById("btnLoadPage");
const inputLoadPage = document.getElementById("inputLoadPage");

if (btnLoadPage && inputLoadPage) {
  btnLoadPage.addEventListener("click", () => {
    inputLoadPage.click(); // Kích hoạt sự kiện click giả lập vào thẻ input file ẩn
  });

  inputLoadPage.addEventListener("change", (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (ev) => {
      const img = new Image();
      img.onload = () => {
        // Xóa vùng canvas cũ trên layer chỉ định và vẽ đè file mới tải lên lên
        layers[1].ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
        layers[1].ctx.drawImage(img, 0, 0);
        pushHistoryEntry("Load file thủ công");
      };
      img.src = ev.target.result;
    };
    reader.readAsDataURL(file);
  });
}
// Khởi tạo layer list lần đầu
renderLayerList();
// ========================================
// DOWNLOAD MỌI Layer thành 1 file ảnh
// ========================================
document.getElementById("btnDownload")?.addEventListener("click", () => {
  // Gộp tất cả layer thành 1 ảnh
  const flatCanvas = flattenAllLayers();

  // Tạo link download tự động
  const link = document.createElement("a");
  link.download = "manga-page-" + Date.now() + ".png";
  link.href = flatCanvas.toDataURL("image/png");
  link.click();
});
