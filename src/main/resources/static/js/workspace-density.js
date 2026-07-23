/**
 * Chuẩn hóa workspace giữa các role và ghép các tab ít chức năng.
 * API ranking: GET /manga/ranking?month={month}&quarter={quarter}&year={year}.
 */
(function () {
  "use strict";

  function navItemFor(tabId) {
    const escaped = window.CSS?.escape ? CSS.escape(tabId) : tabId;
    const button = document.querySelector(
      `.topnav [onclick*="${escaped}"], .topnav [data-target="${escaped}"]`,
    );
    return button?.closest("li") || null;
  }

  function removeNavigation(tabId) {
    navItemFor(tabId)?.remove();
  }

  function stripRedundantHeading(container) {
    const firstHeading = container.querySelector(":scope > h1, :scope > h2");
    firstHeading?.remove();
    const firstRule = container.querySelector(":scope > hr");
    firstRule?.remove();
  }

  function mergeAsDetails(sourceId, targetId, options) {
    const source = document.getElementById(sourceId);
    const target = document.getElementById(targetId);
    if (!source || !target || source.dataset.workspaceMerged === "true") return null;

    source.dataset.workspaceMerged = "true";
    source.classList.remove("tab-pane", "active");
    stripRedundantHeading(source);

    const details = document.createElement("details");
    details.className = "workspace-merged-details";
    details.id = `${sourceId}-merged`;
    details.open = Boolean(options.open);
    details.innerHTML = `
      <summary>
        <span>
          <strong>${options.title}</strong>
          <small>${options.description}</small>
        </span>
        <span class="workspace-merged-state" aria-hidden="true"></span>
      </summary>
      <div class="workspace-merged-body"></div>`;
    details.querySelector(".workspace-merged-body").append(...source.childNodes);
    source.replaceWith(details);
    target.appendChild(details);
    removeNavigation(sourceId);
    return details;
  }

  function mergeAsSection(sourceId, targetId, options) {
    const source = document.getElementById(sourceId);
    const target = document.getElementById(targetId);
    if (!source || !target || source.dataset.workspaceMerged === "true") return null;

    const wasActive = source.classList.contains("active");
    source.dataset.workspaceMerged = "true";
    source.classList.remove("tab-pane", "active");
    stripRedundantHeading(source);

    const section = document.createElement("section");
    section.className = "workspace-merged-section";
    section.id = `${sourceId}-merged`;
    section.innerHTML = `
      <header class="workspace-merged-header">
        <div>
          <h2>${options.title}</h2>
          <p>${options.description}</p>
        </div>
      </header>
      <div class="workspace-merged-body"></div>`;
    section.querySelector(".workspace-merged-body").append(...source.childNodes);
    source.replaceWith(section);
    target.appendChild(section);
    removeNavigation(sourceId);
    if (wasActive) target.classList.add("active");
    return section;
  }

  function addQuickActions(targetId, actions) {
    const target = document.getElementById(targetId);
    if (!target || target.querySelector(":scope > .workspace-quick-actions")) return;

    const bar = document.createElement("nav");
    bar.className = "workspace-quick-actions";
    bar.setAttribute("aria-label", "Hành động nhanh");
    actions.forEach((action) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = action.primary
        ? "workspace-quick-action is-primary"
        : "workspace-quick-action";
      button.textContent = action.label;
      button.addEventListener("click", action.run);
      bar.appendChild(button);
    });

    const heading = target.querySelector(":scope > h1, :scope > h2, :scope > section > header");
    if (heading?.parentElement === target) heading.insertAdjacentElement("afterend", bar);
    else target.prepend(bar);
  }

  function ensureSharedRankingModal() {
    let overlay = document.getElementById("sharedRankingModalOverlay");
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = "sharedRankingModalOverlay";
    overlay.className = "modal-overlay ranking-modal-overlay shared-ranking-modal";
    overlay.hidden = true;
    overlay.innerHTML = `
      <div class="modal-content ranking-modal-content" role="dialog" aria-modal="true" aria-labelledby="sharedRankingModalTitle">
        <div class="modal-header ranking-modal-header">
          <div>
            <h5 id="sharedRankingModalTitle">Ranking series</h5>
            <p>So sánh hiệu suất series theo tháng, quý hoặc cả năm.</p>
          </div>
          <button type="button" class="modal-close-btn" data-ranking-close aria-label="Đóng bảng ranking">&times;</button>
        </div>
        <div class="modal-body ranking-modal-body">
          <div class="ranking-filter-bar" aria-label="Bộ lọc ranking">
            <div class="ranking-filter-field">
              <label for="sharedRankMonth">Tháng</label>
              <select id="sharedRankMonth">
                <option value="0">-- Cả năm --</option>
                ${Array.from({ length: 12 }, (_, index) => `<option value="${index + 1}">Tháng ${index + 1}</option>`).join("")}
              </select>
            </div>
            <div class="ranking-filter-field">
              <label for="sharedRankYear">Năm</label>
              <select id="sharedRankYear"></select>
            </div>
            <div class="ranking-filter-field">
              <label for="sharedRankQuarter">Quý</label>
              <select id="sharedRankQuarter">
                <option value="0">-- Tất cả --</option>
                <option value="1">Quý 1 (T1-T3)</option>
                <option value="2">Quý 2 (T4-T6)</option>
                <option value="3">Quý 3 (T7-T9)</option>
                <option value="4">Quý 4 (T10-T12)</option>
              </select>
            </div>
            <div class="ranking-filter-actions">
              <button type="button" class="ranking-filter-button" data-ranking-load>Xem ranking</button>
              <button type="button" class="ranking-filter-button" data-ranking-collapse>Thu lại</button>
            </div>
          </div>
          <div class="ranking-results" data-ranking-results>
            <div class="ranking-table-scroll">
              <table class="data-table">
                <thead><tr><th>Hạng</th><th>Mã Series</th><th>Tên Series</th><th>Lượt xem</th><th>Like</th><th>Dislike</th></tr></thead>
                <tbody data-ranking-body></tbody>
              </table>
              <p class="empty-msg" data-ranking-message>Đang tải ranking...</p>
            </div>
          </div>
        </div>
      </div>`;
    document.body.appendChild(overlay);

    const month = overlay.querySelector("#sharedRankMonth");
    const quarter = overlay.querySelector("#sharedRankQuarter");
    const year = overlay.querySelector("#sharedRankYear");
    const currentYear = new Date().getFullYear();
    for (let value = currentYear; value >= currentYear - 4; value -= 1) {
      year.add(new Option(String(value), String(value)));
    }
    month.addEventListener("change", () => { if (month.value !== "0") quarter.value = "0"; });
    quarter.addEventListener("change", () => { if (quarter.value !== "0") month.value = "0"; });
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay || event.target.closest("[data-ranking-close]")) closeSharedRankingModal();
    });
    overlay.querySelector("[data-ranking-load]").addEventListener("click", loadSharedRanking);
    overlay.querySelector("[data-ranking-collapse]").addEventListener("click", () => {
      overlay.querySelector("[data-ranking-results]").hidden = true;
    });
    return overlay;
  }

  function rankingCell(value) {
    const cell = document.createElement("td");
    cell.textContent = value == null ? "—" : String(value);
    return cell;
  }

  async function loadSharedRanking() {
    const overlay = ensureSharedRankingModal();
    const results = overlay.querySelector("[data-ranking-results]");
    const tbody = overlay.querySelector("[data-ranking-body]");
    const message = overlay.querySelector("[data-ranking-message]");
    const table = overlay.querySelector("table");
    const loadButton = overlay.querySelector("[data-ranking-load]");
    results.hidden = false;
    tbody.replaceChildren();
    table.hidden = true;
    message.hidden = false;
    message.textContent = "Đang tải ranking...";
    loadButton.disabled = true;
    const query = new URLSearchParams({
      month: overlay.querySelector("#sharedRankMonth").value,
      quarter: overlay.querySelector("#sharedRankQuarter").value,
      year: overlay.querySelector("#sharedRankYear").value,
    });
    try {
      const response = await fetch(`/manga/ranking?${query}`);
      if (!response.ok) throw new Error("Không thể tải ranking");
      const rows = await response.json();
      if (!rows.length) {
        message.textContent = "Không có dữ liệu trong khoảng thời gian này.";
        return;
      }
      const medals = ["🥇", "🥈", "🥉"];
      rows.forEach((item) => {
        const row = document.createElement("tr");
        row.append(
          rankingCell(`${medals[item.rank - 1] || ""}${medals[item.rank - 1] ? " " : ""}${item.rank}`),
          rankingCell(item.seriesId), rankingCell(item.seriesName), rankingCell(item.totalView),
          rankingCell(item.totalLike), rankingCell(item.totalDislike),
        );
        tbody.appendChild(row);
      });
      table.hidden = false;
      message.hidden = true;
    } catch (error) {
      message.textContent = "Không thể tải ranking. Vui lòng thử lại.";
    } finally {
      loadButton.disabled = false;
    }
  }

  function openSharedRankingModal() {
    const overlay = ensureSharedRankingModal();
    overlay.hidden = false;
    overlay.classList.add("show");
    document.documentElement.classList.add("app-modal-open");
    loadSharedRanking();
    requestAnimationFrame(() => overlay.querySelector("#sharedRankMonth")?.focus());
  }

  function closeSharedRankingModal() {
    const overlay = document.getElementById("sharedRankingModalOverlay");
    if (!overlay) return;
    overlay.classList.remove("show");
    overlay.hidden = true;
    document.documentElement.classList.remove("app-modal-open");
  }

  window.openSharedRankingModal = openSharedRankingModal;

  let workflowReturnFocus = null;

  function ensureSharedWorkflowModal({ id, title, description, content }) {
    let overlay = document.getElementById(id);
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = id;
    overlay.className = "modal-overlay ranking-modal-overlay shared-workflow-modal";
    overlay.hidden = true;
    overlay.innerHTML = `
      <div class="modal-content ranking-modal-content workflow-modal-content" role="dialog" aria-modal="true" aria-labelledby="${id}Title">
        <div class="modal-header ranking-modal-header">
          <div>
            <h5 id="${id}Title">${title}</h5>
            <p>${description}</p>
          </div>
          <button type="button" class="modal-close-btn" data-workflow-close aria-label="Đóng ${title}">&times;</button>
        </div>
        <div class="modal-body ranking-modal-body workflow-modal-body"></div>
      </div>`;
    overlay.querySelector(".workflow-modal-body").appendChild(content);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay || event.target.closest("[data-workflow-close]")) {
        closeSharedWorkflowModal(overlay);
      }
    });
    document.body.appendChild(overlay);
    return overlay;
  }

  function openSharedWorkflowModal(id, trigger) {
    const overlay = document.getElementById(id);
    if (!overlay) return;
    workflowReturnFocus = trigger || document.activeElement;
    overlay.hidden = false;
    overlay.classList.add("show");
    document.documentElement.classList.add("app-modal-open");
    requestAnimationFrame(() => overlay.querySelector("[data-workflow-close]")?.focus());
  }

  function closeSharedWorkflowModal(overlay) {
    const target = typeof overlay === "string" ? document.getElementById(overlay) : overlay;
    if (!target || target.hidden) return;
    target.classList.remove("show");
    target.hidden = true;
    document.documentElement.classList.remove("app-modal-open");
    workflowReturnFocus?.focus?.();
    workflowReturnFocus = null;
  }

  function removeContentTabIcons() {
    document
      .querySelectorAll(
        ".workspace-quick-actions i, .shared-workflow-modal i, [role='tablist'] > button i, .proposal-tabs-nav .proposal-tab-btn i, .approved-proposal-tabs .approved-proposal-tab i",
      )
      .forEach((icon) => icon.remove());
  }

  function removeDeadPlaceholders() {
    document.querySelectorAll("#tab-assistant, #tab-other").forEach((tab) => {
      if (!/tính năng đang phát triển/i.test(tab.textContent || "")) return;
      removeNavigation(tab.id);
      tab.remove();
    });
  }

  function initAdmin() {
    const voteTab = document.getElementById("tab-vote");
    const importTab = document.getElementById("tab-import");
    const publishTab = document.getElementById("tab-publish");
    if (!voteTab || !importTab || !publishTab) return;

    const voteHeading = voteTab.querySelector(":scope > h2");
    if (voteHeading) voteHeading.textContent = "Bình chọn";

    const cards = [...voteTab.querySelectorAll(":scope > .section-card")];
    const rankingCard = cards[0];
    const sessionsCard = cards[1];
    rankingCard?.classList.add("admin-vote-ranking-panel");
    sessionsCard?.classList.add("admin-vote-sessions-panel");

    const prepareTabContent = (tab, className) => {
      const wasActive = tab.classList.contains("active");
      tab.classList.remove("tab-pane", "active");
      stripRedundantHeading(tab);
      tab.classList.add("admin-workflow-content", className);
      removeNavigation(tab.id);
      return wasActive;
    };

    const importWasActive = prepareTabContent(importTab, "admin-import-content");
    ensureSharedWorkflowModal({
      id: "adminExcelImportModal",
      title: "Nhập dữ liệu Excel",
      description: "Cập nhật lượt xem chapter và phiếu Like/Dislike từ tệp Excel.",
      content: importTab,
    });

    const publishWasActive = prepareTabContent(publishTab, "admin-publish-content");
    ensureSharedWorkflowModal({
      id: "adminPendingPublishModal",
      title: "Chapter chờ xuất bản",
      description: "Kiểm tra các chapter đã được Tantou duyệt và hoàn tất xuất bản.",
      content: publishTab,
    });

    if (importWasActive || publishWasActive) voteTab.classList.add("active");

    addQuickActions("tab-vote", [
      {
        label: "Nhập dữ liệu Excel",
        primary: true,
        run: (event) => openSharedWorkflowModal("adminExcelImportModal", event.currentTarget),
      },
      {
        label: "Chapter chờ xuất bản",
        run: (event) => {
          window.loadPendingPublishChapters?.();
          openSharedWorkflowModal("adminPendingPublishModal", event.currentTarget);
        },
      },
    ]);

    // Tab Phân công giữ hai bảng ngay trong content; admin.html quản lý việc chuyển bảng.
  }

  function initEditor() {
    if (!document.getElementById("activeSessionsBody") || !document.getElementById("proposalVoteBody")) return;
    const home = document.getElementById("tab-home");
    const sessions = document.getElementById("activeSessionsSection");
    const sessionsHeading = sessions?.previousElementSibling;
    const proposal = document.getElementById("tab-project");
    const proposalWasActive = proposal?.classList.contains("active");

    if (!home || !sessions || !proposal) return;

    if (sessionsHeading?.matches("h1, h2, h3")) sessionsHeading.remove();
    ensureSharedWorkflowModal({
      id: "editorVoteSessionsModal",
      title: "Phiên vote",
      description: "Theo dõi các phiên đang mở và gửi lựa chọn của bạn.",
      content: sessions,
    });

    proposal.classList.remove("tab-pane", "active");
    stripRedundantHeading(proposal);
    ensureSharedWorkflowModal({
      id: "editorPendingProposalsModal",
      title: "Đề xuất chờ duyệt",
      description: "Xem hồ sơ, tiến độ bỏ phiếu và đưa ra quyết định.",
      content: proposal,
    });
    removeNavigation("tab-project");
    if (proposalWasActive) home.classList.add("active");

    addQuickActions("tab-home", [
      { label: "Ranking", run: openSharedRankingModal },
      { label: "Phiên vote", run: (event) => openSharedWorkflowModal("editorVoteSessionsModal", event.currentTarget) },
      { label: "Đề xuất chờ duyệt", primary: true, run: (event) => openSharedWorkflowModal("editorPendingProposalsModal", event.currentTarget) },
    ]);
  }

  function initMangaka() {
    if (!document.querySelector(".home-report-hub") || !document.getElementById("assistantTasksContainer")) return;
    const details = mergeAsDetails("tab-assistant", "tab-home", {
      title: "Theo dõi công việc của trợ lý",
      description: "Kiểm tra task đang thực hiện và mở chi tiết ngay từ trang tổng quan.",
      open: false,
    });
    details?.addEventListener("toggle", () => {
      if (details.open && typeof window.loadAssistantTasks === "function") {
        window.loadAssistantTasks();
      }
    });
  }

  function initAssistant() {
    if (!document.querySelector(".assistant-task-table") || !document.getElementById("tab-home")) return;
    addQuickActions("tab-home", [
      { label: "Ranking", run: openSharedRankingModal },
      { label: "Công việc được giao", primary: true, run: () => window.openTab?.("tab-project") },
      { label: "Mở bảng vẽ", run: () => window.openTab?.("tab-draw") },
      { label: "Tin nhắn", run: () => window.openTab?.("tab-chat") },
    ]);
  }

  function initTantou() {
    if (!document.getElementById("pendingCancelTbody") || !document.getElementById("tab-home")) return;
    addQuickActions("tab-home", [
      { label: "Ranking", run: openSharedRankingModal },
      { label: "Duyệt bản thảo", primary: true, run: () => window.openTab?.("tab-proposal") },
      { label: "Duyệt chapter", run: () => window.openTab?.("tab-chapter-review") },
      { label: "Quản lý Mangaka", run: () => { window.openTab?.("tab-manage-mangaka"); window.mmLoadMangakas?.(); } },
    ]);
  }

  function directHeading(tab) {
    return Array.from(tab.children).find((child) => /^H[12]$/.test(child.tagName)) || null;
  }

  function isNativeWorkspace(tab) {
    if (tab.id === "tab-draw") return true;
    if (tab.querySelector(":scope > .proposal-workspace")) return true;
    if (tab.querySelector(":scope > .home-report-hub")) return true;
    if (tab.id === "tab-project" && tab.querySelector("#project-view-series")) return true;
    return false;
  }

  function normalizeNativeWorkspace(tab) {
    tab.classList.add("role-workspace-native");
    const nativeSurface = tab.querySelector(
      ":scope > .proposal-workspace, :scope > .home-report-hub, :scope > .project-series-surface",
    );
    nativeSurface?.classList.add("role-workspace-native-surface");
  }

  function buildWorkspaceShell(tab) {
    if (!tab || tab.dataset.workspaceShell === "true") return;
    tab.dataset.workspaceShell = "true";

    if (isNativeWorkspace(tab)) {
      normalizeNativeWorkspace(tab);
      return;
    }

    const heading = directHeading(tab);
    const title = heading?.textContent?.trim() || "Không gian làm việc";
    heading?.remove();

    const surface = document.createElement("section");
    surface.className = "role-workspace-surface";
    surface.setAttribute("aria-label", title);

    const header = document.createElement("header");
    header.className = "role-workspace-header";
    header.innerHTML = `<div><h1>${title}</h1></div>`;

    const content = document.createElement("div");
    content.className = "role-workspace-content";
    content.append(...tab.childNodes);

    surface.append(header, content);
    tab.appendChild(surface);
  }

  function normalizeWorkspaceShells() {
    document
      .querySelectorAll(".flex-container > .tab-pane")
      .forEach(buildWorkspaceShell);
    document.body.classList.add("role-workspaces-ready");
  }

  /* Tạo vùng cuộn nội bộ cho các bảng có số dòng tăng theo dữ liệu API. */
  const TABLE_SCROLL_CONTAINER_SELECTOR = [
    ".app-data-table-scroll",
    ".assistant-task-table-wrap",
    ".proposal-table-stage",
    ".ranking-table-scroll",
    ".project-series-table-scroll",
    ".chapter-list-pane",
    ".shared-workflow-modal",
  ].join(",");

  function wrapScrollableTable(table) {
    if (!(table instanceof HTMLTableElement)) return;
    if (!table.closest(".role-workspace-content")) return;
    if (table.closest(TABLE_SCROLL_CONTAINER_SELECTOR)) return;

    const wrapper = document.createElement("div");
    wrapper.className = "app-data-table-scroll";
    wrapper.setAttribute("role", "region");
    wrapper.setAttribute("aria-label", "Bảng dữ liệu có thể cuộn");
    wrapper.tabIndex = 0;
    table.parentNode?.insertBefore(wrapper, table);
    wrapper.appendChild(table);
  }

  function normalizeScrollableTables(root = document) {
    const tables = [];
    if (root instanceof HTMLTableElement && root.matches(".data-table, .profile-role-table")) {
      tables.push(root);
    }
    root
      .querySelectorAll?.("table.data-table, table.profile-role-table")
      .forEach((table) => tables.push(table));
    tables.forEach(wrapScrollableTable);
  }

  /* Theo dõi bảng được render sau khi đổi tab hoặc tải dữ liệu để không cần tải lại trang. */
  function observeScrollableTables() {
    const workspaceRoot = document.querySelector(".flex-container");
    if (!workspaceRoot) return;

    const observer = new MutationObserver((records) => {
      records.forEach((record) => {
        record.addedNodes.forEach((node) => {
          if (node.nodeType === Node.ELEMENT_NODE) normalizeScrollableTables(node);
        });
      });
    });
    observer.observe(workspaceRoot, { childList: true, subtree: true });
  }

  function init() {
    removeDeadPlaceholders();
    initAdmin();
    initEditor();
    initMangaka();
    initAssistant();
    initTantou();
    normalizeWorkspaceShells();
    normalizeScrollableTables();
    observeScrollableTables();
    removeContentTabIcons();
    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape") return;
      closeSharedRankingModal();
      document
        .querySelectorAll(".shared-workflow-modal.show")
        .forEach((overlay) => closeSharedWorkflowModal(overlay));
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
