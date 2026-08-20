<template>
  <!--  <div v-if="loading" class="container-fluid px-4 loading">
      <div class="d-flex flex-column align-items-center justify-content-center">
        <div class="row">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <div class="row">
          <strong>Loading</strong>
        </div>
      </div>
    </div>-->

  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="container-fluid px-4">
    <!--    <h1 class="mt-4">{{ jobData.jobName }}</h1>-->
    <h1 class="mt-4">Crawl History for {{ $route.params.jobName }}</h1>
    <Breadcrumb/>
    <!--    <button type="button" class="btn btn-danger" data-bs-dismiss="modal" @click="createCrawlSchedule()">Create
        </button>-->

    <!-- Crawl Scheduler Details -->
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Crawl History</b>
      </div>
      <div class="card-body">
        <Datatable :loading="loading"
                   :tableData="crawlLogs"
                   :tableOptions="tableOptions.crawlLogs"
                   id="crawlLogsTable"
        >
          <template v-slot:table-body>
            <tr v-for="(crawLog, i) in crawlLogs" :class="selectedJobId === crawLog['jobId'] ? 'table-active' :''" :key="i">
              <template v-for="column in tableOptions.crawlLogs.columns" :key="column">
                <td v-if="!['state', 'progress', 'view', 'dbStats', 'errors', 'rounds', 'currentRound', 'currentStep', 'createdDate', 'updatedDate'].includes(column.name)">
                  {{ crawLog[column.name] }}
                </td>
                <td v-if="column.name === 'currentStep'">
                  {{ JSON.parse(crawLog?.jobConfig)?.reindex ? 'REINDEX' : crawLog[column.name] }}
                </td>
                <td v-if="['rounds', 'currentRound'].includes(column.name)">
                  {{ !JSON.parse(crawLog?.jobConfig)?.reindex ? crawLog[column.name] : '' }}
                </td>
                <td v-if="column.name === 'dbStats'">
                  <a href="#" v-if="crawLog[column.name]" class="link-primary" data-bs-toggle="modal"
                     data-bs-target="#crawlStats_crawlLogs" @click="selectedJob = crawLog">db:
                    {{ numberComma(crawLog[column.name]?.status?.['2']?.['count']) }} / indexed: {{ crawLog?.solrCount.toLocaleString() }}
                  </a>
                </td>
                <td v-if="column.name === 'state'">
                  <span v-if="crawLog[column.name] === 'failed'.toUpperCase()"
                        class="badge rounded-pill bg-danger text-danger bg-opacity-25">{{ crawLog[column.name] }}</span>
                  <span v-else-if="crawLog[column.name] === 'stopped'.toUpperCase()"
                        class="badge rounded-pill bg-warning text-warning bg-opacity-25">{{
                      crawLog[column.name]
                    }}</span>
                  <span v-else-if="crawLog[column.name] === 'finished'.toUpperCase()"
                        class="badge rounded-pill bg-success text-success bg-opacity-25">{{
                      crawLog[column.name]
                    }}</span>
                  <span v-else class="badge rounded-pill bg-primary text-secondary bg-opacity-25">{{
                      crawLog[column.name]
                    }}</span>
                </td>
                <td v-if="column.name === 'progress'">
                  <div class="progress position-relative">
                    <template v-if="JSON.parse(crawLog?.jobConfig)?.reindex">
                      <div class="progress-bar" role="progressbar"
                           :style="'width: '+(crawLog.state === 'Running' ? 0 : 1)*100+'%'"
                           :aria-valuenow="(crawLog.state === 'Running' ? 0 : 1)*100" aria-valuemin="0"
                           :aria-valuemax="1"><small class="justify-content-center d-flex position-absolute w-100"
                                                     :class="((crawLog.state === 'Running' ? 0 : 1)*100) < 50 ? 'text-dark' : ''">{{
                          (crawLog.state === 'Running' ? 0 : 1) * 100
                        }}%</small></div>
                    </template>
                    <template v-else>
                      <div class="progress-bar" role="progressbar" :style="'width: '+getProgress(crawLog)+'%'"
                           :aria-valuenow="crawLog['currentRound']" aria-valuemin="0"
                           :aria-valuemax="crawLog['rounds']"><small
                          class="justify-content-center d-flex position-absolute w-100"
                          :class="getProgress(crawLog) < 50 ? 'text-dark' : ''">{{ getProgress(crawLog) }}%</small>
                      </div>
                    </template>
                  </div>
                </td>
                <td v-if="column.name === 'view'" class="justify-content-evenly text-center">
                  <div class="btn-group btn-group-sm align-items-center">
                    <router-link class="btn link-primary p-0 m-1"
                                 :to="{ name: 'crawlSteps', params: { jobName: $route.params.jobName, jobId: crawLog.jobId }}"
                                 @click="selectedJobId = crawLog['jobId']"><i class="fas fa-tasks"></i></router-link>
                  </div>
                </td>
                <td v-if="column.name === 'errors'" class="text-danger text-truncate" style="max-width: 250px;"
                    data-bs-toggle="tooltip" data-bs-placement="top" :title="crawLog[column.name]">
                  <a href="#" v-if="crawLog[column.name]" class="link-primary"
                     data-bs-toggle="modal" data-bs-target="#crawlErrors" @click="selectedJob = crawLog">errors
                  </a>
                </td>
                <td v-if="['createdDate', 'updatedDate'].includes(column.name)">
                  {{ getLocalDateTime(crawLog[column.name]) }}
                </td>
              </template>
            </tr>
          </template>
        </Datatable>
      </div>
    </div>

    <CrawlDbStatsModal id="crawlStats_crawlLogs" :data="selectedJob"/>

    <ErrorModal id="crawlErrors" :data="selectedJob"/>

    <router-view></router-view>

  </div>
