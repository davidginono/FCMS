(() => {
const FCMS_ICON_PATHS = {
  "alert-triangle": '<path d="m21.73 18-8-14a2 2 0 0 0-3.46 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  "arrow-left": '<path d="m12 19-7-7 7-7"/><path d="M19 12H5"/>',
  "bar-chart-3": '<path d="M3 3v18h18"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/>',
  "building-2": '<path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18"/><path d="M6 12H4a2 2 0 0 0-2 2v8h20v-8a2 2 0 0 0-2-2h-2"/><path d="M10 6h4"/><path d="M10 10h4"/><path d="M10 14h4"/><path d="M10 18h4"/>',
  "check-circle-2": '<path d="M12 22a10 10 0 1 0-10-10 10 10 0 0 0 10 10Z"/><path d="m9 12 2 2 4-4"/>',
  "chevron-left": '<path d="m15 18-6-6 6-6"/>',
  "chevron-right": '<path d="m9 18 6-6-6-6"/>',
  "clipboard-list": '<rect width="8" height="4" x="8" y="2" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><path d="M12 11h4"/><path d="M12 16h4"/><path d="M8 11h.01"/><path d="M8 16h.01"/>',
  "download": '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/>',
  "edit-3": '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
  "file-text": '<path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5Z"/><path d="M14 2v6h6"/><path d="M16 13H8"/><path d="M16 17H8"/><path d="M10 9H8"/>',
  "filter": '<path d="M22 3H2l8 9.46V19l4 2v-8.54Z"/>',
  "landmark": '<line x1="3" x2="21" y1="22" y2="22"/><line x1="6" x2="6" y1="18" y2="11"/><line x1="10" x2="10" y1="18" y2="11"/><line x1="14" x2="14" y1="18" y2="11"/><line x1="18" x2="18" y1="18" y2="11"/><polygon points="12 2 20 7 4 7"/>',
  "layers": '<path d="m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.84l8.57 3.9a2 2 0 0 0 1.66 0l8.57-3.9a1 1 0 0 0 0-1.84Z"/><path d="m22 12.5-9.17 4.17a2 2 0 0 1-1.66 0L2 12.5"/><path d="m22 17.5-9.17 4.17a2 2 0 0 1-1.66 0L2 17.5"/>',
  "layout-dashboard": '<rect width="7" height="9" x="3" y="3" rx="1"/><rect width="7" height="5" x="14" y="3" rx="1"/><rect width="7" height="9" x="14" y="12" rx="1"/><rect width="7" height="5" x="3" y="16" rx="1"/>',
  "log-out": '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>',
  "menu": '<path d="M4 12h16"/><path d="M4 6h16"/><path d="M4 18h16"/>',
  "panel-left-close": '<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18"/><path d="m16 15-3-3 3-3"/>',
  "plus": '<path d="M5 12h14"/><path d="M12 5v14"/>',
  "printer": '<path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M6 14h12v8H6z"/>',
  "receipt": '<path d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z"/><path d="M16 8h-6"/><path d="M16 12h-6"/><path d="M16 16h-6"/>',
  "refresh-cw": '<path d="M21 12a9 9 0 0 1-15.6 6"/><path d="M3 16v5h5"/><path d="M3 12A9 9 0 0 1 18.6 6"/><path d="M21 8V3h-5"/>',
  "search": '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
  "trash-2": '<path d="M3 6h18"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/>',
  "user-circle": '<circle cx="12" cy="12" r="10"/><circle cx="12" cy="10" r="3"/><path d="M7 20.7a5 5 0 0 1 10 0"/>',
  "users": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>'
};

function renderIcons(root = document) {
  root.querySelectorAll("[data-lucide]").forEach((icon) => {
    const name = icon.getAttribute("data-lucide");
    const paths = FCMS_ICON_PATHS[name];
    if (!paths || icon.dataset.rendered === "true") {
      return;
    }
    icon.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
    icon.dataset.rendered = "true";
  });
}

if ("scrollRestoration" in window.history) {
  window.history.scrollRestoration = "manual";
}

const SCROLL_STORAGE_PREFIX = "fcms.scroll:";

function getScrollStorageKey() {
  return `${SCROLL_STORAGE_PREFIX}${window.location.pathname}`;
}

function readScrollState() {
  try {
    return JSON.parse(window.sessionStorage.getItem(getScrollStorageKey()) || "{}");
  } catch (error) {
    return {};
  }
}

function writeScrollState(state) {
  try {
    window.sessionStorage.setItem(getScrollStorageKey(), JSON.stringify(state));
  } catch (error) {
    // Browsers can disable storage; scroll persistence is progressive only.
  }
}

function updateScrollState(mutator) {
  const state = readScrollState();
  mutator(state);
  writeScrollState(state);
}

function prefersReducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function isScrollableElement(element) {
  const style = window.getComputedStyle(element);
  const overflow = `${style.overflow} ${style.overflowX} ${style.overflowY}`;
  return /(auto|scroll|overlay)/.test(overflow)
    && (element.scrollHeight > element.clientHeight + 1 || element.scrollWidth > element.clientWidth + 1);
}

function getScrollElementKey(element, index) {
  if (element.dataset.fcmsScrollKey) {
    return element.dataset.fcmsScrollKey;
  }
  if (element.id) {
    return `id:${element.id}`;
  }
  const label = element.getAttribute("aria-label");
  if (label) {
    return `aria:${label}`;
  }
  const classKey = Array.from(element.classList).slice(0, 3).join(".");
  return `auto:${element.tagName.toLowerCase()}:${classKey}:${index}`;
}

function getScrollCandidates() {
  const explicit = Array.from(document.querySelectorAll(
    "[data-fcms-persist-scroll], .erp-table-scroll, .app-sidebar-nav, .app-filter-toolbar"
  ));
  const automatic = Array.from(document.querySelectorAll("main, aside, nav, section, div, form"))
    .filter(isScrollableElement);
  return Array.from(new Set([...explicit, ...automatic]))
    .filter((element) => element !== document.body && element !== document.documentElement);
}

function rafThrottle(callback) {
  let frame = 0;
  return () => {
    if (frame) {
      return;
    }
    frame = window.requestAnimationFrame(() => {
      frame = 0;
      callback();
    });
  };
}

function restoreScrollPosition(target, position) {
  if (!position) {
    return;
  }
  const top = Number(position.top) || 0;
  const left = Number(position.left) || 0;
  const behavior = prefersReducedMotion() ? "auto" : "smooth";
  target.scrollTo({ top, left, behavior });
}

function initScrollPersistence() {
  const savedState = readScrollState();
  const candidates = getScrollCandidates();

  if (!window.WorkspaceNavigation?.restoring) window.setTimeout(() => {
    restoreScrollPosition(window, savedState.window);
    candidates.forEach((element, index) => {
      restoreScrollPosition(element, savedState.elements?.[getScrollElementKey(element, index)]);
    });
  }, 80);

  window.addEventListener("scroll", rafThrottle(() => {
    updateScrollState((state) => {
      state.window = { top: window.scrollY, left: window.scrollX };
    });
  }), { passive: true });

  candidates.forEach((element, index) => {
    const key = getScrollElementKey(element, index);
    element.addEventListener("scroll", rafThrottle(() => {
      updateScrollState((state) => {
        state.elements = state.elements || {};
        state.elements[key] = { top: element.scrollTop, left: element.scrollLeft };
      });
    }), { passive: true });
  });

  window.addEventListener("pagehide", () => {
    updateScrollState((state) => {
      state.window = { top: window.scrollY, left: window.scrollX };
      state.elements = state.elements || {};
      candidates.forEach((element, index) => {
        state.elements[getScrollElementKey(element, index)] = {
          top: element.scrollTop,
          left: element.scrollLeft
        };
      });
    });
  });
}

function showLoadingRegion(loadingRegion) {
  if (!loadingRegion) {
    return;
  }
  loadingRegion.classList.add("is-loading");
  loadingRegion.setAttribute("aria-busy", "true");
}

function hideLoadingRegion(loadingRegion) {
  if (!loadingRegion) {
    return;
  }
  loadingRegion.classList.remove("is-loading");
  loadingRegion.removeAttribute("aria-busy");
}

function clearLoadingRegions() {
  document.querySelectorAll(".app-table-region.is-loading, .app-table-region[aria-busy='true']")
    .forEach(hideLoadingRegion);
}

function getFilterFormFields(form) {
  return Array.from(form.elements).filter((field) => {
    return field.name && !["button", "submit", "reset", "file"].includes(field.type);
  });
}

function rememberFilterFormDefaults() {
  document.querySelectorAll("[data-fcms-filter-form]").forEach((form) => {
    getFilterFormFields(form).forEach((field) => {
      field.dataset.fcmsRenderedValue = field.value;
    });
  });
}

function syncFilterFormsToUrl() {
  const params = new URLSearchParams(window.location.search);

  document.querySelectorAll("[data-fcms-filter-form]").forEach((form) => {
    getFilterFormFields(form).forEach((field) => {
      if (field.type === "checkbox" || field.type === "radio") {
        field.checked = params.has(field.name)
          ? params.getAll(field.name).includes(field.value)
          : field.defaultChecked;
        return;
      }

      const renderedValue = field.dataset.fcmsRenderedValue ?? field.defaultValue ?? "";
      const nextValue = params.has(field.name) ? params.get(field.name) : renderedValue;
      field.value = nextValue ?? "";
    });
  });
}

function resetRestoredPageState() {
  clearLoadingRegions();
  syncFilterFormsToUrl();
}

function scheduleRestoredPageStateReset() {
  resetRestoredPageState();
  window.requestAnimationFrame(resetRestoredPageState);
  window.setTimeout(resetRestoredPageState, 80);
}

window.addEventListener("pagehide", resetRestoredPageState);
window.addEventListener("pageshow", scheduleRestoredPageStateReset);
window.addEventListener("popstate", scheduleRestoredPageStateReset);

document.addEventListener("DOMContentLoaded", () => {
  renderIcons();
  const topbar = document.querySelector('.app-topbar');
  if (topbar) {
    const measureTopbar = () => document.documentElement.style.setProperty('--fcms-topbar-height', `${topbar.getBoundingClientRect().height}px`);
    measureTopbar();
    if ('ResizeObserver' in window) new ResizeObserver(measureTopbar).observe(topbar);
  }
  rememberFilterFormDefaults();
  scheduleRestoredPageStateReset();
  initScrollPersistence();

  document.querySelectorAll("[data-print-page]").forEach((button) => {
    button.addEventListener("click", () => window.print());
  });

  document.querySelectorAll("[data-fcms-filter-form]").forEach((form) => {
    const targetId = form.getAttribute("data-fcms-loading-target");
    const loadingRegion = targetId ? document.getElementById(targetId) : null;

    form.addEventListener("submit", () => showLoadingRegion(loadingRegion));

  });

  document.querySelectorAll("[data-fcms-loading-link]").forEach((link) => {
    const targetId = link.getAttribute("data-fcms-loading-link");
    const loadingRegion = targetId ? document.getElementById(targetId) : null;
    link.addEventListener("click", (event) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      if (link.target && link.target !== "_self") {
        return;
      }
      showLoadingRegion(loadingRegion);
    });
  });

});

document.addEventListener('click',event=>{const button=event.target.closest('[data-dismiss-notice]');if(!button)return;const notice=button.closest('.console-message');const focused=notice?.contains(document.activeElement);notice?.remove();if(focused)(document.querySelector('[data-resource-notice]')||document.getElementById('main-content'))?.focus();});


document.addEventListener('DOMContentLoaded',()=>{const title=document.querySelector('main h1,[data-workspace-title]'),current=document.querySelector('.erp-page-path [aria-current=page]');if(title&&current)current.textContent=title.textContent.trim();});

})();
