<script setup lang="ts">
import { inject, ref } from 'vue'
import { Folder, HelpCircle } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLogs } from '../api'

const emit = defineEmits(['open-explorer', 'import-dataset', 'import-model', 'train-model', 'select-image'])

const appState = inject('appState') as any
const currentDataset = appState.currentDataset
const currentModel = appState.currentModel
const currentImage = appState.currentImage

const showAbout = () => {
  ElMessageBox.alert(
    '<p>软件名称：PathoCheck</p><p>用途：病理切片图像质量评估与有效区域筛选</p>',
    '关于 PathoCheck',
    { dangerouslyUseHTMLString: true }
  )
}

const showLogs = async () => {
  try {
    const logs = await getLogs()
    ElMessageBox.alert(
      `<pre class="text-xs text-left overflow-auto max-h-96">${logs}</pre>`,
      '系统日志',
      { dangerouslyUseHTMLString: true, customClass: 'w-[600px]' }
    )
  } catch (e: any) {
    ElMessage.error('获取日志失败: ' + e.message)
  }
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
          <HelpCircle class="w-4 h-4 mr-2" />
          Help
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="showLogs">Log Viewer</el-dropdown-item>
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
  </div>
</template>