</template>

<script>
import Breadcrumb from "../../components/Breadcrumb";
import Datatable from "../../components/table/Datatable";
import SchedulerService from "../../services/scheduler.service";
import CrawlDbStatsModal from "../../components/CrawlDbStatsModal";
import ErrorModal from "../../components/ErrorModal";

export default {
  name: "CrawlLogs",
  components: {
    Breadcrumb,
    Datatable,
    CrawlDbStatsModal,
    ErrorModal
  },
  data() {
    return {
      loading: false,
      error: null,
      crawlLogs: null,
      selectedJobId: null,
      selectedJob: null,
      tableOptions: {
        crawlLogs: {
          // enableImport: true,
          // enableAddRow: true,
          // enableActions: true,
          order: [[0, "desc"]],
          columns: [
            {label: 'JobId', name: 'jobId'},
            {label: 'State', name: 'state'},
            {label: 'Type', name: 'jobType'},
            {label: 'On Step', name: 'currentStep'},
            {label: 'Progress', name: 'progress'},
            {label: 'Round', name: 'currentRound'},
            {label: 'MaxRounds', name: 'rounds'},
            {label: 'Total URL\'s', name: 'dbStats'},
            {label: 'Errors', name: 'errors'},
            {label: 'Start Time', name: 'createdDate'},
            {label: 'End Time', name: 'updatedDate'},
            {label: 'Step Logs', name: 'view'},
          ]
        },
      },
    }
  },
  mounted() {
    this.fetchData()
    // watch the params of the route to fetch the data again
    /*this.$watch(
        () => this.$route.params,
        () => {
          this.fetchData()
        },
        // fetch the data when the view is created and the data is
        // already being observed
        {immediate: true}
    )*/
  },
  methods: {
    async fetchData() {
      this.loading = true
      if (Object.keys(this.$route.params).length !== 0) {
        //console.log(">>>> params", this.$route.params, "query", this.$route.query)
        this.error = this.crawlLogs = null
        // await this.getCrawlLogs(this.$route.params.jobName, this.$route.params.jobGroup)
        await this.getCrawlLogs(this.$route.params.jobName)
        if (this.$route.params?.jobId) {
          let selectedJob = this.crawlLogs.find(obj => { return obj.jobId === this.$route.params.jobId})
          this.selectedJobId = selectedJob.jobId
        }
      }
      this.loading = false
    },
    async getCrawlLogs(jobName) {
      // http://localhost:8484/api/crawllog/search/getCrawlLogsByJobKey?jobKey=scheduled_crawl.collection1
      /*let jobKey = jobGroup + "." + jobName
      await SchedulerService.getCrawlLogs('crawllog/search/getCrawlLogsByJobKey', {
        jobKey: jobKey,
        projection: 'crawlLogInfo'
      })*/
      await SchedulerService.getCrawlLogs('crawllog/search/getCrawlLogsByJobKeyEndsWith', {
        jobName: jobName,
        projection: 'crawlLogInfo'
      })
          .then(res => {
            // console.log("getCrawlLogs result:", res.data)
            this.crawlLogs = res.data._embedded.crawllog
            // console.log("getCrawlLogs result:", this.crawlLogs)
            this.crawlLogs.forEach(crawlLog => {
              if (crawlLog.dbStats) {
                // console.log("dbStats", crawlLog.dbStats)
                let json = JSON.parse(crawlLog.dbStats)
                // console.log("JSON parse result", json)
                crawlLog.dbStats = json
              }
            })
          })
          .catch(errors => {
            console.log(errors);
            this.error = errors
          })
    },
    getProgress(crawLog) {
      if (crawLog['currentRound'] === crawLog['rounds'] && crawLog['state'] !== 'FINISHED') {
        return 99
      }
      let percent = Math.trunc(((crawLog['currentRound']) / crawLog['rounds']) * 100)

      if (percent > 100 || crawLog['state'] === 'FINISHED') {
        return 100
      }
      return percent
    },
    getLocalDateTime(utc) {
      let u = utc + 'Z'
      return new Date(u).toLocaleString()
    },
    numberComma(value) {
      if (typeof value === 'number') {
        return value.toLocaleString()
      } else if (!isNaN(value)) {
        return Number(value).toLocaleString()
      }
      return value
    }
  },

}
</script>

<style scoped>

</style>
