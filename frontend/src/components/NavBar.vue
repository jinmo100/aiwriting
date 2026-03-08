<template>
  <div class="nav-bar">
    <el-menu
      :default-active="activeIndex"
      mode="horizontal"
      @select="handleSelect"
      background-color="#545c64"
      text-color="#fff"
      active-text-color="#ffd04b"
    >
      <el-menu-item index="/submit">
        <el-icon><Edit /></el-icon>
        <span>提交作文</span>
      </el-menu-item>
      <el-menu-item index="/config">
        <el-icon><Setting /></el-icon>
        <span>API配置</span>
      </el-menu-item>
      <el-menu-item index="/history">
        <el-icon><Document /></el-icon>
        <span>历史记录</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Edit, Setting, Document } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const activeIndex = computed(() => route.path)

function handleSelect(index: string) {
  router.push(index)
}
</script>

<style scoped>
.nav-bar {
  margin-bottom: 20px;
}

.el-menu {
  border-bottom: none;
}
</style>
