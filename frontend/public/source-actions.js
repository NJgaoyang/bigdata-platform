(function () {
  'use strict';

  function ensureDialog() {
    if (document.getElementById('source-create-dialog')) return;
    var host = document.createElement('div');
    host.id = 'source-create-dialog';
    host.innerHTML = '<style>' +
      '#source-create-dialog{display:none;position:fixed;inset:0;z-index:1000;background:rgba(11,20,35,.46);align-items:center;justify-content:center}' +
      '#source-create-dialog.open{display:flex}' +
      '#source-create-dialog .dialog{width:500px;max-width:calc(100vw - 32px);background:#fff;border-radius:14px;box-shadow:0 24px 64px rgba(15,30,54,.28);overflow:hidden}' +
      '#source-create-dialog .head{height:62px;padding:0 22px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e9eef5;font-size:18px;font-weight:400;color:#334155}' +
      '#source-create-dialog .close{border:0;background:transparent;font-size:27px;color:#7f8da2;cursor:pointer}' +
      '#source-create-dialog form{padding:20px 22px 12px;display:grid;grid-template-columns:1fr 1fr;gap:14px}' +
      '#source-create-dialog label{display:grid;gap:6px;color:#4d5f78;font-size:13px;font-weight:400}' +
      '#source-create-dialog label.full{grid-column:1/-1}' +
      '#source-create-dialog .visibility-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid #e2e9f2;border-radius:8px;background:#f8faff}' +
      '#source-create-dialog .visibility-copy{display:grid;gap:3px}' +
      '#source-create-dialog .visibility-copy small{font-size:11px;color:#8a97aa;font-weight:400}' +
      '#source-create-dialog .switch{position:relative;display:inline-flex;flex:0 0 auto;width:42px;height:24px}' +
      '#source-create-dialog .switch input{position:absolute;opacity:0;width:1px;height:1px;margin:0}' +
      '#source-create-dialog .switch-track{position:absolute;inset:0;border-radius:20px;background:#c8d2df;cursor:pointer;transition:.18s}' +
      '#source-create-dialog .switch-track:after{content:"";position:absolute;width:18px;height:18px;left:3px;top:3px;border-radius:50%;background:#fff;transition:.18s}' +
      '#source-create-dialog .switch input:checked + .switch-track{background:#3478f6}' +
      '#source-create-dialog .switch input:checked + .switch-track:after{transform:translateX(18px)}' +
      '#source-create-dialog input,#source-create-dialog select{height:38px;padding:0 10px;border:1px solid #d9e2ee;border-radius:8px;color:#263b59;background:#fff;font:inherit;font-size:14px}' +
      '#source-create-dialog .error{grid-column:1/-1;min-height:18px;color:#df4343;font-size:12px}' +
      '#source-create-dialog .foot{padding:14px 22px 20px;display:flex;justify-content:flex-end;gap:10px}' +
      '#source-create-dialog .foot button{height:36px;padding:0 17px;border-radius:8px;border:1px solid #d8e2ef;background:#fff;color:#50627b;font:inherit;cursor:pointer}' +
      '#source-create-dialog .foot button.primary{border-color:#3478f6;background:#3478f6;color:#fff}' +
      '</style>' +
      '<div class="dialog" role="dialog" aria-modal="true" aria-labelledby="source-dialog-title">' +
      '<div class="head"><span id="source-dialog-title">新建数据源</span><button class="close" type="button" aria-label="关闭">×</button></div>' +
      '<form id="source-create-form">' +
      '<label>数据源名称<input name="name" required maxlength="100" placeholder="例如：订单 MySQL"></label>' +
      '<label>数据源类型<select name="type"><option value="MYSQL">MySQL</option><option value="STARROCKS">StarRocks</option></select></label>' +
      '<label class="full">主机地址<input name="host" required placeholder="例如：127.0.0.1"></label>' +
      '<label>端口<input name="port" required type="number" min="1" max="65535" value="3306"></label>' +
      '<label>数据库名（可选）<input name="databaseName"></label>' +
      '<label>用户名<input name="username" required placeholder="数据库用户名"></label>' +
      '<label>密码<input name="password" type="password" autocomplete="new-password" placeholder="新建时必填"></label>' +
      '<label class="full visibility-row"><span class="visibility-copy"><span>是否展示元数据</span><small>开启后可在元数据页面查看该数据源的库表信息</small></span><span class="switch"><input name="metadataVisible" type="checkbox" checked><span class="switch-track"></span></span></label>' +
      '<div class="error" role="alert"></div>' +
      '</form><div class="foot"><button class="cancel" type="button">取消</button><button class="primary submit" type="button">创建数据源</button></div></div>';
    document.body.appendChild(host);

    var form = host.querySelector('#source-create-form');
    var type = form.elements.type;
    function updateTypeHints() {
      var starRocks = type.value === 'STARROCKS';
      form.elements.port.value = starRocks ? '9030' : '3306';
    }
    type.addEventListener('change', updateTypeHints);
    host._updateTypeHints = updateTypeHints;
    host.querySelector('.close').onclick = closeDialog;
    host.querySelector('.cancel').onclick = closeDialog;
    host.onclick = function (event) { if (event.target === host) closeDialog(); };
    host.querySelector('.submit').onclick = submit;
    form.addEventListener('submit', function (event) { event.preventDefault(); submit(); });
  }

  function dialog() { return document.getElementById('source-create-dialog'); }
  function closeDialog() { var box = dialog(); if (box) box.classList.remove('open'); }
  function openDialog(item) {
    ensureDialog();
    var box = dialog();
    var form = box.querySelector('#source-create-form');
    form.reset();
    box._updateTypeHints();
    box._editingId = item && item.id ? item.id : null;
    box.querySelector('#source-dialog-title').textContent = box._editingId ? '编辑数据源' : '新建数据源';
    box.querySelector('.submit').textContent = box._editingId ? '保存' : '创建数据源';
    form.elements.password.placeholder = box._editingId ? '留空表示不修改' : '新建时必填';
    if (item) {
      ['name', 'type', 'host', 'port', 'databaseName', 'username'].forEach(function (key) { if (item[key] != null) form.elements[key].value = item[key]; });
      form.elements.metadataVisible.checked = item.metadataVisible !== false;
    }
    box.querySelector('.error').textContent = '';
    box.classList.add('open');
    window.setTimeout(function () { box.querySelector('[name="name"]').focus(); }, 0);
  }

  function submit() {
    var box = dialog();
    var form = box.querySelector('#source-create-form');
    var error = box.querySelector('.error');
    if (!form.reportValidity()) return;
    var data = Object.fromEntries(new FormData(form).entries());
    data.metadataVisible = form.elements.metadataVisible.checked;
    if (!box._editingId && !data.password) { error.textContent = '新建数据源必须填写密码'; return; }
    data.port = Number(data.port);
    var submitButton = box.querySelector('.submit');
    submitButton.disabled = true;
    submitButton.textContent = '创建中…';
    error.textContent = '';
    window.fetch('/api/data-sources' + (box._editingId ? '/' + box._editingId : ''), { method: box._editingId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify(data) })
      .then(function (response) { return response.json().catch(function () { return {}; }).then(function (payload) { if (!response.ok || payload.success === false) throw new Error(payload.message || '创建失败'); return payload; }); })
      .then(function (payload) {
        closeDialog();
        window.dispatchEvent(new CustomEvent('platform:data-source-changed', { detail: payload.data || null }));
      })
      .catch(function (reason) { error.textContent = reason.message || '创建失败，请检查连接信息'; })
      .finally(function () { submitButton.disabled = false; submitButton.textContent = box._editingId ? '保存' : '创建数据源'; });
  }

  function isCreateButton(element) {
    var button = element.closest && element.closest('button');
    return button && (button.matches('#page-source button[data-a="新建数据源"]') ||
      button.matches('#page-settings button[data-a="新建数据源"]') ||
      (button.closest('#page-source,#page-settings') && button.classList.contains('quickbox') && button.textContent.trim() === '新建数据源'));
  }

  document.addEventListener('click', function (event) {
    if (!isCreateButton(event.target)) return;
    event.preventDefault();
    event.stopPropagation();
    openDialog(null);
  }, true);
  window.addEventListener('platform:edit-data-source', function (event) { openDialog(event.detail || null); });
})();
