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
<!--    <h1 class="mt-4">Dashboard</h1>-->
    <Breadcrumb/>
    <div class="row">
      <div class="col-xl-3 col-md-6 mb-4">
        <div class="card bg-primary text-white">
          <div class="card-body d-flex flex-column">
            <h5 class="card-title text-uppercase">Scheduled Crawls</h5>
            <div class="d-flex justify-content-center" v-if="loading">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <h2 v-if="!loading" class="card-text text-end mt-auto"><i class="fas fa-spider float-start"></i><span>{{ numberComma(scheduledCrawls) }}</span></h2>
<!--            <span class="card-text">Number of Crawls Executed:<span class="float-end">{{ numberOfJobsExecuted }}</span></span>-->
          </div>
          <div class="card-footer d-flex align-items-center justify-content-between">
            <router-link class="small text-white stretched-link" to="/scheduler">View Details</router-link>
            <div class="small text-white"><i class="fas fa-angle-right"></i></div>
          </div>
        </div>
      </div>
      <div class="col-xl-3 col-md-6 mb-4">
        <div class="card bg-primary text-white">
          <div class="card-body d-flex flex-column">
            <h5 class="card-title text-uppercase">Active Collections</h5>
            <div class="d-flex justify-content-center" v-if="loading">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <h2 v-if="!loading" class="card-text text-white text-end mt-auto"><i class="fas fa-sitemap float-start"></i><span>{{ numberComma(collectionCount) }}</span></h2>
<!--            <span>Number of Crawls Executed:<span class="float-end">{{ numberOfJobsExecuted }}</span></span>-->
          </div>
          <div class="card-footer d-flex align-items-center justify-content-between">
            <router-link class="small text-white stretched-link" to="/collections">View Details</router-link>
            <div class="small text-white"><i class="fas fa-angle-right"></i></div>
          </div>
        </div>
      </div>
      <div class="col-xl-3 col-md-6 mb-4">
        <div class="card bg-primary text-white">
          <div class="card-body d-flex flex-column">
            <h5 class="card-title text-uppercase">User's Searches</h5>
            <span v-if="!loadingSearchesCount" class="card-subtitle text-end">Last 30 days</span>
            <div class="d-flex justify-content-center" v-if="loadingSearchesCount">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <h2 v-if="!loadingSearchesCount" class="card-text text-white text-end mt-auto">
              <i class="fas fa-search float-start"></i>
              <span>{{ numberComma(searchesCount.totalSearchCount) }}</span>
            </h2>
          </div>
          <div class="card-footer d-flex align-items-center justify-content-between">
            <router-link class="small text-white stretched-link" to="/search">View Details</router-link>
            <div class="small text-white"><i class="fas fa-angle-right"></i></div>
          </div>
        </div>
      </div>
      <div class="col-xl-3 col-md-6 mb-4">
        <div class="card bg-primary text-white">
          <div class="card-body d-flex flex-column">
            <h5 class="card-title text-uppercase">Total Public URL's Available</h5>
            <div class="d-flex justify-content-center" v-if="loading">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <h2 v-if="!loading" class="card-text text-white text-end mt-auto"><i class="fas fa-cloud float-start"></i><span>{{ numberComma(solrCount) }}</span></h2>
            <!--            <span>Number of Crawls Executed:<span class="float-end">{{ numberOfJobsExecuted }}</span></span>-->
          </div>
          <div class="card-footer d-flex align-items-center justify-content-between">
            <router-link class="small text-white stretched-link" to="/reports">View Details</router-link>
            <div class="small text-white"><i class="fas fa-angle-right"></i></div>
          </div>
        </div>
      </div>
      <div class="col-xl-3 col-md-6 mb-4" v-if="isAdmin">
        <div class="card bg-primary text-white">
          <div class="card-body d-flex flex-column">
            <h5 class="card-title text-uppercase">Users</h5>
            <div class="d-flex justify-content-center" v-if="loading">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <h2 v-if="!loading" class="card-text text-white text-end mt-auto"><i class="fas fa-users float-start"></i><span>{{ numberComma(userCount) }}</span></h2>
            <!--            <span>Number of Crawls Executed:<span class="float-end">{{ numberOfJobsExecuted }}</span></span>-->
          </div>
          <div class="card-footer d-flex align-items-center justify-content-between">
            <router-link class="small text-white stretched-link" to="/users">View Details</router-link>
            <div class="small text-white"><i class="fas fa-angle-right"></i></div>
          </div>
        </div>
      </div>
      <div class="col-xl-3 col-md-6 mb-4">
        <div class="card" :class="systemStatusCardClass">
          <div class="card-body d-flex flex-column">
            <h5 class="card-title text-uppercase">System Status</h5>
            <div class="d-flex justify-content-center" v-if="loading">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <h2 v-if="!loading" class="card-text text-end mt-auto"><i class="fas fa-server float-start"></i><span>{{ systemStatus }}</span></h2>
            <span v-if="!loading && systemStatus === 'DEGRADED'" class="card-text text-end small">Scheduler not running on {{ healthNode }}</span>
            <!--            <span>Number of Crawls Executed:<span class="float-end">{{ numberOfJobsExecuted }}</span></span>-->
          </div>
          <div class="card-footer d-flex align-items-center justify-content-between">
            <router-link class="small stretched-link" :class="systemStatus === 'DEGRADED' ? 'text-dark' : 'text-white'" to="/backend">View Details</router-link>
            <div class="small"><i class="fas fa-angle-right"></i></div>
          </div>
        </div>
      </div>
