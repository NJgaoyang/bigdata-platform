<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import { ElMessage } from "element-plus";
import { Lock, Unlock } from "@element-plus/icons-vue";
import { Graph } from "@antv/x6";
import { platformApi } from "../api";

type LineageRecord = {
  id?: number;
  sourceTable: string;
  targetTable: string;
  relationType?: string;
};
const search = ref("");
const level = ref("表级");
const grain = ref("字段级");
const records = ref<LineageRecord[]>([]);
const searched = ref(false);
const canvas = ref<HTMLElement>();
const fullscreen = ref(false);
const selectedChainIds = ref<Set<string>>(new Set());
const lockedNodeIds = ref<Set<string>>(new Set());
const selectedChainLocked = computed(
  () => selectedChainIds.value.size > 0
    && [...selectedChainIds.value].every((id) => lockedNodeIds.value.has(id)),
);
let graph: Graph | undefined;

function resetChainState() {
  selectedChainIds.value = new Set();
  lockedNodeIds.value = new Set();
}

async function loadLineage() {
  const tableName = search.value.trim();
  if (!tableName) {
    searched.value = false;
    records.value = [];
    resetChainState();
    graph?.dispose();
    graph = undefined;
    ElMessage.warning("请先输入要分析的表名");
    return;
  }
  try {
    const response = await platformApi.lineageByTable(tableName);
    records.value = (response.data.data || []) as LineageRecord[];
    resetChainState();
    searched.value = true;
    if (records.value.length) renderGraph(records.value);
    else {
      graph?.dispose();
      graph = undefined;
    }
    ElMessage.success(
      records.value.length
        ? `已加载 ${records.value.length} 条血缘关系`
        : "当前对象暂无已保存血缘",
    );
  } catch {
    ElMessage.error("血缘查询失败，请确认后端服务可用");
  }
}
function queryLineage() {
  loadLineage();
}
function findConnectedChain(anchorId: string) {
  const adjacent = new Map<string, Set<string>>();
  records.value.forEach(({ sourceTable, targetTable }) => {
    if (!adjacent.has(sourceTable)) adjacent.set(sourceTable, new Set());
    if (!adjacent.has(targetTable)) adjacent.set(targetTable, new Set());
    adjacent.get(sourceTable)!.add(targetTable);
    adjacent.get(targetTable)!.add(sourceTable);
  });
  const found = new Set<string>();
  const pending = [anchorId];
  while (pending.length) {
    const current = pending.shift()!;
    if (found.has(current)) continue;
    found.add(current);
    adjacent.get(current)?.forEach((next) => {
      if (!found.has(next)) pending.push(next);
    });
  }
  return found;
}
function refreshChainStyles() {
  if (!graph) return;
  graph.getNodes().forEach((node) => {
    const selected = selectedChainIds.value.has(node.id);
    const locked = lockedNodeIds.value.has(node.id);
    node.attr({
      body: {
        fill: locked ? "#eef5ff" : "#fff",
        stroke: selected ? "#1677ff" : "#9db0c8",
        strokeWidth: selected ? 2 : 1,
      },
      label: { fill: locked ? "#1268db" : "#344054" },
    });
  });
  graph.getEdges().forEach((edge) => {
    const selected = selectedChainIds.value.has(edge.getSourceCellId())
      && selectedChainIds.value.has(edge.getTargetCellId());
    edge.attr("line/stroke", selected ? "#1677ff" : "#9db0c8");
    edge.attr("line/strokeWidth", selected ? 2 : 1);
  });
}
function selectChain(anchorId: string) {
  selectedChainIds.value = findConnectedChain(anchorId);
  refreshChainStyles();
}
function clearChainSelection() {
  selectedChainIds.value = new Set();
  refreshChainStyles();
}
function toggleSelectedChainLock() {
  if (!selectedChainIds.value.size) {
    ElMessage.warning("请先点击图中的节点选择要锁定的链路");
    return;
  }
  const next = new Set(lockedNodeIds.value);
  if (selectedChainLocked.value) selectedChainIds.value.forEach((id) => next.delete(id));
  else selectedChainIds.value.forEach((id) => next.add(id));
  lockedNodeIds.value = next;
  refreshChainStyles();
  ElMessage.success(selectedChainLocked.value ? "当前链路已锁定" : "当前链路已解锁");
}
async function toggleFullscreen() {
  fullscreen.value = !fullscreen.value;
  await nextTick();
  graph?.resize(canvas.value?.clientWidth || 1000, canvas.value?.clientHeight || 650);
  graph?.centerContent();
}
function renderGraph(items: LineageRecord[]) {
  if (!canvas.value) return;
  graph?.dispose();
  graph = new Graph({
    container: canvas.value,
    grid: { size: 10, visible: true },
    panning: true,
    mousewheel: { enabled: true, modifiers: ["ctrl"] },
    interacting(cellView) {
      return { nodeMovable: !lockedNodeIds.value.has(cellView.cell.id) };
    },
  });
  const names = [
    ...new Set(items.flatMap((item) => [item.sourceTable, item.targetTable])),
  ];
  const columns = Math.max(1, Math.ceil(Math.sqrt(names.length)));
  const positions = new Map<string, { x: number; y: number }>();
  names.forEach((name, index) =>
    positions.set(name, {
      x: 45 + (index % columns) * 280,
      y: 80 + Math.floor(index / columns) * 130,
    }),
  );
  names.forEach((name) => {
    const position = positions.get(name)!;
    graph!.addNode({
      id: name,
      shape: "rect",
      x: position.x,
      y: position.y,
      width: 220,
      height: 68,
      label: name,
      attrs: {
        body: { fill: "#fff", stroke: "#9db0c8", rx: 7, ry: 7 },
        label: { fill: "#344054", fontSize: 12 },
      },
    });
  });
  items.forEach((item) =>
    graph!.addEdge({
      source: item.sourceTable,
      target: item.targetTable,
      router: "manhattan",
      connector: "rounded",
      attrs: { line: { stroke: "#9db0c8", targetMarker: "classic" } },
    }),
  );
  graph.on("node:click", ({ node }) => selectChain(node.id));
  graph.on("blank:click", clearChainSelection);
}
onBeforeUnmount(() => graph?.dispose());
</script>

