<template>
  <div class="workspace-placeholder">
    <el-card class="info-card">
      <template #header>
        <div class="card-header">
          <span>AuraDocs 工作台（鉴权联调验证）</span>
          <el-button type="danger" plain size="small" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </template>

      <el-descriptions :column="1" border v-if="userStore.userInfo">
        <el-descriptions-item label="用户 ID">{{ userStore.userInfo.userId }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ userStore.userInfo.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ userStore.userInfo.nickname }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag size="small">{{ userStore.userInfo.role }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

onMounted(() => {
  // 页面加载时主动调一次 /api/auth/me，验证 JWT 拦截器与请求头注入是否生效
  userStore.fetchUserInfo()
})

const handleLogout = () => {
  userStore.logout()
  ElMessage.info('已安全退出登录')
  router.push('/login')
}
</script>

<style scoped>
.workspace-placeholder {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8fafc;
}

.info-card {
  width: 460px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>