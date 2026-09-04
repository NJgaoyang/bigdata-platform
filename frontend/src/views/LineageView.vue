<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { Graph } from "@antv/x6";
import { platformApi } from "../api";

type LineageRecord = {
  id?: number;
  sourceTable: string;
  targetTable: string;
  relationType?: string;
};
const search = ref("dws_trade_province_1d");
const level = ref("表级");
const grain = ref("字段级");
const env = ref("生产环境");
const records = ref<LineageRecord[]>([]);
const canvas = ref<HTMLElement>();
const fullscreen = ref(false);
let graph: Graph | undefined;

async function loadLineage() {
  try {
    const response = await platformApi.lineage();
    const all = (response.data.data || []) as LineageRecord[];
    records.value = search.value.trim()
      ? all.filter(
          (item) =>
            item.sourceTable
              ?.toLowerCase()
              .includes(search.value.toLowerCase()) ||
            item.targetTable
              ?.toLowerCase()
              .includes(search.value.toLowerCase()),
        )
      : all;
    renderGraph(records.value);
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
}
onMounted(loadLineage);
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
          <label>目标对象</label><input v-model="search" />
        </div>
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
        <div class="form-item">
          <label>运行环境</label
          ><select v-model="env">
            <option>生产环境</option>
            <option>开发环境</option>
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
          <span class="muted">数据血缘关系图</span
          ><span class="muted">共 {{ records.length }} 条关系</span>
          <button v-if="fullscreen" class="btn-default" @click="toggleFullscreen">退出全屏</button>
        </div>
        <div ref="canvas" class="canvas-area"></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.canvas-area {
  min-height: 650px;
  height: calc(100vh - 190px);
  background: #f8fbff;
  overflow: hidden;
}
.lineage-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 120;
  background: #fff;
}
.lineage-fullscreen .canvas-area { height: 100vh; }
</style>
