<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Upload, Trash2, Image as ImageIcon, Box, Database, Play, Loader2 } from 'lucide-vue-next'
import { listDatasets, deleteDataset, loadDataset, listModels, deleteModel, loadModel, selectImage, trainModel } from '../api'
import type { Dataset, Model } from '../types'

const props = defineProps<{
  visible: boolean;
  selectedDataset: Dataset | null;
  selectedModel: Model | null;
}>()

const emit = defineEmits(['update:visible', 'update:selectedDataset', 'update:selectedModel', 'image-selected'])

const datasets = ref<Dataset[]>([])
const models = ref<Model[]>([])
const loading = ref(false)
const activeTab = ref('datasets')
const uploadProgress = ref(0)
const isUploading = ref(false)
const trainingProgress = ref(0)
const isTraining = ref(false)

const fetchData = async () => {
  loading.value = true
  // 预先清空列表，增强刷新感 (Clear lists to show refresh state)
  datasets.value = []
  models.value = []
  try {
    const [d, m] = await Promise.all([listDatasets(), listModels()])
    datasets.value = [...d]
    models.value = [...m]
  } catch (e: any) {
    ElMessage.error('Failed to load data: ' + e.message)
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val) fetchData()
})

const close = () => {
  emit('update:visible', false)
}

// Handlers
const handleImportDataset = async () => {
  // Mock file input
  const input = document.createElement('input')
  input.type = 'file'
  input.webkitdirectory = true
  input.multiple = true
  input.onchange = async (e: any) => {
    const files = Array.from(e.target.files) as File[]
    if (!files.length) return
    
    // the first file's path can determine the dataset name (folder name)
    const folderPath = (files[0] as any).webkitRelativePath
    const datasetName = folderPath ? folderPath.split('/')[0] : 'Unknown_Dataset'
    const relativePaths = files.map((f: any) => f.webkitRelativePath)

    isUploading.value = true
    uploadProgress.value = 0
    ElNotification.info({
      title: '正在导入',
      message: `正在导入数据集 "${datasetName}"，请稍候...`,
      duration: 0,
      id: 'import-notify'
    })

    try {
      await loadDataset(datasetName, files, relativePaths, (p) => {
        uploadProgress.value = p
      })
      ElNotification.close('import-notify')
      ElMessage.success({
        message: '数据集导入成功 (Dataset imported successfully)',
        duration: 3000,
        showClose: true
      })
      // 延迟一秒刷新，确保后端文件索引已更新 (Delay to ensure backend indexing)
      setTimeout(async () => {
        await fetchData()
      }, 1000)
    } catch (err: any) {
      ElNotification.close('import-notify')
      ElMessage.error('导入失败: ' + err.message)
    } finally {
      isUploading.value = false
    }
  }
  input.click()
}

const handleDeleteDataset = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除此数据集吗？ (Are you sure you want to delete this dataset?)', '警告', { type: 'warning' })
    loading.value = true
    await deleteDataset(id)
    if (props.selectedDataset?.id === id) emit('update:selectedDataset', null)
    ElMessage.success('已删除 (Deleted)')
    await fetchData()
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error('删除失败: ' + err.message)
  } finally { loading.value = false }
}

const handleImportModel = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.model'
  input.onchange = async (e: any) => {
    const file = e.target.files[0]
    if (!file) return
    isUploading.value = true
    uploadProgress.value = 0
    ElNotification.info({
      title: '正在导入',
      message: `正在导入模型 "${file.name}"...`,
      duration: 0,
      id: 'import-model-notify'
    })

    try {
      await loadModel(file.name.replace('.model', ''), file, (p) => {
        uploadProgress.value = p
      })
      ElNotification.close('import-model-notify')
      ElMessage.success({
        message: '模型导入成功 (Model imported successfully)',
        duration: 3000,
        showClose: true
      })
      // 延迟一秒刷新，确保后端文件索引已更新 (Delay to ensure backend indexing)
      setTimeout(async () => {
        await fetchData()
      }, 1000)
    } catch (err: any) {
      ElNotification.close('import-model-notify')
      ElMessage.error('导入失败: ' + err.message)
    } finally {
      isUploading.value = false
    }
  }
  input.click()
}

