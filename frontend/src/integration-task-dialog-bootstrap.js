// Data integration must not depend on the Monaco/development workspace runtime.
// This bootstrap is intentionally tiny and defensive: it captures the create-task
// action first, then loads the full dialog module independently.
let dialogModulePromise = null;

function loadDialogModule() {
  if (window.platformIntegrationTaskDialog && typeof window.platformIntegrationTaskDialog.open === 'function') {
    return Promise.resolve(window.platformIntegrationTaskDialog);
  }
  if (!dialogModulePromise) {
    dialogModulePromise = import('./integration-task-dialog.js')
      .then(() => {
        const api = window.platformIntegrationTaskDialog;
        if (!api || typeof api.open !== 'function') {
          throw new Error('同步任务模块初始化失败');
        }
        return api;
      })
      .catch((error) => {
        dialogModulePromise = null;
        console.error('[integration] failed to load create-task dialog', error);
        throw error;
      });
  }
  return dialogModulePromise;
}

function createTaskTrigger(target) {
  if (!target || typeof target.closest !== 'function') return null;
  const page = target.closest('#page-integration');
  if (!page) return null;
  const clickable = target.closest('button, a, [role="button"], .mbtn');
  if (!clickable || !page.contains(clickable)) return null;
  const text = String(clickable.textContent || '').replace(/\s+/g, '');
  return text.includes('新建同步任务') ? clickable : null;
}

function showLoadError(error) {
  const message = error && error.message ? error.message : '同步任务模块加载失败';
  const toast = document.getElementById('toast');
  if (toast) {
    const icon = toast.querySelector('i');
    const label = toast.querySelector('span');
    if (icon) icon.className = 'ri-error-warning-fill';
    if (label) label.textContent = message;
    toast.classList.add('show');
    window.setTimeout(() => toast.classList.remove('show'), 3500);
    return;
  }
  window.alert(message);
}

document.addEventListener('click', (event) => {
  if (!createTaskTrigger(event.target)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  loadDialogModule().then((api) => api.open()).catch(showLoadError);
}, true);

// Eagerly warm the module after the document is ready, but keep click-time loading
// as the source of truth so another frontend module cannot break this feature.
const warmup = () => loadDialogModule().catch(() => {});
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', warmup, { once: true });
else window.setTimeout(warmup, 0);

window.platformIntegrationTaskBootstrap = { load: loadDialogModule };
