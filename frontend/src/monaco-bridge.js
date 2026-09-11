import * as monaco from '../node_modules/monaco-editor/esm/vs/editor/editor.api.js';
import editorWorker from '../node_modules/monaco-editor/esm/vs/editor/editor.worker.js?worker';
import '../node_modules/monaco-editor/min/vs/editor/editor.main.css';
import './development-ui.css';
import './integration-task-dialog-bootstrap.js';
import './development-permissions.js';
import './development-workspace.js';
import './settings-cluster-fix.js';

const worker = editorWorker;
self.MonacoEnvironment = {
  getWorker() {
    return new worker();
  }
};

let editor = null;
let changeDisposable = null;
let mouseDisposable = null;
let completionDisposable = null;
let handlers = {};
let suppressChange = false;

const languageFor = (type) => {
  const value = String(type || 'SQL').toUpperCase();
  return value === 'PYTHON' ? 'python' : value === 'SHELL' ? 'shell' : 'sql';
};

const keywords = {
  sql: ['SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN', 'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'INSERT INTO', 'INSERT OVERWRITE', 'UPDATE', 'DELETE FROM', 'CREATE TABLE', 'ALTER TABLE', 'DROP TABLE', 'WITH', 'AS', 'AND', 'OR', 'NOT', 'NULL', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'DISTINCT'],
  python: ['def', 'class', 'import', 'from', 'as', 'return', 'if', 'elif', 'else', 'for', 'while', 'in', 'try', 'except', 'finally', 'with', 'lambda', 'yield', 'True', 'False', 'None', 'and', 'or', 'not', 'print'],
  shell: ['if', 'then', 'elif', 'else', 'fi', 'for', 'while', 'in', 'do', 'done', 'case', 'esac', 'function', 'export', 'source', 'echo', 'cd', 'mkdir', 'rm', 'cp', 'mv', 'cat', 'grep', 'awk', 'sed', 'curl']
};

function installEditorLayoutFixes() {
  if (document.getElementById('platform-monaco-layout-fixes')) return;
  const style = document.createElement('style');
  style.id = 'platform-monaco-layout-fixes';
  style.textContent = `
    #page-development .monaco-mounted > .linenos,
    #page-development .monaco-mounted .linenos { display: none !important; }
    #page-development .monaco-mounted,
    #page-development .monaco-editor-host { padding: 0 !important; margin: 0 !important; border: 0 !important; }
    #page-development .monaco-editor-host { width: 100% !important; height: 100% !important; }
    #page-development .monaco-editor-host .monaco-editor,
    #page-development .monaco-editor-host .overflow-guard { border: 0 !important; box-shadow: none !important; }
    #page-development .monaco-editor-host .decorationsOverviewRuler { display: none !important; }
    #page-development .monaco-editor-host .margin { border-right: 1px solid #edf1f5 !important; }
  `;
  document.head.appendChild(style);
}

function registerCompletion(language) {
  if (completionDisposable) completionDisposable.dispose();
  completionDisposable = monaco.languages.registerCompletionItemProvider(language, {
    triggerCharacters: [' ', '.', '_'],
    provideCompletionItems(model, position) {
      const range = new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column);
      const base = (keywords[language] || []).map((label) => ({
        label,
        kind: monaco.languages.CompletionItemKind.Keyword,
        insertText: label,
        range
      }));
      const tables = language === 'sql' ? (self.platformMonacoCompletionItems || []).map((item) => ({
        label: item.label,
        detail: item.detail || '表',
        kind: monaco.languages.CompletionItemKind.Field,
        insertText: item.label,
        range
      })) : [];
      return { suggestions: base.concat(tables) };
    }
  });
}

const layoutOptions = {
  lineNumbers: 'on',
  lineNumbersMinChars: 3,
  glyphMargin: false,
  folding: false,
  lineDecorationsWidth: 4,
  overviewRulerLanes: 0,
  overviewRulerBorder: false,
  hideCursorInOverviewRuler: true
};

