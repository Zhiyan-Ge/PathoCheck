<script setup lang="ts">
import { ref, inject } from 'vue'
import { Folder, HelpCircle, Download, RefreshCw, ScrollText } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLogs } from '../api'

const emit = defineEmits(['open-explorer', 'import-dataset', 'import-model', 'train-model', 'select-image'])

const appState = inject('appState') as any
const currentDataset = appState.currentDataset
const currentModel = appState.currentModel
const currentImage = appState.currentImage

const logDialogVisible = ref(false)
const logs = ref('')
const logLoading = ref(false)

const showAbout = () => {
  ElMessageBox.alert(
    `
    <div class="space-y-4">
      <div class="text-center">
        <h3 class="text-lg font-bold text-[var(--trae-text-active)]">PathoCheck v1.0.3</h3>
        <p class="text-sm opacity-70">病理切片图像质量评估与有效区域筛选平台</p>
      </div>
      <div class="bg-white/5 p-3 rounded text-sm space-y-2 border border-white/10">
        <p><b>核心功能：</b></p>
        <ul class="list-disc ml-5 space-y-1 opacity-80">
          <li>多格式病理切片导入与管理</li>
          <li>基于 RF 模型的图像质量自动评估</li>
          <li>有效组织区域检测与背景筛选</li>
          <li>生成可视化评估报告与导出</li>
        </ul>
        <div class="pt-2 border-t border-white/10 mt-2">
          <p><b>环境信息：</b> Java Spring Boot + Vue 3 + Element Plus</p>
          <p><b>开发者：</b> Taffy团队</p>
        </div>
      </div>
      <p class="text-[10px] text-center opacity-40">© 2026 PathoCheck Project. All rights reserved.</p>
    </div>
    `,
    '关于 PathoCheck',
    { 
      dangerouslyUseHTMLString: true,
      confirmButtonText: '确定',
      customClass: 'about-dialog'
    }
  )
}

const fetchLogs = async () => {
  logLoading.value = true
  try {
    logs.value = await getLogs()
  } catch (e: any) {
    ElMessage.error('获取日志失败: ' + e.message)
  } finally {
    logLoading.value = false
  }
}

const showLogs = async () => {
  await fetchLogs()
  logDialogVisible.value = true
}

const downloadLogs = () => {
  const blob = new Blob([logs.value], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `pathocheck_${new Date().getTime()}.log`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('日志已准备下载')
}
</script>

<template>
  <div class="h-10 flex items-center bg-[var(--trae-toolbar-bg)] border-b border-[var(--trae-border)] px-4 select-none">
    <!-- Menu -->
    <div class="flex space-x-2">
      <el-dropdown trigger="click">
        <span class="text-sm px-3 py-1 cursor-pointer hover:bg-white/10 rounded flex items-center outline-none text-[var(--trae-text)]">
          <Folder class="w-4 h-4 mr-2" />
          File
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$emit('open-explorer')">打开资源管理器</el-dropdown-item>
            <el-dropdown-item divided @click="$emit('import-dataset')">导入数据集 (Load Dataset)</el-dropdown-item>
            <el-dropdown-item @click="$emit('import-model')">导入模型 (Load Model)</el-dropdown-item>
            <el-dropdown-item @click="$emit('train-model')">训练模型 (Train Model)</el-dropdown-item>
            <el-dropdown-item @click="$emit('select-image')">选择图像评估 (Select Image)</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-dropdown trigger="click">
        <span class="text-sm px-3 py-1 cursor-pointer hover:bg-white/10 rounded flex items-center outline-none text-[var(--trae-text)]">
          <ScrollText class="w-4 h-4 mr-2" />
          Logs
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="showLogs">Log Viewer</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-dropdown trigger="click">
        <span class="text-sm px-3 py-1 cursor-pointer hover:bg-white/10 rounded flex items-center outline-none text-[var(--trae-text)]">
          <HelpCircle class="w-4 h-4 mr-2" />
          Help
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="showAbout">About PathoCheck</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <div class="flex-1"></div>

    <!-- Status -->
    <div class="flex space-x-6 text-xs text-[var(--trae-text)]">
      <div>
        <span class="opacity-60">Dataset:</span>
        <span class="ml-1 font-medium text-[var(--trae-text-active)]">{{ currentDataset?.datasetName || '—' }}</span>
      </div>
      <div>
        <span class="opacity-60">Model:</span>
        <span class="ml-1 font-medium text-[var(--trae-text-active)]">{{ currentModel?.modelName || '—' }}</span>
      </div>
      <div>
        <span class="opacity-60">Image:</span>
        <span class="ml-1 font-medium text-[var(--trae-text-active)]">{{ currentImage?.imageName || '—' }}</span>
      </div>
    </div>

    <!-- Log Viewer Dialog -->
    <el-dialog
      v-model="logDialogVisible"
      title="系统日志 (System Logs)"
      width="800px"
      destroy-on-close
      append-to-body
    >
      <div class="relative">
        <!-- Loading Progress Bar -->
        <div v-if="logLoading" class="absolute top-0 left-0 right-0 z-50">
          <el-progress :percentage="100" :indeterminate="true" :show-text="false" :stroke-width="2" />
        </div>

        <div class="flex justify-end space-x-2 mb-2">
          <el-button size="small" @click="fetchLogs">
            <template #icon><RefreshCw class="w-3 h-3 mr-1" /></template>刷新
          </el-button>
          <el-button size="small" type="primary" @click="downloadLogs">
            <template #icon><Download class="w-3 h-3 mr-1" /></template>保存到本地
          </el-button>
        </div>
        <div class="bg-black/40 p-4 rounded border border-white/10 font-mono text-xs overflow-auto max-h-[500px] whitespace-pre-wrap text-gray-300">
          {{ logs || '暂无日志内容' }}
        </div>
        <div class="mt-4 text-[10px] text-gray-500 italic">
          注：系统日志在后端自动滚动更新，旧日志会被覆盖。若需永久保存，请点击上方“保存到本地”按钮。
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style>
.about-dialog {
  background-color: var(--trae-bg) !important;
  border: 1px solid var(--trae-border) !important;
}
.about-dialog .el-message-box__title {
  color: var(--trae-text-active) !important;
}
</style>
