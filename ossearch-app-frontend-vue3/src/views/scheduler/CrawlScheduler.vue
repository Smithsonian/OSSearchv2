<template>
<!--  <div v-if="loading" class="container-fluid px-4 loading">
    <div class="d-flex justify-content-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>
  </div>-->

  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="container-fluid px-4">
    <!--    <h1 class="mt-4">Overview</h1>-->
    <Breadcrumb/>

<!--    <button type="button" class="btn btn-primary" @click="$router.push({name: 'crawlScheduleForm', query: { collectionId: 1, collectionName: 'collection1' }})">Test Create Form</button>-->

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-sitemap me-1"></i>
        <b>Scheduled Crawls</b>
        <div v-if="isAdmin" class="float-end">
          <router-link type="button" class="btn btn-sm btn-primary bi-plus-lg float-end" to="/scheduler/create">
            Crawl Schedule
          </router-link>
        </div>
      </div>
      <div class="card-body">
        <Datatable :loading="loading"
            :tableData="scheduledCrawlsTableData"
            :tableOptions="tableOptions.scheduledCrawls"
                   id="crawlSchTable"
        >
          <template v-slot:caption>
            <caption class="caption-top">
              <div class="align-items-center">
                <i class="fas fa-play-circle link-success"></i> = Start Crawling Now,
                <i class="fas fa-pause-circle link-warning"></i> = Pause Scheduled Crawling,
                <i class="fas fa-redo-alt link-secondary"></i> = Resume Scheduled Crawling,
                <i class="fas fa-stop-circle link-danger"></i> = Stop Running Crawl
              </div>
            </caption>
          </template>
          <template v-slot:table-body>
            <tr v-for="(job, i) in scheduledCrawlsTableData" :key="i">
              <!-- Loop Row Cells -->
              <template v-for="(key, j) in tableOptions.scheduledCrawls.columns" :key="j">
                <td v-if="!['jobName', 'jobStatus', 'actions', 'control', 'logs', 'cronExpression', 'finalFiredTime', 'startTime'].includes(key.name)">{{ job[key.name] }}</td>
                <td v-if="key.name === 'cronExpression'">{{ job[key.name] !== "None" ? explain(job[key.name]) : "None" }}</td>
                <td v-if="key.name === 'jobName'">
                  <router-link :class="job['groupName'] !== 'scheduled_crawl' ? 'disabled' : ''" :to="{ name: 'crawlScheduleDetails', params: { jobName: job.jobName, groupName: job.groupName }}">{{ job[key.name] }}</router-link>
                </td>
                <td v-if="key.name === 'jobStatus'">
                  <span v-if="job[key.name] === 'PAUSED'" class="badge rounded-pill bg-danger text-danger bg-opacity-25">{{ job[key.name] }}</span>
                  <span v-else-if="job[key.name] === 'SCHEDULED'" class="badge rounded-pill bg-primary text-secondary bg-opacity-25">{{ job[key.name] }}</span>
                  <span v-else-if="job[key.name] === 'RUNNING'" class="badge rounded-pill bg-success text-success bg-opacity-25">{{ job[key.name] }}</span>
                  <span v-else class="badge rounded-pill bg-warning text-warning bg-opacity-25">{{ job[key.name] }}</span>
                </td>
                <td v-if="key.name === 'actions'" class="justify-content-evenly text-center">
                  <div class="btn-group btn-group-sm align-items-center">
                    <router-link :class="job['groupName'] !== 'scheduled_crawl' ? 'disabled link-secondary' : ''" class="btn-link link-primary p-0 m-2" :to="{ name: 'crawlScheduleDetails', params: { jobName: job.jobName, groupName: job.groupName }}"><i class="fa fa-edit"></i></router-link>
                    <a v-if="isAdmin" href="#" :class="job['groupName'] !== 'scheduled_crawl' ? 'disabled link-secondary' : ''" class="btn-link link-danger p-0" data-bs-toggle="modal" data-bs-target="#deleteModal" title="Delete" @click.prevent="selectedCrawlSchedule = job">
                      <i class="fa fa-times-circle" :class="job['groupName'] !== 'scheduled_crawl' ? 'link-secondary' : ''"></i>
                    </a>
                  </div>
                </td>
                <td v-if="key.name === 'logs'" class="ps-4">
                  <router-link class="btn-link link-primary" :to="{name: 'crawlLogs', params: { jobName: job.jobName, jobGroup: job.groupName }}" target="_blank"><i class="fas fa-eye"></i></router-link>
                </td>
                <td v-if="key.name === 'control'" class="justify-content-evenly text-center">
                  <div class="btn-group btn-group-sm align-items-center">
                    <a :class="job['jobStatus'] === 'RUNNING' || job['groupName'] !== 'scheduled_crawl' ? 'disabled link-secondary' : 'link-success'" href="#" class="btn-link p-0 m-2" data-bs-toggle="tooltip" data-bs-placement="top" title="Start Crawling Now" @click="startCrawl(job)"><i class="fas fa-play-circle"></i></a>
                    <a :class="job['groupName'] !== 'scheduled_crawl' ? 'disabled link-secondary' : 'link-warning'" href="#" class="btn-link p-0 m-2" data-bs-toggle="tooltip" data-bs-placement="top" title="Pause Scheduled Crawling" @click="pauseCrawl(job)"><i class="fas fa-pause-circle"></i></a>
                    <a :class="job['groupName'] !== 'scheduled_crawl' ? 'disabled link-secondary' : 'link-secondary'" href="#" class="btn-link p-0 m-2" data-bs-toggle="tooltip" data-bs-placement="top" title="Resume Scheduled Crawling" @click="resumeCrawl(job)"><i class="fas fa-redo-alt"></i></a>
                    <a href="#" class="btn-link link-danger p-0 m-2" data-bs-toggle="tooltip" data-bs-placement="top" title="Stop Running Crawl" @click="stopCrawl(job)"><i class="fas fa-stop-circle"></i></a>
                  </div>
                </td>
              </template>

            </tr>
          </template>
        </Datatable>
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-history me-1"></i>
        <b>Crawl History</b>
      </div>
      <div class="card-body">
        <Datatable :loading="loading"
                   :tableData="crawlLogsHistoryTableData"
                   :tableOptions="tableOptions.crawlLogsHistory"
                   id="crawlLogsHistoryTable"
        >
          <template v-slot:table-body>
            <tr v-for="(crawlLog, i) in crawlLogsHistoryTableData" :key="i">
              <template v-for="column in tableOptions.crawlLogsHistory.columns" :key="column">
                <td v-if="!['state', 'view', 'collection', 'jobGroup', 'dbStats', 'errors', 'currentStep', 'createdDate', 'updatedDate', 'groupName', 'round'].includes(column.name)">{{ crawlLog[column.name] }}</td>
                <td v-if="column.name === 'round'">{{ crawlLog?.currentRound }} of {{ crawlLog?.rounds }}</td>
                <td v-if="column.name === 'collection'">{{ crawlLog[column.name] }}</td>
