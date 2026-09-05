# SQL workbench resize and fullscreen visual QA

## Current comparison target

- Source visual truth: `/root/.codex/attachments/21a0a8c0-8963-4601-924f-c11db3970f3c/codex-clipboard-fe236aa8-7912-41a1-91aa-721233cdab69.png`.
- Autocomplete defect reference: `/root/.codex/attachments/2e1c44c2-2201-4f10-a902-48bd653758bb/codex-clipboard-d71ab9ff-922d-4ed8-ae40-27148ab622ef.png` (910 × 181 pixels).
- Collapsed field-panel defect reference: `/root/.codex/attachments/cb3691cd-dbe5-47c2-9771-ebc83f45f215/codex-clipboard-649535b6-ecf8-419a-9f39-e6cce311d228.png` (150 × 887 pixels).
- SQL interaction reference: `/root/.codex/attachments/4aa5ecd5-ca86-495f-aa1e-395005b78f85/codex-clipboard-1e4b2287-e338-47b5-aaad-f8ecc96d623c.png` (1920 × 872 pixels).
- SQL toolbar and field-panel sizing reference: `/root/.codex/attachments/96eb5765-bd93-4b0d-b6b0-ea7a0f827b22/codex-clipboard-0f7c3ee8-8576-4bfe-aef7-33127285be7e.png` (1357 × 187 pixels).
- Nested metadata-field reference: `/root/.codex/attachments/242a3772-0bee-48d9-8f8b-e50b1ee6473d/codex-clipboard-3795c81f-7f0c-47c7-8544-1276998cbdf7.png` (251 × 176 pixels).
- Metadata collapse reference: `/root/.codex/attachments/e35b8c40-640d-4821-b4b5-3722998d2cd5/codex-clipboard-c2594d03-a039-4d61-b921-2d8c23ad8da3.png`.
- Direct nested-field reference: `/root/.codex/attachments/60b0ff8e-f03a-4ef8-862a-03f3ca397a2f/codex-clipboard-f3cd6df1-42dc-44df-ba10-8b1df157d4e0.png`.
- Truncated autocomplete reference: `/root/.codex/attachments/30d2ee4c-2f03-4087-bde6-839d07809605/codex-clipboard-c020f3c4-9b5d-465d-8d18-0cad57607707.png` (901 × 148 pixels).
- Persistently truncated autocomplete reference: `/root/.codex/attachments/a20e431d-a977-4e65-b775-5c5897e70982/codex-clipboard-562956f2-7f96-4879-858f-8c6ccdc48dd9.png` (625 × 124 pixels).
- Data-source picker and autocomplete reference: `/root/.codex/attachments/2513fd49-35a7-409a-84ec-639ec07edfd7/codex-clipboard-026de3b9-0c5b-4fe1-b9b4-fe70bdbc75d1.png` (932 × 140 pixels).
- Source pixels: 1920 × 872; desktop CSS target: 1920 × 872 at device scale factor 1.
- Implementation route: `http://127.0.0.1:5173/explore`.
- State: SQL workbench with metadata tree, editor, result panel, and field list visible.
- Implementation screenshot: unavailable because this session does not expose an inspectable browser capture surface.

## Current implementation evidence

- The metadata/editor boundary is horizontally draggable from 180px to 380px.
- The result boundary is vertically draggable while preserving at least 120px for both work areas.
- The field list defaults to 252px and collapses to a 32px right-docked rail.
- The data-source selector is in the left metadata panel; the editor toolbar no longer displays “数据连接”, and fullscreen remains at the far right.
- The SQL usage hint is an editor empty state and disappears as soon as the active SQL tab contains text.
- Result tables use independent horizontal and vertical overflow.
- The global `.highlight` collision with Monaco's completion-label markup is isolated, and completion candidates now distinguish databases, tables, SQL keywords, and functions.
- The result surface has a restrained left/right/bottom boundary, lower corner radius, and scrollbars matching the existing neutral palette.
- Query tabs can be renamed, saved through the real development-file API, and saved with Ctrl/Cmd+S; the editor can be collapsed and reopened from its persistent toolbar control.
- The field-panel reopen control now stays completely inside its 32px collapsed rail with an explicit pointer target and stacking context.
- The message tab is removed; table and field rows support double-click insertion at the current Monaco cursor, and the collapsed field panel has both a rail control and a toolbar fallback.
- Rename is now available only by double-clicking the tab label. The right field panel is resizable from 220px to 520px when fields exist, exposes horizontal overflow for long types/comments, and stays fixed at 252px when empty.
- The standalone right field panel is removed. Real metadata columns expand directly beneath each table in the left tree; table and field double-click insertion remains available.
- Database rows now collapse or expand all of their visible tables. Fields are listed directly beneath an expanded table without an intermediate “字段” row, and hovering a field exposes its real Chinese comment (or an explicit empty-comment fallback).
- The SQL result area no longer shows the initial “运行查询后显示真实结果” placeholder, and the lineage analysis sidebar no longer duplicates the target-object input.
- All row-, label-, line-height-, highlight-, and overflow-level overrides have been removed from Monaco autocomplete. Monaco now fully owns its measured row and glyph layout; only the neutral border, radius, and shadow remain project-styled.
- Monaco completion widgets now use a fixed overflow container, and the global forced sizing of Monaco's internal `.overflow-guard` has been removed so suggestions are not clipped by the editor surface.
- Switching data sources clears stale field caches and loads every accessible database and its tables; one inaccessible system database no longer prevents the remaining tree from rendering.
- Vue type checking, Vite production build, Maven packaging, and HTTP checks on ports 5173 and 8080 passed.

