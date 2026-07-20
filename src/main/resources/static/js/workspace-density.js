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
      { label: "Ranking", icon: "fa-solid fa-chart-column", run: () => document.getElementById("rankingSection")?.scrollIntoView({ behavior: "smooth" }) },
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
      { label: "Công việc được giao", icon: "fa-solid fa-list-check", primary: true, run: () => window.openTab?.("tab-project") },
      { label: "Mở bảng vẽ", icon: "fa-solid fa-brush", run: () => window.openTab?.("tab-draw") },
      { label: "Tin nhắn", icon: "fa-solid fa-comments", run: () => window.openTab?.("tab-chat") },
    ]);
  }

  function initTantou() {
    if (!document.getElementById("pendingCancelTbody") || !document.getElementById("tab-home")) return;
    addQuickActions("tab-home", [
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
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
