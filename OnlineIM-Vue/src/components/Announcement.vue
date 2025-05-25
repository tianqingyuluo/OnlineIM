<template>
  <div class="announcement-container">
    <!-- 公告列表 -->
    <div class="announcement-list">
      <div class="header">
        <h2>群公告</h2>
        <button class="btn-add" @click="showCreateForm = true">+ 新建公告</button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="announcements.length === 0" class="empty">暂无公告</div>

      <div v-else>
        <div
            v-for="announcement in sortedAnnouncements"
            :key="announcement.announcement_id"
            class="announcement-item"
            :class="{ pinned: announcement.pinned }"
            @click="selectAnnouncement(announcement)"
        >
          <div class="announcement-header">
            <span class="title">{{ announcement.title }}</span>
            <span v-if="announcement.pinned" class="pinned-badge">置顶</span>
            <div class="actions">
              <button
                  class="btn-action"
                  @click.stop="togglePin(announcement)"
                  :title="announcement.pinned ? '取消置顶' : '置顶'"
              >
                📌
              </button>
              <button
                  class="btn-action"
                  @click.stop="editAnnouncement(announcement)"
                  title="编辑"
              >
                ✏️
              </button>
              <button
                  class="btn-action"
                  @click.stop="deleteAnnouncement(announcement)"
                  title="删除"
              >
                🗑️
              </button>
            </div>
          </div>
          <div class="content-preview">{{ truncatedContent(announcement.content) }}</div>
          <div class="meta">
            <span class="publisher">
              <img :src="announcement.publisher_info.avatar_url" class="avatar" />
              {{ announcement.publisher_info.nickname || announcement.publisher_info.username }}
            </span>
            <span class="time">{{ formatTime(announcement.updated_at) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 公告详情/编辑面板 -->
    <div class="announcement-detail" v-if="selectedAnnouncement || showCreateForm">
      <div class="detail-header">
        <h3>{{ showCreateForm ? '新建公告' : '公告详情' }}</h3>
        <button class="btn-close" @click="closeDetail">×</button>
      </div>

      <div v-if="!isEditing && !showCreateForm" class="detail-content">
        <h4>{{ selectedAnnouncement.title }}</h4>
        <div class="content">{{ selectedAnnouncement.content }}</div>
        <div class="meta">
          <span>发布者: {{ selectedAnnouncement.publisher_info.nickname || selectedAnnouncement.publisher_info.username }}</span>
          <span>更新时间: {{ formatTime(selectedAnnouncement.updated_at) }}</span>
        </div>
        <div class="actions">
          <button class="btn-edit" @click="isEditing = true">编辑</button>
        </div>
      </div>

      <div v-else class="edit-form">
        <form @submit.prevent="submitForm">
          <div class="form-group">
            <label for="title">标题</label>
            <input
                id="title"
                v-model="formData.title"
                type="text"
                required
                placeholder="输入公告标题"
            />
          </div>

          <div class="form-group">
            <label for="content">内容</label>
            <textarea
                id="content"
                v-model="formData.content"
                required
                rows="6"
                placeholder="输入公告内容"
            ></textarea>
          </div>

          <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formData.pinned" />
              置顶公告
            </label>
          </div>

          <div class="form-actions">
            <button type="button" class="btn-cancel" @click="cancelEdit">取消</button>
            <button type="submit" class="btn-submit">
              {{ showCreateForm ? '发布' : '更新' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed, onMounted } from 'vue';
import { groupAnnouncementService } from '@/services/announcement.service.ts';
import type { GroupAnnouncement, GroupAnnouncementRequest } from '@/type/groupAnnouncement';

export default defineComponent({
  name: 'GroupAnnouncement',
  props: {
    groupId: {
      type: String,
      required: true
    }
  },
  setup(props, { emit }) { // 添加 emit 参数
    const announcements = ref<GroupAnnouncement[]>([]);
    const loading = ref(false);
    const selectedAnnouncement = ref<GroupAnnouncement | null>(null);
    const isEditing = ref(false);
    const showCreateForm = ref(false);
    const error = ref<string | null>(null);

    const formData = ref<GroupAnnouncementRequest>({
      title: '',
      content: '',
      pinned: false
    });

    // 按置顶和时间排序的公告列表
    const sortedAnnouncements = computed(() => {
      return [...announcements.value].sort((a, b) => {
        if (a.pinned !== b.pinned) {
          return a.pinned ? -1 : 1;
        }
        return new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime();
      });
    });

    // 加载公告列表
    const loadAnnouncements = async () => {
      try {
        loading.value = true;
        announcements.value = await groupAnnouncementService.getAnnouncementsByGroupId(props.groupId);
        error.value = null;
      } catch (err) {
        error.value = '加载公告失败，请稍后重试';
        console.error(err);
      } finally {
        loading.value = false;
      }
    };

    // 选择公告查看详情
    const selectAnnouncement = (announcement: GroupAnnouncement) => {
      selectedAnnouncement.value = announcement;
      isEditing.value = false;
      showCreateForm.value = false;
    };

    // 关闭详情面板
    const closeDetail = () => {
      selectedAnnouncement.value = null;
      isEditing.value = false;
      showCreateForm.value = false;
    };

    // 编辑公告
    const editAnnouncement = (announcement: GroupAnnouncement) => {
      selectedAnnouncement.value = announcement;
      formData.value = {
        title: announcement.title,
        content: announcement.content,
        pinned: announcement.pinned
      };
      isEditing.value = true;
    };

    // 取消编辑
    const cancelEdit = () => {
      if (showCreateForm.value) {
        showCreateForm.value = false;
      } else {
        isEditing.value = false;
      }
      formData.value = { title: '', content: '', pinned: false };
    };

    // 提交表单（新建或更新）
    const submitForm = async () => {
      try {
        if (showCreateForm.value) {
          // 新建公告
          const newAnnouncement = await groupAnnouncementService.createAnnouncement(
              props.groupId,
              formData.value
          );
          announcements.value.unshift(newAnnouncement);
          showCreateForm.value = false;
        } else if (selectedAnnouncement.value) {
          // 更新公告
          const updatedAnnouncement = await groupAnnouncementService.updateAnnouncement(
              props.groupId,
              formData.value,
              selectedAnnouncement.value.announcement_id
          );

          const index = announcements.value.findIndex(
              a => a.announcement_id === updatedAnnouncement.announcement_id
          );
          if (index !== -1) {
            announcements.value.splice(index, 1, updatedAnnouncement);
          }

          selectedAnnouncement.value = updatedAnnouncement;
          isEditing.value = false;
        }

        formData.value = { title: '', content: '', pinned: false };
        error.value = null;
      } catch (err) {
        error.value = '操作失败，请稍后重试';
        console.error(err);
      }
    };

    // 删除公告
    const deleteAnnouncement = async (announcement: GroupAnnouncement) => {
      if (!confirm('确定要删除这条公告吗？')) return;

      try {
        await groupAnnouncementService.deleteAnnouncement(
            props.groupId,
            announcement.announcement_id
        );

        announcements.value = announcements.value.filter(
            a => a.announcement_id !== announcement.announcement_id
        );

        if (selectedAnnouncement.value?.announcement_id === announcement.announcement_id) {
          selectedAnnouncement.value = null;
        }

        error.value = null;
      } catch (err) {
        error.value = '删除公告失败，请稍后重试';
        console.error(err);
      }
    };

    // 切换置顶状态
    const togglePin = async (announcement: GroupAnnouncement) => {
      try {
        await groupAnnouncementService.pinnedAnnouncement(
            props.groupId,
            announcement.announcement_id
        );

        const updatedAnnouncement = {
          ...announcement,
          pinned: !announcement.pinned
        };

        const index = announcements.value.findIndex(
            a => a.announcement_id === announcement.announcement_id
        );
        if (index !== -1) {
          announcements.value.splice(index, 1, updatedAnnouncement);
        }

        if (selectedAnnouncement.value?.announcement_id === announcement.announcement_id) {
          selectedAnnouncement.value = updatedAnnouncement;
        }

        error.value = null;
      } catch (err) {
        error.value = '操作失败，请稍后重试';
        console.error(err);
      }
    };

    // 辅助函数：截断内容预览
    const truncatedContent = (content: string) => {
      return content.length > 50 ? content.substring(0, 50) + '...' : content;
    };

    // 辅助函数：格式化时间
    const formatTime = (timeString: string) => {
      return new Date(timeString).toLocaleString();
    };

    onMounted(() => {
      loadAnnouncements();
    });

    const goBack = () => {
      emit('back'); // 发射 'back' 事件
    };

    return {
      announcements,
      sortedAnnouncements,
      loading,
      selectedAnnouncement,
      isEditing,
      showCreateForm,
      formData,
      error,
      selectAnnouncement,
      closeDetail,
      editAnnouncement,
      cancelEdit,
      submitForm,
      deleteAnnouncement,
      togglePin,
      truncatedContent,
      formatTime,
      goBack // 暴露 goBack 方法
    };
  }
});
</script>

<style scoped>
/* 灰度风格样式 */
.announcement-container {
  display: flex;
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  font-family: 'Segoe UI', Arial, sans-serif;
  color: #333;
}

.announcement-list {
  flex: 1;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ddd;
}

h2, h3, h4 {
  margin: 0;
  color: #444;
}

.btn-add, .btn-edit, .btn-submit {
  background-color: #555;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn-add:hover, .btn-edit:hover, .btn-submit:hover {
  background-color: #666;
}

.loading, .empty {
  padding: 20px;
  text-align: center;
  color: #777;
}

.announcement-item {
  background: white;
  border-radius: 6px;
  padding: 15px;
  margin-bottom: 15px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  border-left: 4px solid #ddd;
}

.announcement-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.announcement-item.pinned {
  border-left-color: #888;
}

.announcement-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title {
  font-weight: bold;
  font-size: 1.1em;
}

.pinned-badge {
  background: #888;
  color: white;
  font-size: 0.8em;
  padding: 2px 6px;
  border-radius: 10px;
  margin-left: 10px;
}

.actions {
  display: flex;
  gap: 8px;
}

.btn-action {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1.1em;
  color: #666;
  transition: color 0.2s;
}

.btn-action:hover {
  color: #333;
}

.content-preview {
  color: #666;
  margin-bottom: 10px;
  line-height: 1.4;
}

.meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.9em;
  color: #888;
}

.publisher {
  display: flex;
  align-items: center;
}

.avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  margin-right: 5px;
}

.announcement-detail {
  flex: 1;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ddd;
}

.btn-close {
  background: none;
  border: none;
  font-size: 1.5em;
  cursor: pointer;
  color: #666;
}

.detail-content {
  background: white;
  padding: 20px;
  border-radius: 6px;
}

.detail-content h4 {
  margin-bottom: 15px;
  color: #444;
}

.content {
  line-height: 1.6;
  margin-bottom: 20px;
  white-space: pre-wrap;
}

.edit-form {
  background: white;
  padding: 20px;
  border-radius: 6px;
}

.form-group {
  margin-bottom: 15px;
}

label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
  color: #555;
}

input[type="text"], textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-family: inherit;
}

textarea {
  resize: vertical;
}

.checkbox-label {
  display: flex;
  align-items: center;
  font-weight: normal;
  cursor: pointer;
}

.checkbox-label input {
  margin-right: 8px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.btn-cancel {
  background: #ddd;
  color: #333;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn-cancel:hover {
  background: #ccc;
}

.error-message {
  color: #d32f2f;
  margin-top: 10px;
  text-align: center;
}

@media (max-width: 768px) {
  .announcement-container {
    flex-direction: column;
  }
}
</style>