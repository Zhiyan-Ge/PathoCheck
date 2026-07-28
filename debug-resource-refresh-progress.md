# Debug Session: resource-refresh-progress
- **Status**: [OPEN]
- **Issue**: 1) 模型训练时没有可视化进度条；2) 上传数据集后资源管理器不能实时显示新数据；3) 上传完成后提示框不自动消失。
- **Debug Server**: http://127.0.0.1:7777/event
- **Log File**: .dbg/trae-debug-log-resource-refresh-progress.ndjson

## Reproduction Steps
1. 打开资源管理器。
2. 上传一个数据集，观察上传提示、资源管理器列表是否实时出现新数据集。
3. 选择一个数据集开始训练模型，观察训练提示框是否显示底部白色条状进度条和百分比。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | 训练状态没有正确驱动 UI，训练提示分支未渲染 | High | Low | Pending |
| B | 上传接口完成后列表接口第一次返回旧数据，导致首次刷新拿不到新数据 | High | Medium | Pending |
| C | 上传结束后通知与遮罩状态没有一起关闭 | Medium | Low | Pending |
| D | 列表已刷新但响应式更新未触发界面重绘 | Medium | Medium | Pending |

## Log Evidence
- 已在 `frontend/src/components/Explorer.vue` 中为上传开始、上传结束、延迟刷新、训练开始、训练结束添加埋点。
- 已在 `frontend/src/api/index.ts` 中为上传进度事件、列表接口返回、训练接口请求前后添加埋点。

## Verification Conclusion
[Pending]
