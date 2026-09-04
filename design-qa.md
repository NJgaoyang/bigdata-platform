# Synchronization wizard, cluster configuration, and audit record visual QA

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
