<template>
  <div style="width: 100%">
    <a-row style="margin-top: 25px">
      <a-col :span="24" style="margin-top: 25px">
        <div style="background:#ECECEC; padding:30px">
          <div class="statistics-container">
            <a-skeleton :loading="loading" active :paragraph="{ rows: 10 }"/>
            <a-alert v-if="!loading" message="设备在线统计" type="info" close-text="Close Now" class="stats-alert"/>
            <a-row :gutter="15" v-if="!loading">
              <a-col :span="6" v-for="(item, index) in dataList" style="margin-bottom: 15px" :key="index">
                <a-card :bordered="false" hoverable :description="item.address" class="device-card">
                  <template #title>
                  <span>
                    <a-badge status="processing"/>
                    {{ item.name }} <span class="device-code">{{ item.code }}</span>
                  </span>
                  </template>
                  <a-row>
                    <a-col :span="24" class="device-info">
                      <div class="info-item">
                        <span class="info-label">设备类型：</span>
                        <span class="info-value">{{ item.typeName }}</span>
                      </div>
                      <div class="info-item">
                        <span class="info-label">所属用户：</span>
                        <span class="info-value">{{ item.userName }}</span>
                      </div>
                      <div class="info-item">
                        <span class="info-label">上次在线时间：</span>
                        <span class="info-value">{{ item.lastOpenDate ? item.lastOpenDate : '- -' }}</span>
                      </div>
                      <div class="status-container">
                        <a-tag size="small" :color="item.openFlag == 1 ? 'blue' : 'pink'">
                          {{ item.openFlag == 1 ? '开' : '关' }}
                        </a-tag>
                      </div>
                    </a-col>
                  </a-row>
                </a-card>
              </a-col>
            </a-row>
          </div>
          <a-pagination show-quick-jumper :defaultCurrent="page.current" :total="page.total"
                        :defaultPageSize="page.size" showLessItems @change="pageChange"/>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script>
export default {
  name: 'House',
  data () {
    return {
      page: {
        current: 1,
        total: 0,
        size: 36
      },
      dataList: [],
      loading: false,
      checkFlag: '1',
      series: [{
        name: 'Series 1',
        data: [80, 50, 30, 40, 100, 20]
      }],
      chartOptions: {
        chart: {
          height: 350,
          type: 'radar'
        },
        title: {
          text: 'Basic Radar Chart'
        },
        xaxis: {
          categories: ['January', 'February', 'March', 'April', 'May', 'June']
        }
      }
    }
  },
  watch: {
    checkFlag: function (value) {
      this.selectSchoolRate(value)
    }
  },
  mounted () {
    this.fetch()
  },
  methods: {
    selectSchoolRate (type) {
      this.loading = true
      this.$get(`/cos/score-line-info/selectSchoolRate/type/${type}`).then((r) => {
        this.dataList = r.data.data
        this.loading = false
      })
    },
    pageChange (page, pageSize) {
      this.page.size = pageSize
      this.page.current = page
      this.fetch()
    },
    fetch (params = {}) {
      // 显示loading
      this.loading = true
      // 如果分页信息为空，则设置为默认值
      params.size = this.page.size
      params.current = this.page.current
      this.$get('/cos/device-info/page', {
        ...params
      }).then((r) => {
        let data = r.data.data
        const pagination = {...this.pagination}
        pagination.total = data.total
        this.dataList = data.records
        this.page = pagination
        // 数据加载完毕，关闭loading
        this.loading = false
      })
    }
  }
}
</script>
<style scoped>
.device-card {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.device-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.device-code {
  color: #fa541c;
  font-weight: bold;
}

.device-info {
  font-size: 13px;
  font-family: 'Microsoft YaHei', sans-serif;
}

.info-item {
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
}

.info-label {
  color: #666;
  min-width: 80px;
}

.info-value {
  flex: 1;
  text-align: right;
  color: #333;
}

.status-container {
  text-align: right;
  margin-top: 10px;
}

>>> .ant-card-head-title {
  font-size: 14px;
  font-family: 'Microsoft YaHei', sans-serif;
  font-weight: 500;
}

>>> .ant-alert-message {
  font-size: 16px;
  font-family: 'Microsoft YaHei', sans-serif;
  font-weight: 500;
}

/* 分页样式优化 */
>>> .ant-pagination {
  text-align: center;
  margin-top: 20px;
}

.statistics-container {
  background: linear-gradient(to bottom, #f5f5f5, #e8e8e8);
  padding: 30px;
  border-radius: 10px;
  min-height: calc(100vh - 100px);
}

.stats-alert {
  margin-bottom: 20px;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}
</style>