<!--                <td v-if="column.name === 'jobGroup'">{{ crawlLog?.jobConfig?.jobGroup }}</td>-->
                <td v-if="column.name === 'currentStep'">{{ crawlLog[column.name] }}</td>
                <td v-if="column.name === 'dbStats'">
                  <a href="#" v-if="crawlLog[column.name]" class="link-primary" data-bs-toggle="modal"
                     data-bs-target="#modal_SchedulerCrawlStats" @click="selectedJob = crawlLog">db:
                    {{ numberComma(crawlLog[column.name]?.status?.['2']?.['count']) }} / indexed: {{ crawlLog?.solrCount.toLocaleString() }}
                  </a>
                </td>
                <td v-if="column.name === 'state'">
                  <span v-if="crawlLog[column.name] === 'failed'.toUpperCase()" class="badge rounded-pill bg-danger text-danger bg-opacity-25">{{ crawlLog[column.name] }}</span>
                  <span v-else-if="crawlLog[column.name] === 'stopped'.toUpperCase()" class="badge rounded-pill bg-warning text-warning bg-opacity-25">{{ crawlLog[column.name] }}</span>
                  <span v-else-if="crawlLog[column.name] === 'finished'.toUpperCase()" class="badge rounded-pill bg-success text-success bg-opacity-25">{{ crawlLog[column.name] }}</span>
                  <span v-else class="badge rounded-pill bg-primary text-secondary bg-opacity-25">{{ crawlLog[column.name] }}</span>
                </td>
                <td v-if="column.name === 'view'" class="justify-content-evenly text-center">
