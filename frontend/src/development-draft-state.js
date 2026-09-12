const DRAFT_STATE_STYLE_ID = 'development-draft-state-style';
let queued = false;

function page() {
  return document.getElementById('page-development');
}

function activeTab() {
  return page()?.querySelector('.dev-tab.on:not(.dev-tab-add)') || null;
}

function isDraftTab() {
  const tab = activeTab();
  if (!tab) return false;
  const key = String(tab.dataset.devTab || '').trim();
  if (!key) return false;
  return !/^file-\d+$/.test(key);
}

function notify(message) {
  const toast = document.getElementById('toast');
  if (!toast) return;
  const icon = toast.querySelector('i');
  const label = toast.querySelector('span');
  if (icon) icon.className = 'ri-information-fill';
  if (label) label.textContent = message;
  toast.classList.add('show');
  window.clearTimeout(notify.timer);
  notify.timer = window.setTimeout(() => toast.classList.remove('show'), 2400);
}

function setHtml(node, value) {
  if (node && node.innerHTML !== value) node.innerHTML = value;
}

function setText(node, value) {
  if (node && node.textContent !== value) node.textContent = value;
}

function setHidden(node, hidden) {
  if (node && node.hidden !== !!hidden) node.hidden = !!hidden;
}

function installStyles() {
  if (document.getElementById(DRAFT_STATE_STYLE_ID)) return;
  const style = document.createElement('style');
  style.id = DRAFT_STATE_STYLE_ID;
  style.textContent = `
    #page-development[data-dev-draft-active="true"] .dev-publish-file,
    #page-development[data-dev-draft-active="true"] .dev-create-development-version,
    #page-development[data-dev-draft-active="true"] .dev-unpublish-file,
    #page-development[data-dev-draft-active="true"] .dev-save-to-project {
      display: none !important;
    }
  `;
  document.head.appendChild(style);
}

function applyDraftState() {
  const root = page();
  if (!root) return;
  const draft = isDraftTab();
  root.dataset.devDraftActive = String(draft);
  if (!draft) return;

  // A draft has no persisted file id and therefore must never inherit the
  // delivery state of the previously selected resource-tree file.
  const current = root.querySelector('.dev-version-development');
  const pushed = root.querySelector('.dev-version-online');
  const flowOnline = root.querySelector('.dev-version-flow-online');
  const badge = root.querySelector('.dev-flow-state');

  setHtml(current, '当前版本 <b>未保存</b>');
  setHtml(pushed, '已推送 <b>—</b>');
  if (flowOnline) {
    setHidden(flowOnline, false);
    setHtml(flowOnline, '线上版本 <b>—</b>');
  }
  if (badge) {
    setHidden(badge, false);
    if (badge.className !== 'dev-flow-state offline') badge.className = 'dev-flow-state offline';
    setText(badge, '尚未保存');
  }

  setHidden(root.querySelector('.dev-publish-file'), true);
  setHidden(root.querySelector('.dev-create-development-version'), true);
  setHidden(root.querySelector('.dev-unpublish-file'), true);
  setHidden(root.querySelector('.dev-save-to-project'), true);
}

function scheduleApply() {
  if (queued) return;
  queued = true;
  window.requestAnimationFrame(() => {
    queued = false;
    applyDraftState();
  });
}

function installGuards() {
  document.addEventListener('click', (event) => {
    if (!isDraftTab()) return;
    const blocked = event.target.closest?.([
      '#page-development .dev-publish-file',
      '#page-development .dev-create-development-version',
      '#page-development .dev-unpublish-file',
      '#page-development .dev-save-to-project'
    ].join(','));
    if (!blocked) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    notify('当前是未保存草稿，请先点击“保存”生成正式版本');
  }, true);
}

function boot() {
  installStyles();
  installGuards();
  const root = page();
  if (!root) return;
  const observer = new MutationObserver(scheduleApply);
  observer.observe(root, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden'] });
  scheduleApply();
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
else boot();
