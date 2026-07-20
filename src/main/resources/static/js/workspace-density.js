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
      button.innerHTML = `<i class="${action.icon}" aria-hidden="true"></i><span>${action.label}</span>`;
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

  function removeDeadPlaceholders() {
    document.querySelectorAll("#tab-assistant, #tab-other").forEach((tab) => {
      if (!/tính năng đang phát triển/i.test(tab.textContent || "")) return;
      removeNavigation(tab.id);
      tab.remove();
    });
  }

  function initAdmin() {
    if (!document.getElementById("tab-accounts") || !document.getElementById("tab-import")) return;
    mergeAsDetails("tab-import", "tab-accounts", {
      title: "Nhập dữ liệu Excel",
      description: "Cập nhật lượt xem và phiếu đánh giá ngay trong khu vực quản lý dữ liệu.",
      open: false,
    });
    const publishDetails = mergeAsDetails("tab-publish", "tab-vote", {
      title: "Chapter chờ xuất bản",
      description: "Hoàn tất bước xuất bản ngay sau quy trình duyệt và bình chọn.",
      open: false,
    });
    publishDetails?.addEventListener("toggle", () => {
      if (publishDetails.open && typeof window.loadPendingPublishChapters === "function") {
        window.loadPendingPublishChapters();
      }
    });
  }

  function initEditor() {
    if (!document.getElementById("activeSessionsBody") || !document.getElementById("proposalVoteBody")) return;
    mergeAsSection("tab-project", "tab-home", {
      title: "Đề xuất chờ biểu quyết",
      description: "Xem hồ sơ, tiến độ bỏ phiếu và đưa ra quyết định trong cùng một màn hình.",
    });
    addQuickActions("tab-home", [
      { label: "Ranking", icon: "fa-solid fa-chart-column", run: openSharedRankingModal },
      { label: "Phiên vote", icon: "fa-solid fa-check-to-slot", run: () => document.getElementById("activeSessionsSection")?.scrollIntoView({ behavior: "smooth" }) },
      { label: "Đề xuất chờ duyệt", icon: "fa-solid fa-file-circle-check", primary: true, run: () => document.getElementById("tab-project-merged")?.scrollIntoView({ behavior: "smooth" }) },
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
      { label: "Ranking", icon: "fa-solid fa-chart-column", run: openSharedRankingModal },
      { label: "Công việc được giao", icon: "fa-solid fa-list-check", primary: true, run: () => window.openTab?.("tab-project") },
      { label: "Mở bảng vẽ", icon: "fa-solid fa-brush", run: () => window.openTab?.("tab-draw") },
      { label: "Tin nhắn", icon: "fa-solid fa-comments", run: () => window.openTab?.("tab-chat") },
    ]);
  }

  function initTantou() {
    if (!document.getElementById("pendingCancelTbody") || !document.getElementById("tab-home")) return;
    addQuickActions("tab-home", [
      { label: "Ranking", icon: "fa-solid fa-chart-column", run: openSharedRankingModal },
      { label: "Duyệt bản thảo", icon: "fa-solid fa-file-circle-check", primary: true, run: () => window.openTab?.("tab-proposal") },
      { label: "Duyệt chapter", icon: "fa-solid fa-book-open", run: () => window.openTab?.("tab-chapter-review") },
      { label: "Quản lý Mangaka", icon: "fa-solid fa-users", run: () => { window.openTab?.("tab-manage-mangaka"); window.mmLoadMangakas?.(); } },
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

  function init() {
    removeDeadPlaceholders();
    initAdmin();
    initEditor();
    initMangaka();
    initAssistant();
    initTantou();
    normalizeWorkspaceShells();
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeSharedRankingModal();
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
