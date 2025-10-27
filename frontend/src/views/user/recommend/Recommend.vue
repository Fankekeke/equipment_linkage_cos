<template>
  <div class="recommend-container">
    <a-row style="margin-top: 25px">
      <a-col :span="24">
        <a-alert message="智能场景推荐" type="info" show-icon style="margin-bottom: 20px">
          <template #description>
            根据您的设备使用习惯，系统为您推荐以下自动化场景
          </template>
        </a-alert>

        <div v-if="loading" class="loading-container">
          <a-spin size="large" tip="加载中..." />
        </div>

        <div v-else>
          <a-empty v-if="recommendList.length === 0" description="暂无推荐场景" />

          <a-row :gutter="[20, 20]" v-else>
            <a-col :span="12" v-for="(item, index) in recommendList" :key="index">
              <a-card class="recommend-card" hoverable>
                <template #title>
                  <div class="card-header">
                    <a-avatar :size="32" icon="bulb" style="background-color: #1890ff;" />
                    <span class="scene-name">{{ item.sceneName }}</span>
                  </div>
                </template>

                <div class="card-content">
                  <p class="scene-description">
                    <a-icon type="info-circle" theme="filled" style="color: #1890ff; margin-right: 8px;" />
                    {{ item.description }}
                  </p>

                  <a-divider dashed />

                  <div class="scene-details">
                    <a-row :gutter="16">
                      <a-col :span="12">
                        <div class="detail-item">
                          <div class="detail-label">触发设备：</div>
                          <div class="detail-value">
                            <a-tag color="orange">{{ item.triggerDeviceName || 'ID: ' + item.triggerDeviceId }}</a-tag>
                          </div>
                        </div>
                        <div class="detail-item">
                          <div class="detail-label">触发动作：</div>
                          <div class="detail-value">
                            <a-tag :color="item.triggerAction === '0' ? 'red' : 'green'">
                              {{ item.triggerAction === '0' ? '关闭' : '开启' }}
                            </a-tag>
                          </div>
                        </div>
                      </a-col>

                      <a-col :span="12">
                        <div class="detail-item">
                          <div class="detail-label">目标设备：</div>
                          <div class="detail-value">
                            <a-tag color="blue">{{ item.targetDeviceName || 'ID: ' + item.targetDeviceId }}</a-tag>
                          </div>
                        </div>
                        <div class="detail-item">
                          <div class="detail-label">执行动作：</div>
                          <div class="detail-value">
                            <a-tag :color="item.targetAction === '0' ? 'red' : 'green'">
                              {{ item.targetAction === '0' ? '关闭' : '开启' }}
                            </a-tag>
                          </div>
                        </div>
                      </a-col>
                    </a-row>
                  </div>
                </div>

                <template #actions>
<!--                  <a-button type="primary" @click="createScene(item)">-->
<!--                    <a-icon type="plus-circle" />-->
<!--                    创建场景-->
<!--                  </a-button>-->
                </template>
              </a-card>
            </a-col>
          </a-row>
        </div>
      </a-col>
    </a-row>
  </div>
</template>
<script>
import {mapState} from 'vuex'
export default {
  name: 'Recommend',
  computed: {
    ...mapState({
      currentUser: state => state.account.user
    })
  },
  data () {
    return {
      recommendList: [],
      loading: false
    }
  },
  mounted () {
    this.queryRecommend()
  },
  methods: {
    queryRecommend () {
      this.loading = true
      this.$get(`/cos/device-info/recommendScene`, {
        userId: this.currentUser.userId
      }).then((r) => {
        this.recommendList = r.data.data || []
        this.loading = false
      }).catch(() => {
        this.loading = false
        this.$message.error('获取推荐失败')
      })
    },

    createScene (item) {
      this.$confirm({
        title: '确认创建场景',
        content: `确定要创建"${item.sceneName}"自动化场景吗？`,
        onOk: () => {
          // 这里调用创建场景的API
          this.$message.success('场景创建成功')
          // 可以从推荐列表中移除已创建的场景
          this.recommendList = this.recommendList.filter(scene => scene !== item)
        }
      })
    },

    ignoreRecommend (item) {
      this.$confirm({
        title: '确认忽略推荐',
        content: `确定要忽略"${item.sceneName}"这条推荐吗？`,
        onOk: () => {
          // 这里调用忽略推荐的API
          this.recommendList = this.recommendList.filter(scene => scene !== item)
          this.$message.success('已忽略该推荐')
        }
      })
    }
  }
}
</script>

<style scoped>.recommend-container {
  width: 100%;
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 100px);
}

.loading-container {
  text-align: center;
  padding: 50px 0;
}

.recommend-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.recommend-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scene-name {
  font-size: 16px;
  font-weight: 500;
  color: #262626;
}

.card-content {
  padding: 10px 0;
}

.scene-description {
  font-size: 14px;
  color: #595959;
  line-height: 1.6;
  margin-bottom: 20px;
}

.scene-details {
  background-color: #fafafa;
  border-radius: 6px;
  padding: 16px;
}

.detail-item {
  display: flex;
  margin-bottom: 12px;
}

.detail-label {
  width: 80px;
  color: #8c8c8c;
  font-size: 13px;
}

.detail-value {
  flex: 1;
  font-size: 13px;
}

>>> .ant-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

>>> .ant-card-actions > li {
  border: none !important;
  padding: 0 !important;
}

>>> .ant-alert {
  border-radius: 8px;
}
</style>