<!--        <div class="col-xl-3 col-md-6">
            <div class="card bg-success text-white mb-4">
                <div class="card-body">Collections</div>
                <div class="card-footer d-flex align-items-center justify-content-between">
                    <router-link class="small text-white stretched-link" to="/collections">View Details</router-link>
                    <div class="small text-white"><i class="fas fa-angle-right"></i></div>
                </div>
            </div>
        </div>-->
    </div>
<!--    <div class="row">
        <div class="col-xl-6  mb-4">
            <div class="card">
                <div class="card-header">
                  <i class="fas fa-chart-bar me-1"></i>
                  <b>Public URL's Available By Collection</b>
                </div>
                <div class="card-body">

                  <Datatable :loading="loading"
                             :tableData="solrCounts"
                             :tableOptions="tableOptions.solrCounts"
                             id="solrRecordTable"
                  >
                    <template v-slot:table-body>
                      <tr v-for="(collection, i) in solrCounts" :key="i">
                        <td v-if="isAdmin">{{ collection.id }}</td>
                        <td><router-link :to="{ name: 'collection', params: { name: collection.name, id: collection.id }}">{{ collection.name }}</router-link></td>
                        <td>{{ collection.count }}</td>
                      </tr>
                    </template>
                  </Datatable>

                </div>
            </div>
        </div>
        <div class="col-xl-6 mb-4">
            <div class="card">
                <div class="card-header">
                  <i class="fas fa-chart-area me-1"></i>
                  <b>Latest Crawl URL Counts By Collection</b>
                </div>
                <div class="card-body">
                  <Datatable :loading="loading"
                             :tableData="crawlDbStats"
                             :tableOptions="tableOptions.crawlDbStats"
                             :responsive="true"
                             id="dbStatsTable"
                  >
                    <template v-slot:table-body>
                      <tr v-for="(crawLog, i) in crawlDbStats" :key="i">
                        <template v-for="column in tableOptions.crawlDbStats.columns" :key="column">
                          <td v-if="column.name === 'id'">{{ crawLog[column.name] }}</td>
                          <td v-if="column.name === 'jobKey'">
                            <router-link :to="{name: 'crawlLogs', params: { jobName: crawLog[column.name].split('.')[1], jobGroup: crawLog[column.name].split('.')[0] }}">{{ crawLog[column.name] }}</router-link>
                          </td>
                          <td v-if="column.name === 'fetched'">{{ crawLog.dbStats?.status?.['2']?.['count'] ? crawLog.dbStats?.status?.['2']?.['count'] : 0}}</td>
                          <td v-if="column.name === 'unfetched'">{{ crawLog.dbStats?.status?.['1']?.['count'] ? crawLog.dbStats?.status?.['1']?.['count'] : 0 }}</td>
                          <td v-if="column.name === 'solrCount'">{{ crawLog[column.name] }}</td>
                        </template>
                        <td><a class="link-primary" href="#" data-bs-toggle="modal" data-bs-target="#crawlStats_dashboard" @click="selectedDbStats = crawlDbStats[i]">details</a></td>
                      </tr>
                    </template>
                  </Datatable>
                </div>
            </div>
        </div>
    </div>-->
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-chart-bar me-1"></i>
        <b>Public URL's Available By Collection</b>
      </div>
      <div class="card-body">

        <Datatable :loading="loading"
                   :tableData="solrCounts"
                   :tableOptions="tableOptions.solrCounts"
                   id="solrRecordTable"
        >
          <template v-slot:table-body>
            <tr v-for="(collection, i) in solrCounts" :key="i">
              <td v-if="isAdmin">{{ collection.id }}</td>
              <td><router-link :to="{ name: 'collection', params: { name: collection.name, id: collection.id }}">{{ collection.name }}</router-link></td>
              <td class="text-end">{{ collection.count.toLocaleString() }}</td>
            </tr>
          </template>
        </Datatable>

      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-chart-area me-1"></i>
        <b>Latest Crawl URL Counts By Collection</b>
      </div>
      <div class="card-body">
        <Datatable :loading="loading"
                   :tableData="crawlDbStats"
                   :tableOptions="tableOptions.crawlDbStats"
                   :responsive="true"
                   id="dbStatsTable"
        >
          <template v-slot:table-body>
            <tr v-for="(crawLog, i) in crawlDbStats" :key="i">
              <template v-for="column in tableOptions.crawlDbStats.columns" :key="column">
                <td v-if="column.name === 'id'">{{ crawLog[column.name] }}</td>
                <td v-if="column.name === 'jobKey'">
                  <router-link :to="{name: 'crawlLogs', params: { jobName: crawLog[column.name].split('.')[1], jobGroup: crawLog[column.name].split('.')[0] }}">{{ crawLog[column.name] }}</router-link>
                </td>
                <td class="text-end" v-if="column.name === 'fetched'">{{ crawLog.dbStats?.status?.['2']?.['count'] ? numberComma(crawLog.dbStats?.status?.['2']?.['count']) : 0}}</td>
                <td class="text-end" v-if="column.name === 'unfetched'">{{ crawLog.dbStats?.status?.['1']?.['count'] ? numberComma(crawLog.dbStats?.status?.['1']?.['count']) : 0 }}</td>
                <td class="text-end" v-if="column.name === 'solrCount'">{{ crawLog[column.name].toLocaleString() }}</td>
              </template>
              <td class="text-end"><a class="link-primary" href="#" data-bs-toggle="modal" data-bs-target="#crawlStats_dashboard" @click="selectedDbStats = crawlDbStats[i]">details</a></td>
            </tr>
          </template>
        </Datatable>
      </div>
    </div>
  </div>

  <CrawlDbStatsModal id="crawlStats_dashboard" title="Crawl Stats" :data="selectedDbStats"/>

