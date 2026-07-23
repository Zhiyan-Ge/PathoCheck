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
<<<<<<< HEAD
const uploadProgress = ref(0)
const isUploading = ref(false)
=======
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254

const fetchData = async () => {
  loading.value = true
  try {
    const [d, m] = await Promise.all([listDatasets(), listModels()])
    datasets.value = d
    models.value = m
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

<<<<<<< HEAD
    isUploading.value = true
    uploadProgress.value = 0
=======
    loading.value = true
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
    ElNotification.info({
      title: '正在导入',
      message: `正在导入数据集 "${datasetName}"，请稍候...`,
      duration: 0,
      id: 'import-notify'
    })

    try {
<<<<<<< HEAD
      await loadDataset(datasetName, files, relativePaths, (p) => {
        uploadProgress.value = p
      })
      ElNotification.close('import-notify')
      ElMessage.success({
        message: '数据集导入成功 (Dataset imported successfully)',
        duration: 3000,
        showClose: true
      })
=======
      await loadDataset(datasetName, files, relativePaths)
      ElNotification.close('import-notify')
      ElMessage.success('数据集导入成功 (Dataset imported successfully)')
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
      await fetchData()
    } catch (err: any) {
      ElNotification.close('import-notify')
      ElMessage.error('导入失败: ' + err.message)
    } finally {
<<<<<<< HEAD
      isUploading.value = false
=======
      loading.value = false
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
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
<<<<<<< HEAD
    isUploading.value = true
    uploadProgress.value = 0
=======
    loading.value = true
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
    ElNotification.info({
      title: '正在导入',
      message: `正在导入模型 "${file.name}"...`,
      duration: 0,
      id: 'import-model-notify'
    })

    try {
<<<<<<< HEAD
      await loadModel(file.name.replace('.model', ''), file, (p) => {
        uploadProgress.value = p
      })
      ElNotification.close('import-model-notify')
      ElMessage.success({
        message: '模型导入成功 (Model imported successfully)',
        duration: 3000,
        showClose: true
      })
=======
      await loadModel(file.name.replace('.model', ''), file)
      ElNotification.close('import-model-notify')
      ElMessage.success('模型导入成功 (Model imported successfully)')
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
      await fetchData()
    } catch (err: any) {
      ElNotification.close('import-model-notify')
      ElMessage.error('导入失败: ' + err.message)
    } finally {
<<<<<<< HEAD
      isUploading.value = false
=======
      loading.value = false
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
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
    ElNotification.info({
      title: '训练开始',
      message: `正在使用数据集 "${targetDataset.datasetName}" 训练模型 "${modelName}"，请稍候...`,
      duration: 0,
      id: 'train-notify'
    })

    await trainModel(targetDataset.id, modelName)
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
<<<<<<< HEAD
    <div class="flex h-[500px] relative">
      <!-- Upload Progress Overlay -->
      <div v-if="isUploading" class="absolute inset-0 z-50 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
        <el-progress type="circle" :percentage="uploadProgress" :stroke-width="8" :width="120">
          <template #default="{ percentage }">
            <div class="flex flex-col items-center">
              <span class="text-2xl font-bold text-white">{{ percentage }}%</span>
              <span class="text-xs text-gray-300">已上传</span>
            </div>
          </template>
        </el-progress>
        <div class="mt-6 text-white text-lg font-medium">
          {{ uploadProgress < 100 ? '正在上传资源...' : '服务器正在处理...' }}
        </div>
        <div class="mt-2 text-gray-400 text-sm">请勿关闭窗口</div>
      </div>

      <!-- Loading Progress Bar (for non-upload tasks) -->
      <div v-if="loading && !isUploading" class="absolute top-0 left-0 right-0 z-50">
        <el-progress :percentage="100" :indeterminate="true" :show-text="false" :stroke-width="2" />
      </div>

=======
    <div class="flex h-[500px]" v-loading="loading">
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
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