<template>
  <section class="page">
    <div class="module-bar">
      <div class="module-title">
        数据血缘 <span class="crumb">/ 表级血缘</span>
      </div>
      <div class="module-actions">
        <div class="search-box">
          <span>⌕</span
          ><input
            v-model="search"
            placeholder="搜索表名或字段..."
            @keyup.enter="queryLineage"
          />
        </div>
        <button class="btn-primary" @click="queryLineage">查询血缘</button
        ><button class="btn-default" @click="toggleFullscreen">
          {{ fullscreen ? "退出全屏" : "⛶" }}
        </button>
      </div>
    </div>
    <div class="lineage-wrap" :class="{ 'lineage-fullscreen': fullscreen }">
      <aside class="lineage-side">
        <div class="side-title">血缘分析</div>
        <div class="form-item">
          <label>血缘层级</label
          ><select v-model="level">
            <option>表级</option>
            <option>库级</option>
          </select>
        </div>
        <div class="form-item">
          <label>关系粒度</label
          ><select v-model="grain">
            <option>字段级</option>
            <option>表级</option>
          </select>
        </div>
        <button class="btn-primary full" @click="queryLineage">开始分析</button>
        <div class="legend">
          <div><i class="dot source"></i>上游表</div>
          <div><i class="dot target"></i>当前表</div>
          <div><i class="dot downstream"></i>下游表</div>
        </div>
      </aside>
      <div class="lineage-canvas">
        <div class="canvas-toolbar">
          <span class="muted">数据血缘关系图</span>
          <div class="canvas-actions">
            <span class="muted">共 {{ records.length }} 条关系</span>
            <button
              class="btn-default chain-lock-button"
              :class="{ active: selectedChainLocked }"
              :disabled="!selectedChainIds.size"
              :title="selectedChainIds.size ? (selectedChainLocked ? '解锁当前选中链路' : '锁定当前选中链路') : '请先点击图中的节点选择链路'"
              @click="toggleSelectedChainLock"
            >
              <Unlock v-if="selectedChainLocked" />
              <Lock v-else />
              {{ selectedChainLocked ? "解锁链路" : "锁定链路" }}
            </button>
            <button v-if="fullscreen" class="btn-default" @click="toggleFullscreen">退出全屏</button>
          </div>
        </div>
        <div ref="canvas" class="canvas-area">
          <div v-if="!searched" class="lineage-empty">请输入表名后点击“查询血缘”或“开始分析”</div>
          <div v-else-if="!records.length" class="lineage-empty">未找到该表的已保存血缘关系</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.canvas-area {
  position: relative;
  min-height: 650px;
  height: calc(100vh - 190px);
  background: #f8fbff;
  overflow: hidden;
}
.lineage-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #98a2b3;
  font-size: 13px;
}
.lineage-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 120;
  background: #fff;
}
.lineage-fullscreen .canvas-area { height: 100vh; }
.canvas-actions { display:flex; align-items:center; gap:10px; }
.chain-lock-button svg { width:14px; height:14px; }
.chain-lock-button.active { color:#1268db; border-color:#83b5fb; background:#eef5ff; }
.chain-lock-button:disabled { cursor:not-allowed; color:#a8b1bf; border-color:#e3e8ef; background:#f8fafc; }
</style>