</template>

<script>
import ServerStatusService from "../services/server-status.service";
import SchedulerService from "../services/scheduler.service";
import CollectionService from "../services/collection.service";
import UserService from "../services/user.service";
import Breadcrumb from "../components/Breadcrumb";
import Datatable from "../components/table/Datatable";
import CrawlDbStatsModal from "../components/CrawlDbStatsModal";
import SearchLogService from "../services/searchLog.service";

export default {
  name: "Dashboard",
  components: {
    Breadcrumb,
    Datatable,
    CrawlDbStatsModal
  },
  data() {
    return {
      loading: false,
      loadingSearchesCount: false,
      error: null,
      scheduledCrawls: null,
      numberOfJobsExecuted: null,
      collectionCount: 0,
      searchesCount: 0,
      userCount: 0,
      schedulerStatus: null,
      backedStatus: null,
      healthNode: null,
      solrCount: 0,
      solrCounts: 0,
      crawlDbStats: null,
      selectedDbStats: null,
      tableOptions: {
        solrCounts: {
          order: [[0, "asc"]],
          columns: [
            {label: 'ID', name: 'id', adminOnly: true, style: 'width: 10%'},
            {label: 'Collection Name', name: 'name'},
            {label: 'URL\'s', name: 'count', class: 'text-end'},
          ],
        },
        crawlDbStats: {
          order: [[0, "desc"]],
          columns: [
            // { label: 'Id', name: 'id'},
            { label: 'Scheduled Crawl', name: 'jobKey'},
            { label: 'Crawled', name: 'fetched', class: 'text-end'},
            { label: 'Uncrawled', name: 'unfetched', class: 'text-end'},
            { label: 'Indexed', name: 'solrCount', class: 'text-end'},
            { label: 'Details', name: 'details', class: 'text-end'},
          ]
        },
      },
    }
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
    },
    systemStatus() {
      // DOWN only when core infrastructure (db/ldap/solr) is unhealthy.
      // Behind the load balancer the scheduler runs on a single node, so a
      // "not started" answer from the standby node is expected — show it as
      // DEGRADED with the reporting node's name instead of a hard DOWN.
      if (this.backedStatus !== "UP") {
        return "DOWN"
      }
      return this.schedulerStatus ? "UP" : "DEGRADED"
    },
    systemStatusCardClass() {
      if (this.systemStatus === "UP") {
        return "bg-success text-white"
      }
      return this.systemStatus === "DEGRADED" ? "bg-warning text-dark" : "bg-danger text-white"
    }
  },
  async mounted() {
    this.loading = true
    this.loadingSearchesCount = true
    await this.getScheduledCrawls()
    await this.getSchedulerStatus()
    await this.getCollections()
    await this.getUsers()
    await this.getServerStatus()
    // await this.getSolrCounts()
    await this.getSolrCollectionCounts()
    await this.getCrawlLogStats()
    this.loading = false

    await this.getSearchLogCounts()
    this.loadingSearchesCount = false

  },
  watch: {
    error: {
      deep: true,
      handler: function () {
        // A 403 here must not log the user out: session expiry is handled
        // globally by the axios interceptor (401 -> refresh -> logout), and
        // the WAF can 403 individual requests for a still-valid session.
        let content = (this.error.response && this.error.response.data && this.error.response.data.message) || this.error.message || this.error.toString();
        alert("ERROR: " + content)
      }
    }
  },
  methods: {
    async getScheduledCrawls() {
      await SchedulerService.getAllJobs("/scheduler/getAllJobs2")
          .then(res => {
            this.scheduledCrawls = res.data.length
          })
          .catch(errors => {
            this.error = errors
          })
    },
    async getSchedulerStatus() {
      await ServerStatusService.getSchedulerStatus()
          .then(
              response => {
                let rsp = response.data
                this.numberOfJobsExecuted = rsp.numberOfJobsExecuted
                this.schedulerStatus = rsp.started
              }
          )
          .catch(errors => {
            this.error = errors
          })
    },
    async getCollections() {
      await CollectionService.getCollections("/collection", {size: 1, projection: 'collectionIdNameInfo'})
          .then(response => {
            this.collectionCount = response.data.page.totalElements;
          })
          .catch(errors => {
            this.error = errors
          })
    },
    async getServerStatus() {
      await ServerStatusService.getServerStatus()
          .then(response => {
            this.backedStatus = response.data.status
            this.healthNode = response.data.node
          })
          .catch(() => {
            // A failed health probe must never feed the shared `error`
            // watcher — a stray error here must not force a logout.
            this.backedStatus = 'UNKNOWN'
          })
    },
    async getUsers() {
      await UserService.getUsers('/users', {size: 1, projection: 'userIdNameEmailRoles'})
          .then(response => {
            this.userCount = response.data.page.totalElements;
          })
          .catch(errors => {
            this.error = errors
          })
    },
    async getSolrCollectionCounts() {
      await ServerStatusService.getSolrCollectionCounts()
          .then(response => {
            console.log("getSolrCollectionCounts:", response.data)
            this.solrCounts = response.data.data;
            this.solrCount = response.data.totalCount;
            /*this.solrCount = this.solrCounts.reduce((accumulator, object) => {
              return accumulator + object.count;
            }, 0);*/
          })
          .catch(errors => {
            this.error = errors
          })
    },
    /*async getSolrCounts() {
      await ServerStatusService.getSolr()
          .then(response => {
            this.solrCount = response.data;
          })
          .catch(errors => {
            this.error = errors
          })
    },*/
    async getCrawlLogStats() {
      await ServerStatusService.getCrawlLogStats()
          .then(response => {
            let data = response.data._embedded.crawllog;

            data.forEach(crawlLog => {
              crawlLog.dbStats = JSON.parse(crawlLog.dbStats)
            })

            this.crawlDbStats = data
          })
          .catch(errors => {
            this.error = errors
          })
    },
    async getSearchLogCounts() {
      await SearchLogService.get("/search-stats/last-30-days")
          .then(response => {
            this.searchesCount = response.data
          }).catch(errors => {
            this.error = errors
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

<style lang="scss" scoped>
.card{
  height: 100%;
}
</style>
