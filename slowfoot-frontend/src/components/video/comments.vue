<template>
  <el-card class="comments-card" shadow="never">
    <div class="comments-heading">
      <span>评论</span>
      <el-tag size="small" effect="plain">{{ total }}</el-tag>
      <span class="heading-spacer" />
      <el-button text class="comment-action" aria-label="刷新评论" @click="loadComments">
        <i class="mdi mdi-refresh" />
        <span>刷新</span>
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
          <el-avatar v-if="comment.userAvatar" :src="apiFileGet(comment.userAvatar)" />
          <DefaultAvatar v-else :size="40" />
          <div class="comment-body">
            <strong>{{ comment.userName || '用户' }}</strong>
            <small>{{ formatDate(comment.gmtCreated) }}</small>
            <p>{{ comment.content }}</p>
          </div>
          <div class="comment-actions">
            <el-button v-if="canDelete(comment)" text class="comment-action" aria-label="删除评论" @click="deleteComment(comment.id)">
              <i class="mdi mdi-delete-outline" />
              <span>删除</span>
            </el-button>
            <el-button v-if="userStore.token" text class="comment-action" aria-label="回复评论" @click="reply(comment)">
              <i class="mdi mdi-reply" />
              <span>回复</span>
            </el-button>
          </div>
        </div>
        <div v-for="replyItem in comment.replies || []" :key="replyItem.id" class="comment-item reply-item">
          <el-avatar v-if="replyItem.userAvatar" size="32" :src="apiFileGet(replyItem.userAvatar)" />
          <DefaultAvatar v-else :size="32" />
          <div class="comment-body">
            <strong>{{ replyItem.userName || '用户' }}</strong>
            <small>{{ formatDate(replyItem.gmtCreated) }}</small>
            <p>
              <span v-if="replyItem.replyToUserName" class="muted">回复 {{ replyItem.replyToUserName }}：</span>
              {{ replyItem.content }}
            </p>
          </div>
          <el-button v-if="canDelete(replyItem)" text class="comment-action" aria-label="删除回复" @click="deleteComment(replyItem.id)">
            <i class="mdi mdi-delete-outline" />
            <span>删除</span>
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
import DefaultAvatar from '../DefaultAvatar.vue'
import { useUserStore } from '../../stores'

const props = defineProps({ workId: { type: [Number, String], default: null } })
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
.comments-card { min-height: 100%; color: #2a3446; background: #fff; border: 0; }
.comments-card :deep(.el-card__body) { padding: 0; }
.comments-heading, .comment-item, .comment-actions { display: flex; align-items: center; }
.comments-heading { position: sticky; top: 0; z-index: 1; padding: 15px 18px; background: #fff; border-bottom: 1px solid #eef0f4; font-size: 16px; font-weight: 750; }
.comments-heading :deep(.el-tag) { margin-left: 7px; }
.heading-spacer { flex: 1; }
.comment-action { display: inline-flex; align-items: center; gap: 5px; min-height: 28px; padding: 4px 7px; color: #8a94a6; border-radius: 6px; font-size: 11px; }
.comment-action:hover { color: #ff2442; background: #fff0f2; }
.comment-action i { font-size: 15px; }
.comment-action span { font-size: 11px; }
.comment-editor { padding: 14px 18px 17px; border-bottom: 1px solid #eef0f4; }
.comment-editor :deep(.el-textarea__inner) { color: #2a3446; background: #f7f8fa; box-shadow: 0 0 0 1px #dfe4ec inset; }
.comment-actions { justify-content: flex-end; gap: 8px; margin-top: 10px; }
.comment-list { padding: 3px 18px 12px; }
.comment-item { align-items: flex-start; gap: 11px; padding: 14px 0; border-bottom: 1px solid #f1f3f7; }
.reply-item { margin-left: 42px; }
.comment-body { flex: 1; min-width: 0; }
.comment-body strong, .comment-body small { display: block; }
.comment-body strong { color: #333d4e; font-size: 12px; }
.comment-body small, .muted, .login-hint { color: #98a1b0; font-size: 10px; }
.comment-body p { margin: 6px 0 0; color: #3c4657; font-size: 13px; line-height: 1.5; white-space: pre-wrap; overflow-wrap: anywhere; }
.login-hint, .empty-state { padding: 28px 18px; color: #98a1b0; text-align: center; font-size: 12px; }
:deep(.el-pagination) { justify-content: center; padding: 10px 0 18px; }
</style>