const handleTrainModel = async (dataset?: any) => {
  const targetDataset = dataset && dataset.id ? dataset : props.selectedDataset;
  if (!targetDataset) {
    ElMessage.warning('请先在资源管理器中选择一个数据集 (Please select a dataset first)');
    return;
  }
  
  try {
    const { value: modelName } = await ElMessageBox.prompt('请输入新模型名称 (Please enter a name for the new model)', '训练模型', {
      confirmButtonText: '开始训练',
      cancelButtonText: '取消',
      inputValue: `RF-${new Date().getTime()}`
    })
    
    loading.value = true
    isTraining.value = true
    trainingProgress.value = 0
    const trainTimer = setInterval(() => {
      if (trainingProgress.value < 95) {
        trainingProgress.value += Math.random() * 5
      }
    }, 1000)

    ElNotification.info({
      title: '训练开始',
      message: `正在使用数据集 "${targetDataset.datasetName}" 训练模型 "${modelName}"，请稍候...`,
      duration: 0,
      id: 'train-notify'
    })

    await trainModel(targetDataset.id, modelName)
    clearInterval(trainTimer)
    trainingProgress.value = 100
    
    ElNotification.close('train-notify')
    ElNotification.success({
      title: '训练完成',
      message: `模型 "${modelName}" 训练成功！`,
    })
    await fetchData()
  } catch (err: any) {
    ElNotification.close('train-notify')
    if (err !== 'cancel') ElMessage.error('训练失败: ' + err.message)
  } finally {
    loading.value = false
    isTraining.value = false
    trainingProgress.value = 0
  }
}

const handleDeleteModel = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除此模型吗？ (Are you sure you want to delete this model?)', '警告', { type: 'warning' })
    loading.value = true
    await deleteModel(id)
    if (props.selectedModel?.id === id) emit('update:selectedModel', null)
    ElMessage.success('已删除 (Deleted)')
    // 强制清除本地列表并重新获取
    models.value = models.value.filter(m => m.id !== id)
    await fetchData()
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error('删除失败: ' + err.message)
  } finally { loading.value = false }
}

