<template>
  <div style="width: 100%">
    <a-row style="margin-top: 25px">
      <a-col :span="24">
        <a-skeleton :loading="loading" active :paragraph="{ rows: 10 }"/>
        <a-alert v-if="!loading" message="设备上报历史统计" type="info" close-text="Close Now" style="margin-bottom: 15px"/>
        <a-row :gutter="15" v-if="!loading">
          <a-col :span="7">
            <a-list :data-source="dataList" bordered>
              <a-list-item slot="renderItem" slot-scope="item, index" class="device-list-item">
                <a-list-item-meta>
                  <a slot="title" @click="selectHistoryByDevice(item.id)" class="device-title">
                    {{ item.name }}
                    <span class="device-code">{{ item.code }}</span>
                  </a>
                  <div slot="description" class="device-description">
                    <div class="info-row">
                      <span class="info-label">设备类型：</span>
                      <span class="info-value">{{ item.typeName }}</span>
                    </div>
                    <div class="info-row">
                      <span class="info-label">所属用户：</span>
                      <span class="info-value">{{ item.userName }}</span>
                    </div>
                    <div class="info-row">
                      <span class="info-label">上次在线时间：</span>
                      <span class="info-value">{{ item.lastOpenDate ? item.lastOpenDate : '- -' }}</span>
                    </div>
                    <div class="status-row">
                      <a-tag size="small" :color="item.openFlag == 1 ? 'blue' : 'pink'" class="status-tag">
                        状态：{{ item.openFlag == 1 ? '开' : '关' }}
                      </a-tag>
                    </div>
                  </div>
                </a-list-item-meta>
              </a-list-item>
            </a-list>
          </a-col>
          <a-col :span="17">
            <div class="chart-container">
              <a-card hoverable :bordered="false" class="chart-card">
                <a-skeleton active v-if="loading" />
                <apexchart
                  v-if="!loading"
                  type="bar"
                  height="350"
                  :options="chartOptions1"
                  :series="series1"
                  class="apex-chart">
                </apexchart>
              </a-card>
            </div>
          </a-col>
        </a-row>
      </a-col>
    </a-row>
  </div>
</template>

<script>
import {mapState} from 'vuex'

export default {
  name: 'House',
  computed: {
    ...mapState({
      currentUser: state => state.account.user
    })
  },
  data () {
    return {
      page: {
        current: 1,
        total: 0,
        size: 999
      },
      dataList: [],
      deviceList: [],
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
      },
      series1: [],
      chartOptions1: {
        chart: {
          type: 'bar',
          height: 350,
          toolbar: {
            show: true,
            tools: {
              download: true,
              selection: false,
              zoom: false,
              zoomin: false,
              zoomout: false,
              pan: false,
              reset: false
            }
          }
        },
        title: {
          text: '本日数据统计',
          align: 'left',
          style: {
            fontSize: '18px',
            fontWeight: 'bold',
            color: '#262626'
          }
        },
        plotOptions: {
          bar: {
            horizontal: false,
            columnWidth: '55%',
            borderRadius: 4
          }
        },
        dataLabels: {
          enabled: false
        },
        stroke: {
          show: true,
          width: 2,
          colors: ['transparent']
        },
        xaxis: {
          categories: [],
          labels: {
            style: {
              fontSize: '12px'
            }
          }
        },
        yaxis: {
          title: {
            text: ''
          }
        },
        fill: {
          opacity: 1,
          colors: ['#1890ff']
        },
        tooltip: {
          y: {
            formatter: function (val) {
              return '设备值：' + val
            }
          }
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
    selectHistoryByDevice (deviceId) {
      this.$get(`/cos/device-history-info/selectHistoryByDevice`, {deviceId}).then((r) => {
        let values = []
        if (r.data.data !== null && r.data.data.length !== 0) {
          if (this.chartOptions1.xaxis.categories.length === 0) {
            this.chartOptions1.xaxis.categories = r.data.data.map(obj => { return obj.date })
          }
          let itemData = { name: '统计', data: r.data.data.map(obj => { return obj.value }) }
          values.push(itemData)
          this.series1 = values
        }
      })
    },
    selectDeviceList () {
      this.$get(`/cos/device-info/list`).then((r) => {
        this.deviceList = r.data.data
        this.loading = false
      })
    },
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
      params.userId = this.currentUser.userId
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
/* 设备列表项样式 */
.device-list-item {
  border-radius: 6px;
  margin-bottom: 10px;
  transition: all 0.3s;
  border: 1px solid #f0f0f0;
}

.device-list-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
  border-color: #e8e8e8;
}

.device-title {
  font-size: 16px;
  font-weight: 500;
  color: #1890ff;
  transition: color 0.3s;
}

.device-title:hover {
  color: #40a9ff;
}

.device-code {
  font-size: 12px;
  color: #fa541c;
  background-color: #fff2e8;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 8px;
}

.device-description {
  margin-top: 8px;
}

.info-row {
  margin-bottom: 6px;
  display: flex;
  justify-content: space-between;
}

.info-label {
  color: #8c8c8c;
  font-size: 13px;
}

.info-value {
  color: #262626;
  font-size: 13px;
  font-weight: 400;
}

.status-row {
  text-align: right;
  margin-top: 8px;
}

.status-tag {
  font-weight: 500;
}

/* 图表容器样式 */
.chart-container {
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7f4 100%);
  padding: 25px;
  border-radius: 10px;
  height: 100%;
}

.chart-card {
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.apex-chart {
  margin: 0 auto;
}

/* 整体布局调整 */
>>> .ant-list-bordered {
  border-radius: 8px;
  border: 1px solid #e8e8e8;
}

>>> .ant-alert {
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
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
</style>
