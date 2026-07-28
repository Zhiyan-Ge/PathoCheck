<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ImageInfo, Model } from '../types'
import { startAssessment } from '../api'

const props = defineProps<{
  visible: boolean;
  image: ImageInfo | null;
  model: Model | null;
  previewUrl?: string | null;
}>()

const emit = defineEmits(['update:visible', 'assessment-complete'])

const loading = ref(false)
const progress = ref(0)
const videoRef = ref<HTMLVideoElement | null>(null)
const PAUSE_TIME = 884
let timer: any = null

const handleTimeUpdate = () => {
  if (videoRef.value && loading.value && videoRef.value.currentTime >= PAUSE_TIME) {
    videoRef.value.pause()
    videoRef.value.currentTime = PAUSE_TIME
  }
}

const startProgress = () => {
  progress.value = 0
  timer = setInterval(() => {
    if (progress.value < 90) {
      progress.value += Math.random() * 5
    } else if (progress.value < 99.8) {
      progress.value += 0.05 + Math.random() * 0.1
    }
  }, 400)
}

const stopProgress = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  progress.value = 100
  videoRef.value?.play()
}

const close = () => {
  if (!loading.value) {
    emit('update:visible', false)
  }
}

const handleStart = async () => {
  if (!props.model || !props.image) return
  loading.value = true
  startProgress()
  try {
    const report = await startAssessment(props.model.id, props.image.imageId)
    stopProgress()
    setTimeout(() => {
      emit('assessment-complete', report)
    }, 500)
  } catch (e: any) {
    stopProgress()
    ElMessage.error('评估执行失败: ' + e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="close"
    title="病理图像预览与评估"
    width="500px"
    destroy-on-close
  >
    <div v-if="image" class="space-y-4 relative">
      
      <div v-if="loading" class="absolute inset-0 z-50 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm rounded">
        <div class="flex flex-col items-center w-full px-8">
          <div class="mb-3 text-white text-lg font-medium">正在执行病理评估...</div>
          
          <div class="w-full max-w-[280px] aspect-video bg-black rounded-lg overflow-hidden shadow-2xl mb-3 border border-white/20">
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

          <div class="mt-1 text-white font-mono text-sm">{{ progress.toFixed(1) }}%</div>
        </div>
      </div>

      <div class="bg-[var(--trae-bg)] p-4 rounded border border-[var(--trae-border)] flex justify-center items-center h-48 overflow-hidden">
        <img v-if="previewUrl" :src="previewUrl" class="max-h-full max-w-full object-contain" />
        <div v-else class="text-gray-500">
          [ 图像预览不可用 ]
        </div>
      </div>
      
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="文件名 (Name)">{{ image.imageName }}</el-descriptions-item>
        <el-descriptions-item label="格式 (Format)">{{ image.fileFormat }}</el-descriptions-item>
        <el-descriptions-item label="图像宽度 (Width)">{{ image.width }} px</el-descriptions-item>
        <el-descriptions-item label="图像高度 (Height)">{{ image.height }} px</el-descriptions-item>
        <el-descriptions-item label="文件大小 (Size)">{{ (image.fileSize / 1024).toFixed(2) }} KB</el-descriptions-item>
        <el-descriptions-item label="色彩模式 (Color)">{{ image.colorMode }}</el-descriptions-item>
        <el-descriptions-item label="所选模型 (Model)">
          <span :class="model ? 'text-green-500' : 'text-red-500'">
            {{ model ? model.modelName : '请先选择模型' }}
          </span>
        </el-descriptions-item>
      </el-descriptions>
    </div>
    
    <template #footer>
      <div class="flex justify-end space-x-2">
        <el-button @click="close" :disabled="loading">取消</el-button>
        <el-button type="primary" @click="handleStart" :disabled="!model || loading">
          开始评估 (Analyze)
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