<!--                    <router-link class="btn link-primary" :to="{ name: 'crawlSteps', params: { jobName: crawlLog?.jobConfig?.jobName, jobGroup: crawlLog?.jobConfig?.jobGroup, jobId: crawlLog?.jobId }}"><i class="fas fa-eye"></i></router-link>-->
                    <router-link class="btn link-primary" :to="{ name: 'crawlSteps', params: { jobName: crawlLog.jobKey.split('.')[1], jobGroup: crawlLog.jobKey.split('.')[0], jobId: crawlLog?.jobId }}" target="_blank"><i class="fas fa-eye"></i></router-link>
                </td>
                <td v-if="column.name === 'errors'" class="text-danger text-truncate" style="max-width: 250px;"
                    data-bs-toggle="tooltip" data-bs-placement="top" :title="crawlLog[column.name]">
                  <a href="#" v-if="crawlLog[column.name]" class="link-primary"
                     data-bs-toggle="modal" data-bs-target="#crawlErrors" @click="selectedJob = crawlLog">errors
                  </a>
                </td>
                <td v-if="['createdDate', 'updatedDate'].includes(column.name)">{{ getLocalDateTime(crawlLog[column.name]) }}</td>
              </template>
            </tr>
          </template>
        </Datatable>
      </div>
    </div>
  </div>


  <Modal v-if="selectedCrawlSchedule" id="deleteModal">
    <template v-slot:header>
      <h5 class="modal-title text-black">Delete Scheduled Crawl</h5>
    </template>
    <template v-slot:body>
      <p>Are you sure you want to delete scheduled crawl <b>{{ selectedCrawlSchedule.jobName }} {{ selectedCrawlSchedule.jobGroup }}</b>! This
        can not be undone!</p>
    </template>
    <template v-slot:button-action>
      <button type="button" class="btn btn-danger" data-bs-dismiss="modal"
              @click.prevent="deleteCrawlSchedule(selectedCrawlSchedule.jobName, selectedCrawlSchedule.groupName)">Delete
      </button>
    </template>
  </Modal>

  <CrawlDbStatsModal id="modal_SchedulerCrawlStats" :data="selectedJob"/>

  <ErrorModal id="crawlErrors" :data="selectedJob"/>


</template>

<script>
import Datatable from "../../components/table/Datatable";
import Breadcrumb from "../../components/Breadcrumb";
import Modal from "../../components/Modal";
import SchedulerService from "../../services/scheduler.service";
import CrawlDbStatsModal from "../../components/CrawlDbStatsModal";
import ErrorModal from "../../components/ErrorModal";
import EventBus from "../../common/EventBus";
import cronstrue from "cronstrue";

