<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Upload, Trash2, Image as ImageIcon, Box, Database, Play } from 'lucide-vue-next'
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
const videoRef = ref<HTMLVideoElement | null>(null)
const PAUSE_TIME = 884
let uploadFallbackTimer: any = null
let hasRealUploadProgress = false

const startUploadFallback = () => {
  hasRealUploadProgress = false
  if (uploadFallbackTimer) clearInterval(uploadFallbackTimer)
  uploadFallbackTimer = setInterval(() => {
    if (!isUploading.value) return
    if (hasRealUploadProgress) return
    if (uploadProgress.value >= 85) return
    uploadProgress.value = Math.min(85, uploadProgress.value + 0.2 + Math.random() * 0.8)
  }, 400)
}

const stopUploadFallback = () => {
  if (uploadFallbackTimer) {
    clearInterval(uploadFallbackTimer)
    uploadFallbackTimer = null
  }
}

const handleTimeUpdate = () => {
  if (videoRef.value && (isUploading.value || isTraining.value) && videoRef.value.currentTime >= PAUSE_TIME) {
    videoRef.value.pause()
    videoRef.value.currentTime = PAUSE_TIME
  }
}

watch([isUploading, isTraining], ([newUploading, newTraining]) => {
  if (!newUploading && !newTraining) {
    videoRef.value?.play()
  }
})

const fetchData = async () => {
  loading.value = true
  try {
    const [d, m] = await Promise.all([listDatasets(), listModels()])
    datasets.value = [...d]
    models.value = [...m]
  } catch (e: any) {
    ElMessage.error('无法加载资源列表: ' + e.message)
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val) fetchData()
})

const close = () => {
  if (!isUploading.value && !isTraining.value) {
    emit('update:visible', false)
  }
}

const pollForResource = async (type: 'dataset' | 'model', name: string, maxRetries = 10) => {
  for (let i = 0; i < maxRetries; i++) {
    await new Promise(resolve => setTimeout(resolve, 1500));
    await fetchData();
    if (type === 'dataset') {
      if (datasets.value.some(d => d.datasetName === name)) return true;
    } else {
      if (models.value.some(m => m.modelName === name)) return true;
    }
  }
  return false;
}

const handleImportDataset = async () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.webkitdirectory = true
  input.multiple = true
  input.onchange = async (e: any) => {
    const files = Array.from(e.target.files) as File[]
    if (!files.length) return

    const folderPath = (files[0] as any).webkitRelativePath
    const datasetName = folderPath ? folderPath.split('/')[0] : 'Unknown_Dataset'
    const relativePaths = files.map((f: any) => f.webkitRelativePath)

    isUploading.value = true
    uploadProgress.value = 0
    startUploadFallback()
    
    try {
      await loadDataset(datasetName, files, relativePaths, (p) => {
        if (p > 0) {
          hasRealUploadProgress = true
          stopUploadFallback()
        }
        uploadProgress.value = Math.max(uploadProgress.value, p)
      })
      
      uploadProgress.value = 99;
      
      const success = await pollForResource('dataset', datasetName);
      uploadProgress.value = 100;
      
      if (success) {
        ElMessage.success({
          message: `数据集 "${datasetName}" 导入并同步成功`,
          duration: 3000,
          showClose: true
        })
      } else {
        ElMessage.warning('数据集已上传，但同步到列表超时，请手动刷新');
      }
    } catch (err: any) {
      ElMessage.error('导入失败: ' + err.message)
    } finally {
      isUploading.value = false
      uploadProgress.value = 0
      stopUploadFallback()
    }
  }
  input.click()
}

const handleDeleteDataset = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除此数据集吗？该操作不可撤销。', '警告', { type: 'warning' })
    loading.value = true
    await deleteDataset(id)
    if (props.selectedDataset?.id === id) emit('update:selectedDataset', null)
    ElMessage.success('已删除')
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
    
    const modelName = file.name.replace('.model', '');
    isUploading.value = true
    uploadProgress.value = 0
    startUploadFallback()
    
    try {
      await loadModel(modelName, file, (p) => {
        if (p > 0) {
          hasRealUploadProgress = true
          stopUploadFallback()
        }
        uploadProgress.value = Math.max(uploadProgress.value, p)
      })
      
      uploadProgress.value = 99;
      
      const success = await pollForResource('model', modelName);
      uploadProgress.value = 100;
      
      if (success) {
        ElMessage.success({
          message: `模型 "${modelName}" 导入并同步成功`,
          duration: 3000,
          showClose: true
        })
      } else {
        ElMessage.warning('模型已上传，但同步到列表超时，请手动刷新');
      }
    } catch (err: any) {
      ElMessage.error('导入失败: ' + err.message)
    } finally {
      isUploading.value = false
      uploadProgress.value = 0
      stopUploadFallback()
    }
  }
  input.click()
}