function mount(container, value, fileType, nextHandlers) {
  if (!container) return null;
  installEditorLayoutFixes();
  handlers = nextHandlers || {};
  const language = languageFor(fileType);
  if (!editor) {
    editor = monaco.editor.create(container, {
      value: String(value || ''),
      language,
      theme: 'vs',
      automaticLayout: true,
      minimap: { enabled: false },
      ...layoutOptions,
      roundedSelection: false,
      scrollBeyondLastLine: false,
      tabSize: 2,
      insertSpaces: true,
      fontSize: 14,
      lineHeight: 22,
      padding: { top: 8, bottom: 12 },
      wordWrap: 'off',
      suggest: { showMethods: true, showFunctions: true, showVariables: true },
      quickSuggestions: true,
      scrollbar: {
        vertical: 'auto',
        horizontal: 'auto',
        verticalScrollbarSize: 8,
        horizontalScrollbarSize: 8,
        useShadows: false,
        verticalHasArrows: false,
        horizontalHasArrows: false
      }
    });
    container.classList.add('monaco-editor-host');
    container.parentElement && container.parentElement.classList.add('monaco-mounted');
    changeDisposable = editor.onDidChangeModelContent(() => {
      if (!suppressChange && handlers.onChange) handlers.onChange(editor.getValue());
    });
    mouseDisposable = editor.onMouseDown((event) => {
      if (!event.target || !event.target.position || !handlers.onTableClick) return;
      const model = editor.getModel();
      if (!model) return;
      const position = event.target.position;
      const line = model.getLineContent(position.lineNumber);
      let left = Math.max(0, position.column - 1);
      let right = left;
      while (left > 0 && /[A-Za-z0-9_.$`]/.test(line.charAt(left - 1))) left -= 1;
      while (right < line.length && /[A-Za-z0-9_.$`]/.test(line.charAt(right))) right += 1;
      const token = line.slice(left, right).replace(/`/g, '').replace(/^\.+|\.+$/g, '');
      if (token) handlers.onTableClick(token);
    });
  } else if (editor.getDomNode() !== container) {
    editor.getDomNode().parentElement && editor.getDomNode().parentElement.classList.remove('monaco-mounted');
    editor.getDomNode().remove();
    container.appendChild(editor.getDomNode());
    container.classList.add('monaco-editor-host');
    container.parentElement && container.parentElement.classList.add('monaco-mounted');
  }
  editor.updateOptions(layoutOptions);
  const model = editor.getModel();
  if (model) {
    monaco.editor.setModelLanguage(model, language);
    if (model.getValue() !== String(value || '')) {
      suppressChange = true;
      editor.setValue(String(value || ''));
      suppressChange = false;
    }
  }
  registerCompletion(language);
  editor.layout();
  return editor;
}

function setValue(value, markDirty) {
  if (!editor) return;
  const next = String(value || '');
  if (editor.getValue() !== next) {
    suppressChange = true;
    editor.setValue(next);
    suppressChange = false;
  }
  if (markDirty && handlers.onChange) handlers.onChange(next);
}

self.platformMonacoCompletionItems = [];
self.platformMonaco = {
  mount,
  getEditor: () => editor,
  getValue: () => editor ? editor.getValue() : '',
  setValue,
  getSelectedText: () => {
    if (!editor) return '';
    const selection = editor.getSelection();
    return selection && !selection.isEmpty() ? editor.getModel().getValueInRange(selection) : '';
  },
  getCaretOffset: () => {
    if (!editor) return 0;
    const position = editor.getPosition();
    return position ? editor.getModel().getOffsetAt(position) : 0;
  },
  setCompletionItems: (items) => { self.platformMonacoCompletionItems = Array.isArray(items) ? items : []; },
  focus: () => { if (editor) editor.focus(); }
};
self.dispatchEvent(new Event('platform-monaco-ready'));