## Current fidelity surfaces

- Fonts and typography: existing Microsoft YaHei/PingFang project stack and 12–14px workbench hierarchy retained; browser rasterization not captured.
- Spacing and layout rhythm: resizers are 5–7px, the data-source picker is grouped with metadata search, and fullscreen remains at the toolbar edge; browser comparison unavailable.
- Colors and visual tokens: existing neutral borders, white surfaces, blue hover/focus token, and green connection state retained.
- Image and icon fidelity: no raster imagery is present; new controls use the existing Element Plus icon package.
- Copy and content: the requested Chinese hint, data-source picker, fullscreen labels, and resize tooltips are present.

## Current findings

- [P2] Browser-rendered comparison is unavailable.
  Location: SQL workbench at 1920 × 872.
  Evidence: the supplied source image is available, but there is no implementation screenshot from an inspectable browser surface.
  Impact: exact final proportions and drag interaction feel cannot be certified visually in this session.
  Fix: open the running route in an interactive browser, test the three resize/collapse/fullscreen interactions, and capture the same viewport for a side-by-side comparison.

## Current comparison history

- Initial source review identified an oversized right panel, top-bar connection control, static result height, and no fullscreen control.
- Source changes implement the requested proportions and interactions. A post-fix browser capture remains unavailable, so no visual pass can be recorded.

final result: blocked

---

## Previous QA record: synchronization wizard, cluster configuration, and audit record

## Comparison target

- Source visual truth:
  - `/root/.codex/attachments/1119bfcf-b9da-486c-a869-e24f9d79f26a/codex-clipboard-98fe7ee2-cf64-49b9-9c91-18b7919af3e3.png` — create-task basic configuration.
  - `/root/.codex/attachments/7f3bf79b-56ae-4df6-b650-f8f939147ab3/codex-clipboard-8699e0c1-f81f-46b4-b350-d9a31694d96d.png` — table-selection step.
  - `/root/.codex/attachments/61801c92-5396-4162-8fd3-844419e635ab/codex-clipboard-3fb75024-1717-4b58-824e-7ef600564253.png` — preview-and-confirm step.
  - `/root/.codex/attachments/60d060f5-59b7-46ed-8fed-336fc83cb8d4/codex-clipboard-a1e04a65-7832-4480-b43d-d8046b975cf1.png` — cluster configuration list.
  - `/root/.codex/attachments/0724d94d-ce42-4fd7-90c3-bcfd66c9d921/codex-clipboard-833c537c-254f-4eb3-b54c-0e0e87e6d6d6.png` — cluster edit dialog.
  - `/root/.codex/attachments/1d58f9f1-a579-47a1-b5f3-768dea26b337/codex-clipboard-d81dc1e0-66fe-4609-8d0f-06a9f804497f.png` — Chinese audit record table, 1656 × 352 pixels.
  - `/root/.codex/attachments/73724178-29ec-4541-a871-f8e8bdc5b925/codex-clipboard-0caf3e6e-0e90-4f9d-a29d-f4de0ed78fec.png` — audit search area, 1667 × 286 pixels.
- Implementation routes: `http://localhost:5173/integration` and `http://localhost:5173/settings`.
- Intended states: desktop three-step MySQL-to-StarRocks task creation and the System Settings cluster configuration section.
- Implementation screenshot: unavailable. This session has no in-app browser capture surface.

## Implemented source-aligned changes

- Replaced the synchronization creation modal with basic configuration, table selection, and preview confirmation steps.
- The table list is fetched only from the selected MySQL synchronization source; tables can be selected in bulk or individually.
- Added a live SeaTunnel configuration preview, copy action, and multi-table task creation.
- Added cluster configuration below Audit Log in System Settings, with persisted create, edit, delete, detail, individual check, and check-all actions.
- The wizard uses configured MySQL, StarRocks, and SeaTunnel cluster records instead of display-only sample data.
- Localized audit actions, resources, and request details into business-facing Chinese; added live search and removed table cell right borders in the audit view.

## Required fidelity surfaces

- Fonts and typography: the project Chinese font stack and existing hierarchy are retained; browser comparison unavailable.
- Spacing and layout rhythm: three-step progress layout, form grouping, table rows, toolbar, and modal grid are implemented; browser comparison unavailable.
- Colors and visual tokens: the existing neutral project palette, blue primary action, status colors, borders, and radii are retained; browser comparison unavailable.
- Image and icon fidelity: the supplied views use standard UI iconography; existing Element Plus icons and controls are used. No non-standard image assets are present.
- Copy and content: source/target names, tables, cluster state, and generated configuration are API-backed; no example rows are injected.

## Findings

- [P2] Browser-rendered comparison is unavailable.
  Location: all five reference states.
  Evidence: source images are available, but no browser screenshot can be captured in this session.
  Impact: exact desktop spacing, font rasterization, and step/modal dimensions are not visually verified.
  Fix: capture each state at the reference viewport, compare the source and implementation side by side, and correct any visible P1/P2 differences.

## Implementation Checklist

- [x] Frontend production build passed.
- [x] Backend unit tests passed.
- [x] Database migration V10 applied successfully.
- [x] Local backend health endpoint reports UP in real mode.
- [x] Cluster list endpoint returns persisted data successfully.
- [x] Local frontend responds on port 5173.
- [ ] Capture and compare the five browser-rendered reference states.

## Follow-up Polish

- Complete the blocked screenshot comparison before claiming pixel-level fidelity.

final result: blocked