const handleTrainModel = async (dataset?: any) => {
  const targetDataset = dataset && dataset.id ? dataset : props.selectedDataset;
  if (!targetDataset) {
    ElMessage.warning('请先在资源管理器中选择一个数据集作为训练源');
    return;
  }

  try {
    const { value: modelName } = await ElMessageBox.prompt('请输入新模型名称', '训练模型', {
      confirmButtonText: '开始训练',
      cancelButtonText: '取消',
      inputValue: `RF-${new Date().getTime()}`
    })

    isTraining.value = true
    trainingProgress.value = 0
    
    const trainTimer = setInterval(() => {
      if (trainingProgress.value < 90) {
        trainingProgress.value += Math.random() * 3
      } else if (trainingProgress.value < 99.8) {
        trainingProgress.value += 0.03 + Math.random() * 0.08
      }
    }, 600)

    try {
      await trainModel(targetDataset.id, modelName)
      clearInterval(trainTimer)
      trainingProgress.value = 95
      
      const success = await pollForResource('model', modelName);
      trainingProgress.value = 100;
      
      if (success) {
        ElNotification.success({
          title: '训练完成',
          message: `模型 "${modelName}" 已训练并同步成功！`,
        })
      } else {
        ElMessage.warning('训练已完成，但同步到列表超时，请手动刷新');
      }
    } catch (err: any) {
      clearInterval(trainTimer);
      ElMessage.error('训练失败: ' + err.message)
    }
  } catch (err: any) {
  } finally {
    isTraining.value = false
    trainingProgress.value = 0
  }
}

const handleDeleteModel = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除此模型吗？', '警告', { type: 'warning' })
    loading.value = true
    await deleteModel(id)
    if (props.selectedModel?.id === id) emit('update:selectedModel', null)
    ElMessage.success('已删除')
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
      ElMessage.error('图像上传失败: ' + err.message)
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
    title="资源管理器 (Explorer)"
    width="800px"
    custom-class="explorer-dialog"
    destroy-on-close
  >
    <div class="flex h-[500px] relative">
      
      <div v-if="isUploading || isTraining" class="absolute inset-0 z-50 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="flex flex-col items-center max-w-md w-full px-10">
          <div class="mb-4 text-white text-xl font-medium">
            {{ isUploading ? (uploadProgress < 100 ? '正在同步资源...' : '服务器处理中...') : '正在训练模型...' }}
          </div>
          
          <div class="w-full max-w-xs aspect-video bg-black rounded-lg overflow-hidden shadow-2xl mb-4 border border-white/20">
            <video 
              ref="videoRef"
              src="/QiDONG!.mp4" 
              autoplay 
              loop 
              muted 
              playsinline 
              class="w-full h-full object-cover"
              @timeupdate="handleTimeUpdate"
            ></video>
          </div>
          
          <div class="mt-2 text-white font-mono text-lg">
            {{ (isUploading ? uploadProgress : trainingProgress).toFixed(1) }}%
          </div>
          
          <div class="mt-1 text-gray-500 text-xs">请勿关闭当前资源管理器窗口</div>
        </div>
      </div>

      <div v-if="loading && !isUploading" class="absolute top-0 left-0 right-0 z-50">
        <el-progress :percentage="100" :indeterminate="true" :show-text="false" :stroke-width="2" />
      </div>

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
        <el-button type="primary" plain class="w-full justify-start" @click="handleSelectImage" :disabled="isUploading || isTraining">
          <template #icon><ImageIcon /></template>
          选择图像评估
        </el-button>
      </div>

      <div class="flex-1 p-4 overflow-auto">
        <template v-if="activeTab === 'datasets'">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium">数据集列表</h3>
            <el-button type="primary" size="small" @click="handleImportDataset" :disabled="isUploading || isTraining">
              <template #icon><Upload /></template>
              导入数据集
            </el-button>
          </div>
          
          <div v-if="datasets.length === 0 && !loading" class="text-center text-gray-500 py-10">
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
                <el-button size="small" type="success" plain @click.stop="handleTrainModel(ds)" title="使用此数据集训练新模型" :disabled="isUploading || isTraining">
                  <template #icon><Play /></template>
                </el-button>
                <el-button size="small" type="danger" plain @click.stop="handleDeleteDataset(ds.id)" title="删除" :disabled="isUploading || isTraining">
                  <template #icon><Trash2 /></template>
                </el-button>
              </div>
            </div>
          </div>
        </template>

        <template v-if="activeTab === 'models'">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium">模型列表</h3>
            <el-button type="primary" size="small" @click="handleImportModel" :disabled="isUploading || isTraining">
              <template #icon><Upload /></template>
              导入模型
            </el-button>
          </div>
          
          <div v-if="models.length === 0 && !loading" class="text-center text-gray-500 py-10">
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
                  准确率: {{ m.accuracy == null ? '—' : `${(m.accuracy * 100).toFixed(1)}%` }}
                  |
                  F1 分数: {{ m.f1Score == null ? '—' : `${(m.f1Score * 100).toFixed(1)}%` }}
                  |
                  创建时间: {{ new Date(m.createdTime).toLocaleDateString() }}
                </div>
              </div>
              <div>
                <el-button size="small" type="danger" plain @click.stop="handleDeleteModel(m.id)" title="删除" :disabled="isUploading || isTraining">
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
