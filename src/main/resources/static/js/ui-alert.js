(function () {
  const nativeAlert = window.alert ? window.alert.bind(window) : null;
  let previousFocus = null;

  function getAlertMeta(message) {
    const text = String(message || "");
    if (/^\s*(|success|thành công)/i.test(text)) {
      return { type: "success", title: "Thành công" };
    }
    if (/^\s*(|error|lỗi)/i.test(text)) {
      return { type: "error", title: "Lỗi" };
    }
    if (/^\s*(||warning|cảnh báo)/i.test(text)) {
      return {
        type: "warning",
        title: "Cảnh báo",
        icon: "fa-circle-info",
      };
    }
    return { type: "info", title: "Thông báo" };
  }

  function cleanMessage(message) {
    return String(message || "")
      .replace(/^\s*(|||)\s*/u, "")
      .trim();
  }

  function ensureAlertModal() {
    let overlay = document.getElementById("uiAlertOverlay");
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = "uiAlertOverlay";
    overlay.className = "ui-alert-overlay";
    overlay.setAttribute("role", "dialog");
    overlay.setAttribute("aria-modal", "true");
    overlay.setAttribute("aria-labelledby", "uiAlertTitle");
    overlay.innerHTML = `
      <div class="ui-alert-dialog" role="document">
        <div class="ui-alert-head">
          <div class="ui-alert-title-wrap">
            <h3 id="uiAlertTitle">Thông báo</h3>
          </div>
          <button type="button" class="modal-close-btn ui-alert-close" aria-label="Đóng">×</button>
        </div>
        <div class="ui-alert-body">
          <p id="uiAlertMessage"></p>
        </div>
        <div class="ui-alert-actions">
          <button type="button" class="btn-modal-confirm ui-alert-ok">OK</button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);

    const close = () => closeAlertModal();
    overlay.querySelector(".ui-alert-close").addEventListener("click", close);
    overlay.querySelector(".ui-alert-ok").addEventListener("click", close);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) close();
    });

    return overlay;
  }

  function closeAlertModal() {
    const overlay = document.getElementById("uiAlertOverlay");
    if (!overlay) return;
    overlay.classList.remove("show");
    overlay.style.display = "none";
    document.documentElement.classList.remove("ui-alert-open");
    if (previousFocus && typeof previousFocus.focus === "function") {
      previousFocus.focus({ preventScroll: true });
    }
    previousFocus = null;
  }

  window.showUiAlert = function showUiAlert(message) {
    if (!document.body) {
      if (nativeAlert) nativeAlert(message);
      return;
    }

    const overlay = ensureAlertModal();
    const meta = getAlertMeta(message);
    const dialog = overlay.querySelector(".ui-alert-dialog");
    const title = overlay.querySelector("#uiAlertTitle");
    const messageEl = overlay.querySelector("#uiAlertMessage");

    previousFocus = document.activeElement;
    dialog.className = `ui-alert-dialog ui-alert-${meta.type}`;
    title.textContent = meta.title;
    messageEl.textContent = cleanMessage(message) || meta.title;

    overlay.style.display = "flex";
    requestAnimationFrame(() => overlay.classList.add("show"));
    document.documentElement.classList.add("ui-alert-open");

    const okButton = overlay.querySelector(".ui-alert-ok");
    if (okButton) okButton.focus({ preventScroll: true });
  };

  window.alert = window.showUiAlert;

  document.addEventListener("keydown", (event) => {
    const overlay = document.getElementById("uiAlertOverlay");
    if (!overlay || !overlay.classList.contains("show")) return;
    if (event.key === "Escape" || event.key === "Enter") {
      event.preventDefault();
      closeAlertModal();
    }
  });
})();

/* Viewport floating modal normalization */
(function () {
  const floatingModalIds = [
    "scriptModalOverlay",
    "kanbanScriptModalOverlay",
    "chapterScriptModalOverlay",
    "addPageModalOverlay",
    "createChapterModalOverlay",
    "startSeriesModalOverlay",
    "proposalModalOverlay",
    "proposalDetailModal",
    "proposalDetailModalOverlay",
    "resubmitModal",
    "resubmitModalOverlay",
    "assignModalOverlay",
    "reassignModalOverlay",
    "notificationModalOverlay",
    "assistantTaskDetailModal",
    "assistantTaskDetailOverlay",
    "assistantTaskImageZoom",
    "assistantTaskImgZoomOverlay",
    "aiModalOverlay",
    "editSubmissionModal",
  ];

  function liftFloatingModals() {
    if (!document.body) return;
    floatingModalIds.forEach((id) => {
      const modal = document.getElementById(id);
      if (!modal) return;
      modal.classList.add("app-floating-modal");
      if (modal.parentElement !== document.body) {
        document.body.appendChild(modal);
      }
    });
  }

  window.normalizeFloatingModals = liftFloatingModals;

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", liftFloatingModals);
  } else {
    liftFloatingModals();
  }
})();


/* Project confirm modal */
(function () {
  let confirmResolver = null;
  let previousConfirmFocus = null;

  function ensureConfirmModal() {
    let overlay = document.getElementById("uiConfirmOverlay");
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = "uiConfirmOverlay";
    overlay.className = "ui-alert-overlay ui-confirm-overlay app-floating-modal";
    overlay.setAttribute("role", "dialog");
    overlay.setAttribute("aria-modal", "true");
    overlay.setAttribute("aria-labelledby", "uiConfirmTitle");
    overlay.innerHTML = `
      <div class="ui-alert-dialog ui-confirm-dialog" role="document">
        <div class="ui-alert-head">
          <div class="ui-alert-title-wrap">
            <h3 id="uiConfirmTitle">X\u00e1c nh\u1eadn</h3>
          </div>
          <button type="button" class="modal-close-btn ui-confirm-close" aria-label="\u0110\u00f3ng">&times;</button>
        </div>
        <div class="ui-alert-body">
          <p id="uiConfirmMessage"></p>
        </div>
        <div class="ui-alert-actions ui-confirm-actions">
          <button type="button" class="btn-modal-cancel ui-confirm-cancel">H\u1ee7y</button>
          <button type="button" class="btn-modal-confirm ui-confirm-ok">X\u00e1c nh\u1eadn</button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);
    overlay.querySelector(".ui-confirm-close").addEventListener("click", () => closeConfirmModal(false));
    overlay.querySelector(".ui-confirm-cancel").addEventListener("click", () => closeConfirmModal(false));
    overlay.querySelector(".ui-confirm-ok").addEventListener("click", () => closeConfirmModal(true));
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) closeConfirmModal(false);
    });

    return overlay;
  }

  function closeConfirmModal(result) {
    const overlay = document.getElementById("uiConfirmOverlay");
    if (!overlay || !overlay.classList.contains("show")) return;

    overlay.classList.remove("show");
    overlay.style.display = "none";
    document.documentElement.classList.remove("ui-alert-open");

    if (previousConfirmFocus && typeof previousConfirmFocus.focus === "function") {
      previousConfirmFocus.focus({ preventScroll: true });
    }
    previousConfirmFocus = null;

    if (confirmResolver) {
      const resolve = confirmResolver;
      confirmResolver = null;
      resolve(Boolean(result));
    }
  }

  window.showUiConfirm = function showUiConfirm(message, options) {
    if (!document.body) return Promise.resolve(window.confirm(message));

    const overlay = ensureConfirmModal();
    const title = overlay.querySelector("#uiConfirmTitle");
    const messageEl = overlay.querySelector("#uiConfirmMessage");
    const okButton = overlay.querySelector(".ui-confirm-ok");
    const cancelButton = overlay.querySelector(".ui-confirm-cancel");
    const config = options || {};

    if (confirmResolver) closeConfirmModal(false);

    previousConfirmFocus = document.activeElement;
    title.textContent = config.title || "X\u00e1c nh\u1eadn";
    messageEl.textContent = String(message || "");
    okButton.textContent = config.okText || "X\u00e1c nh\u1eadn";
    cancelButton.textContent = config.cancelText || "H\u1ee7y";

    overlay.style.display = "flex";
    requestAnimationFrame(() => overlay.classList.add("show"));
    document.documentElement.classList.add("ui-alert-open");
    okButton.focus({ preventScroll: true });

    return new Promise((resolve) => {
      confirmResolver = resolve;
    });
  };

  window.showUiAlertAsync = function showUiAlertAsync(message) {
    if (!window.showUiAlert) return Promise.resolve();
    window.showUiAlert(message);

    return new Promise((resolve) => {
      const overlay = document.getElementById("uiAlertOverlay");
      if (!overlay) {
        resolve();
        return;
      }

      const cleanup = () => {
        overlay.querySelector(".ui-alert-ok")?.removeEventListener("click", cleanup);
        overlay.querySelector(".ui-alert-close")?.removeEventListener("click", cleanup);
        overlay.removeEventListener("click", onOverlayClick);
        document.removeEventListener("keydown", onKeyDown);
        resolve();
      };
      const onKeyDown = (event) => {
        if (event.key === "Escape" || event.key === "Enter") cleanup();
      };
      const onOverlayClick = (event) => {
        if (event.target === overlay) cleanup();
      };

      overlay.querySelector(".ui-alert-ok")?.addEventListener("click", cleanup, { once: true });
      overlay.querySelector(".ui-alert-close")?.addEventListener("click", cleanup, { once: true });
      overlay.addEventListener("click", onOverlayClick);
      document.addEventListener("keydown", onKeyDown);
    });
  };

  document.addEventListener("keydown", (event) => {
    const overlay = document.getElementById("uiConfirmOverlay");
    if (!overlay || !overlay.classList.contains("show")) return;
    if (event.key === "Escape") {
      event.preventDefault();
      closeConfirmModal(false);
    }
    if (event.key === "Enter") {
      event.preventDefault();
      closeConfirmModal(true);
    }
  });
})();
