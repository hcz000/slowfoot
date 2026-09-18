<template>
  <el-card class="comments-card" shadow="never">
    <div class="comments-heading">
      <span>评论</span>
      <el-tag size="small" effect="plain">{{ total }}</el-tag>
      <span class="heading-spacer" />
      <el-button text circle aria-label="刷新评论" @click="loadComments">
        <i class="mdi mdi-refresh" />
      </el-button>
    </div>
    <el-divider />

    <div v-if="userStore.token" class="comment-editor">
      <el-input v-model="content" type="textarea" :placeholder="parentId ? '回复评论' : '发表评论'"
        :autosize="{ minRows: 2, maxRows: 6 }" maxlength="1000" show-word-limit />
      <div class="comment-actions">
        <el-button v-if="parentId" link @click="parentId = null">取消回复</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!content.trim()" @click="submitComment">
          {{ parentId ? '回复' : '发表评论' }}
        </el-button>
      </div>
    </div>
    <div v-else class="login-hint">登录后可以发表评论</div>

    <div v-if="comments.length" class="comment-list">
      <template v-for="comment in comments" :key="comment.id">
        <div class="comment-item">
          <el-avatar :src="comment.userAvatar ? apiFileGet(comment.userAvatar) : '/logo.png'" />
          <div class="comment-body">
            <strong>{{ comment.userName || '用户' }}</strong>
            <small>{{ formatDate(comment.gmtCreated) }}</small>
            <p>{{ comment.content }}</p>
          </div>
          <div class="comment-actions">
            <el-button v-if="canDelete(comment)" text circle aria-label="删除评论" @click="deleteComment(comment.id)">
              <i class="mdi mdi-delete-outline" />
            </el-button>
            <el-button v-if="userStore.token" text circle aria-label="回复评论" @click="reply(comment)">
              <i class="mdi mdi-reply" />
            </el-button>
          </div>
        </div>
        <div v-for="replyItem in comment.replies || []" :key="replyItem.id" class="comment-item reply-item">
          <el-avatar size="32" :src="replyItem.userAvatar ? apiFileGet(replyItem.userAvatar) : '/logo.png'" />
          <div class="comment-body">
            <strong>{{ replyItem.userName || '用户' }}</strong>
            <small>{{ formatDate(replyItem.gmtCreated) }}</small>
            <p>
              <span v-if="replyItem.replyToUserName" class="muted">回复 {{ replyItem.replyToUserName }}：</span>
              {{ replyItem.content }}
            </p>
          </div>
          <el-button v-if="canDelete(replyItem)" text circle aria-label="删除回复" @click="deleteComment(replyItem.id)">
            <i class="mdi mdi-delete-outline" />
          </el-button>
        </div>
        <el-divider />
      </template>
    </div>
    <div v-else class="empty-state">暂无评论</div>
    <el-pagination v-if="pages > 1" v-model:current-page="page" layout="prev, pager, next" :page-count="pages"
      background @current-change="loadComments" />
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { apiFileGet } from '../../apis/file'
import { apiCreateComment, apiDeleteComment, apiGetWorkComments } from '../../apis/comment'
import { useUserStore } from '../../stores'

const props = defineProps({ workId: { type: Number, default: null } })
const userStore = useUserStore()
const comments = ref([])
const content = ref('')
const parentId = ref(null)
const page = ref(1)
const pages = ref(0)
const total = ref(0)
const submitting = ref(false)
const formatDate = (value) => value ? new Date(value).toLocaleString() : ''

const loadComments = () => {
  if (!props.workId) return
  apiGetWorkComments(props.workId, page.value).then(({ data }) => {
    if (!data.success) return
    comments.value = data.data?.records || []
    pages.value = data.data?.pages || 0
    total.value = data.data?.total || 0
  })
}

const reply = (comment) => {
  parentId.value = comment.id
  content.value = ''
}

const canDelete = (comment) => Boolean(userStore.token && userStore.info?.id === comment.userId)
const deleteComment = (commentId) => apiDeleteComment(commentId).then(({ data }) => {
  if (data.success) loadComments()
})

const submitComment = () => {
  if (!props.workId || !content.value.trim()) return
  submitting.value = true
  apiCreateComment(props.workId, content.value.trim(), parentId.value).then(({ data }) => {
    if (data.success) {
      content.value = ''
      parentId.value = null
      loadComments()
    }
  }).finally(() => { submitting.value = false })
}

watch(() => props.workId, () => {
  page.value = 1
  loadComments()
}, { immediate: true })
</script>

<style scoped>
.comments-card { background: #36393f; border: 0; }
.comments-heading, .comment-item, .comment-actions { display: flex; align-items: center; }
.comments-heading { padding: 12px 16px; font-size: 18px; }
.heading-spacer { flex: 1; }
.comment-editor { padding: 8px 16px 16px; }
.comment-actions { justify-content: flex-end; gap: 8px; }
.comment-list { padding: 0 16px; }
.comment-item { align-items: flex-start; gap: 12px; padding: 12px 0; }
.reply-item { margin-left: 44px; }
.comment-body { flex: 1; min-width: 0; }
.comment-body strong, .comment-body small { display: block; }
.comment-body small, .muted, .login-hint { color: #a3a6ad; }
.comment-body p { margin: 6px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.login-hint, .empty-state { padding: 24px 16px; text-align: center; }
</style>