const handleSelectImage = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*,.tif,.tiff'
  input.onchange = async (e: any) => {
    const file = e.target.files[0]
    if (!file) return
    loading.value = true
    try {
      const imageInfo = await selectImage(file)
      const previewUrl = `/api/images/${imageInfo.imageId}/preview`
      emit('image-selected', imageInfo, previewUrl)
      close()
    } catch (err: any) {
      ElMessage.error('Image selection failed: ' + err.message)
    } finally {
      loading.value = false
    }
  }
  input.click()
}
defineExpose({
  handleImportDataset,
  handleImportModel,
  handleTrainModel,
  handleSelectImage
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="close"
    title="资源管理器"
    width="800px"
    custom-class="explorer-dialog"
    destroy-on-close
  >
    <div class="flex h-[500px] relative">
      <!-- Upload/Training Progress Overlay (Linear at bottom) -->
      <div v-if="isUploading || isTraining" class="absolute inset-0 z-50 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="flex flex-col items-center max-w-md w-full px-10">
          <div class="mb-4 text-white text-xl font-medium">
            {{ isUploading ? (uploadProgress < 100 ? '正在同步资源...' : '服务器处理中...') : '正在训练模型...' }}
          </div>
          
          <div class="w-full bg-white/10 rounded-full h-2 relative overflow-hidden">
            <div 
              class="absolute top-0 left-0 h-full bg-white transition-all duration-300 ease-out shadow-[0_0_10px_rgba(255,255,255,0.5)]"
              :style="{ width: `${isUploading ? uploadProgress : trainingProgress}%` }"
            ></div>
          </div>
          
          <div class="mt-3 text-white font-mono text-lg">
            {{ Math.round(isUploading ? uploadProgress : trainingProgress) }}%
          </div>
          
          <div class="mt-6 text-gray-400 text-sm animate-pulse">
            {{ isUploading ? '正在通过 TCP 协议传输切片数据' : '正在计算 RF 算法特征向量' }}
          </div>
          <div class="mt-1 text-gray-500 text-xs">请勿关闭当前资源管理器窗口</div>
        </div>
      </div>

      <!-- Loading Progress Bar (for non-upload/train tasks) -->
      <div v-if="loading && !isUploading" class="absolute top-0 left-0 right-0 z-50">
        <el-progress :percentage="100" :indeterminate="true" :show-text="false" :stroke-width="2" />
      </div>

      <!-- Sidebar -->
      <div class="w-48 border-r border-[var(--trae-border)] flex flex-col p-2 space-y-1">
        <div 
          class="px-3 py-2 rounded cursor-pointer flex items-center"
          :class="activeTab === 'datasets' ? 'bg-[var(--trae-accent)] text-white' : 'hover:bg-white/5'"
          @click="activeTab = 'datasets'"
        >
          <Database class="w-4 h-4 mr-2" /> 数据集
        </div>
        <div 
          class="px-3 py-2 rounded cursor-pointer flex items-center"
          :class="activeTab === 'models' ? 'bg-[var(--trae-accent)] text-white' : 'hover:bg-white/5'"
          @click="activeTab = 'models'"
        >
          <Box class="w-4 h-4 mr-2" /> 模型
        </div>
        
        <div class="flex-1"></div>
        <el-button type="primary" plain class="w-full justify-start" @click="handleSelectImage">
          <template #icon><ImageIcon /></template>
          选择图像评估
        </el-button>
      </div>

      <!-- Main Content -->
      <div class="flex-1 p-4 overflow-auto">
        <template v-if="activeTab === 'datasets'">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium">数据集列表</h3>
            <el-button type="primary" size="small" @click="handleImportDataset">
              <template #icon><Upload /></template>
              导入数据集
            </el-button>
          </div>
          
          <div v-if="datasets.length === 0" class="text-center text-gray-500 py-10">
            暂无数据集
          </div>
          <div v-else class="space-y-3">
            <div 
              v-for="ds in datasets" 
              :key="ds.id"
              class="border border-[var(--trae-border)] p-3 rounded-lg flex items-center justify-between hover:border-gray-500 transition-colors cursor-pointer"
              :class="{ 'border-[var(--trae-accent)] bg-[var(--trae-accent)]/10': selectedDataset?.id === ds.id }"
              @click="emit('update:selectedDataset', ds)"
            >
              <div>
                <div class="font-medium text-base text-[var(--trae-text-active)]">{{ ds.datasetName }}</div>
                <div class="text-xs text-gray-400 mt-1">
                  总数: {{ ds.imageCount }} | 有效: {{ ds.validCount }} | 无效: {{ ds.invalidCount }}
                </div>
              </div>
              <div class="flex space-x-2">
                <el-button size="small" type="success" plain @click.stop="handleTrainModel(ds)" title="训练新模型">
                  <template #icon><Play /></template>
                </el-button>
                <el-button size="small" type="danger" plain @click.stop="handleDeleteDataset(ds.id)" title="删除">
                  <template #icon><Trash2 /></template>
                </el-button>
              </div>
            </div>
          </div>
        </template>

        <template v-if="activeTab === 'models'">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium">模型列表</h3>
            <el-button type="primary" size="small" @click="handleImportModel">
              <template #icon><Upload /></template>
              导入模型
            </el-button>
          </div>
          
          <div v-if="models.length === 0" class="text-center text-gray-500 py-10">
            暂无模型
          </div>
          <div v-else class="space-y-3">
            <div 
              v-for="m in models" 
              :key="m.id"
              class="border border-[var(--trae-border)] p-3 rounded-lg flex items-center justify-between hover:border-gray-500 transition-colors cursor-pointer"
              :class="{ 'border-[var(--trae-accent)] bg-[var(--trae-accent)]/10': selectedModel?.id === m.id }"
              @click="emit('update:selectedModel', m)"
            >
              <div>
                <div class="font-medium text-base text-[var(--trae-text-active)]">{{ m.modelName }}</div>
                <div class="text-xs text-gray-400 mt-1">
                  Acc: {{ m.accuracy == null ? '—' : `${(m.accuracy * 100).toFixed(1)}%` }}
                  |
                  F1: {{ m.f1Score == null ? '—' : `${(m.f1Score * 100).toFixed(1)}%` }}
                  |
                  创建于: {{ new Date(m.createdTime).toLocaleDateString() }}
                </div>
              </div>
              <div>
                <el-button size="small" type="danger" plain @click.stop="handleDeleteModel(m.id)" title="删除">
                  <template #icon><Trash2 /></template>
                </el-button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </el-dialog>
</template>

<style>
.explorer-dialog .el-dialog__body {
  padding: 0;
}
</style>
