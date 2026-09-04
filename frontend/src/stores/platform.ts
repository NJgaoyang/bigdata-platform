import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DevFile, Project } from '../api'
import { platformApi } from '../api'

export const usePlatformStore = defineStore('platform', () => {
  const projects = ref<Project[]>([])
  const files = ref<DevFile[]>([])
  const activeFileId = ref<number | null>(null)
  const loading = ref(false)

  async function loadDevelopment() {
    loading.value = true
    try {
      const projectResult = await platformApi.projects()
      projects.value = projectResult.data.data || []
      if (projects.value.length) {
        const fileResult = await platformApi.files(projects.value[0].id)
        files.value = fileResult.data.data || []
        activeFileId.value ||= files.value[0]?.id || null
      }
    } finally { loading.value = false }
  }
  const activeFile = () => files.value.find(file => file.id === activeFileId.value)
  return { projects, files, activeFileId, activeFile, loading, loadDevelopment }
})