export default {
  name: "CrawlScheduler",
  components: {
    Breadcrumb,
    Datatable,
    Modal,
    CrawlDbStatsModal,
    ErrorModal
  },
  data() {
    return {
      loading: false,
      error: null,
      tableOptions: {
        scheduledCrawls: {
          order: [[2, "asc"]],
          columns: [
            // { label: 'Id', name: 'id'},
            // { label: 'Job Id', name: 'jobId'},
            { label: 'Control', name: 'control', class: 'text-center'},
            { label: 'History', name: 'logs', class: 'text-center'},
            { label: 'Collection', name: 'jobName'},
            { label: 'Job Group', name: 'groupName'},
            { label: 'Crawling Schedule', name: 'cronExpression'},
            // { label: 'State', name: 'state'},
            { label: 'Status', name: 'jobStatus'},
            // { label: 'Progress', name: 'progress'},
            // { label: 'Description', name: 'description'},
            // { label: 'Start Time', name: 'startTime'},
            { label: 'Last Crawl', name: 'lastFiredTime'},
            { label: 'Next Crawl', name: 'nextFireTime'},
            // { label: 'Final Run', name: 'finalFiredTime'},
            { label: !this.isAdmin ? 'Edit' : 'Edit | Delete', name: 'actions', class: 'text-center'}
          ],
        },
        crawlLogsHistory: {
          order: [],
          columns: [
            { label: 'View', name: 'view', class: 'text-center'},
            // { label: 'JobId', name: 'jobId'},
            { label: 'Collection', name: 'collection'},
            // { label: 'Job Group', name: 'jobGroup'},
            {label: 'Type', name: 'jobType'},
            { label: 'State', name: 'state'},
            { label: 'Round', name: 'round'},
            { label: 'Last Step', name: 'currentStep'},
            { label: 'Total URL\'s', name: 'dbStats'},
            { label: 'Errors', name: 'errors'},
            // { label: 'Start Time', name: 'createdDate'},
            { label: 'Last Update', name: 'updatedDate'},
          ],
        }
      },
      scheduledCrawlsTableData: [],
      crawlLogsHistoryTableData: [],
      selectedCrawlSchedule: {},
      selectedJobId: null,
      selectedJob: null,
      itemIndex: null
    }
  },
  async mounted() {
    console.log("process.env.BASE_URL", process.env.BASE_URL)
    await this.fetchData()
  },
  computed: {
    currentUser() {
      return this.$store.state.auth.user;
    },
    isAdmin() {
      if (this.currentUser && this.currentUser['roles']) {
        return this.currentUser['roles'].includes('ROLE_ADMIN');
      }
      return false;
    }
  },
  methods: {
    async fetchData() {
      this.loading = true
      await this.getAllJobs()
      await this.getCrawlLogs()
      this.loading = false
    },
    async getAllJobs() {
      await SchedulerService.getAllJobs("/scheduler/getAllJobs2")
          .then(res => {
            this.scheduledCrawlsTableData = res.data
            console.log("schedule getAllJobs:", JSON.stringify(this.scheduledCrawlsTableData,null,2))
          })
          .catch(errors => {
            console.log(errors);
            this.error = errors
          })
    },
    async getCrawlLogs() {
      // http://localhost:8484/api/crawllog/search/getCrawlLogsByJobKey?jobKey=scheduled_crawl.collection1

      //await SchedulerService.getCrawlLogs('/crawllog', {size: 100, page: 0, sort: "updatedDate,desc", projection: 'crawlLogInfo'})
      await SchedulerService.getCrawlLogs('/scheduler/crawlLogHistory')
          .then(res => {
            // this.crawlLogsHistoryTableData = res.data
            this.crawlLogsHistoryTableData = res.data
            this.crawlLogsHistoryTableData.forEach(crawlLog => {
              if (crawlLog.dbStats) {
                let json = JSON.parse(crawlLog.dbStats)
                crawlLog.dbStats = json
              }
              let collection = crawlLog?.jobId.slice(0, crawlLog?.jobId.lastIndexOf('_'))
              crawlLog.collection = collection

              if (Object.prototype.hasOwnProperty.call(crawlLog ,'jobConfig')) {
                if (JSON.parse(crawlLog?.jobConfig)?.reindex) {
                  crawlLog.currentStep = 'REINDEX'
                }
              }
              /*if (crawlLog.jobConfig) {
                let json = JSON.parse(crawlLog.jobConfig)
                crawlLog.jobConfig = json
              }*/
            })
          })
          .catch(errors => {
            console.log(errors);
            this.error = errors
          })
    },
    async deleteCrawlSchedule(jobName, jobGroup) {
      await SchedulerService.deleteCrawlJob("/scheduler/delete", { jobName: jobName, jobGroup: jobGroup })
          .then(response => {
            let data = response.data;
            console.log("delete CrawlJobSchedule", JSON.stringify(data, null, 2))
            this.selectedCrawlSchedule = null
          })
          .catch(errors => {
            //console.log(errors);
            // this.error = errors
            let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
            EventBus.dispatch('toast', {
              type: 'danger',
              msg: jobName +' ' + content
            })
          });
      this.getAllJobs()
    },
    async startCrawl(job) {
      await SchedulerService.startCrawlJob("/scheduler/start", {jobName: job.jobName, jobGroup: job.groupName})
          .then(response => {
            let data = response.data;
            console.log("start CrawlJobSchedule", JSON.stringify(data, null, 2))
            EventBus.dispatch('toast', {
              type: 'success',
              msg: job.jobName +' Crawl Started!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            // this.error = errors
            let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
            EventBus.dispatch('toast', {
              type: 'danger',
              msg: job.jobName +' ' + content
            })
          });
      this.getAllJobs()
    },
    async pauseCrawl(job) {
      await SchedulerService.pauseCrawlJob("/scheduler/pause", {jobName: job.jobName, jobGroup: job.groupName})
          .then(response => {
            let data = response.data;
            console.log("pause CrawlJobSchedule", JSON.stringify(data, null, 2))
            EventBus.dispatch('toast', {
              type: 'success',
              msg: job.jobName +' Scheduled Crawling Paused!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            // this.error = errors
            let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
            EventBus.dispatch('toast', {
              type: 'danger',
              msg: job.jobName +' ' + content
            })
          });
      this.getAllJobs()
    },
    async resumeCrawl(job) {
      await SchedulerService.resumeCrawlJob("/scheduler/resume", {jobName: job.jobName, jobGroup: job.groupName})
          .then(response => {
            let data = response.data;
            console.log("resume CrawlJobSchedule", JSON.stringify(data, null, 2))
            EventBus.dispatch('toast', {
              type: 'success',
              msg: job.jobName +' Scheduled Crawling Resumed!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            // this.error = errors
            let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
            EventBus.dispatch('toast', {
              type: 'danger',
              msg: job.jobName +' ' + content
            })
          });
      this.getAllJobs()
    },
    async stopCrawl(job) {
      await SchedulerService.stopCrawlJob("/scheduler/stop", { jobName: job.jobName, jobGroup: job.groupName })
          .then(response => {
            let data = response.data;
            console.log("stop CrawlJobSchedule", JSON.stringify(data, null, 2))
            EventBus.dispatch('toast', {
              type: 'success',
              msg: job.jobName +' Crawl Stopping!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            // this.error = errors
            let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
            EventBus.dispatch('toast', {
              type: 'danger',
              msg: job.jobName +' ' + content
            })
          });
      this.getAllJobs()
    },
    getLocalDateTime(utc) {
      let u = utc//+'Z'
      return new Date(u).toLocaleString()
    },
    explain(cronExpression) {
      return cronstrue.toString(cronExpression, {
        verbose: true,
        use24HourTimeFormat: true,
        dayOfWeekStartIndexZero: false
      })
    },
    numberComma(value) {
      if (typeof value === 'number') {
        return value.toLocaleString()
      } else if (!isNaN(value)) {
        return Number(value).toLocaleString()
      }
      return value
    }
  }
}
</script>

<style scoped>
.disabled {
  color: var(--bs-btn-disabled-color);
  pointer-events: none;
  background-color: var(--bs-btn-disabled-bg);
  border-color: var(--bs-btn-disabled-border-color);
  opacity: var(--bs-btn-disabled-opacity);
}
</style>
