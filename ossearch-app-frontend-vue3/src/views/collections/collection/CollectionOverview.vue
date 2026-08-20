<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

<!--  <div v-if="loading" class="d-flex justify-content-center">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  </div>-->

  <div class="row mt-4">
    <div class="col-xl-4 col-md-6 mb-4">
      <div class="card bg-primary text-white">
        <div class="card-body d-flex flex-column">
          <h5 class="card-title text-uppercase text-start">Total Public URL's Available<i class="fas fa-cloud float-end"></i></h5>
          <div class="d-flex justify-content-center" v-if="loading">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          <h2 v-if="!loading" class="card-text text-white text-end mt-auto">
            <span v-if="solrCount">{{ solrCount?.count.toLocaleString() }}</span>
            <span v-else>0</span>
          </h2>
<!--          <p v-if="!loading && !solrCount" class="card-text">No Public URL's Available</p>-->
          <!--            <span>Number of Crawls Executed:<span class="float-end">{{ numberOfJobsExecuted }}</span></span>-->
        </div>
        <div class="card-footer d-flex align-items-center justify-content-between">
          <router-link class="small text-white stretched-link" :to="{name: 'indexedUrlsReport', params: {tabName: 'indexedUrlsReport'}}">View Details</router-link>
          <div class="small text-white"><i class="fas fa-angle-right"></i></div>
        </div>
      </div>
    </div>

    <div class="col-xl-4 col-md-6 mb-4">
      <div class="card bg-primary text-white">
        <div class="card-body d-flex flex-column">
          <h5 class="card-title text-uppercase text-start">Last Crawl<i class="fas fa-spider float-end"></i></h5>
          <div class="d-flex justify-content-center" v-if="loading">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          <template v-if="!loading && Object.keys(crawlLogs).length === 0">
            <h2 class="card-text text-white text-start mt-auto">
              <span>No Crawls Found</span>
            </h2>
<!--            <p class="card-text">No Crawls Found</p>-->
          </template>
          <template v-if="!loading && Object.keys(crawlLogs).length > 0">
            <ul v-if="!loading" class="card-text list-unstyled">
              <li class="d-flex justify-content-between align-items-center">
                Started:
                <span>{{ getLocalDateTime(crawlLogs.createdDate) }}</span>
              </li>
              <li class="d-flex justify-content-between align-items-center">
                State:
                <span>{{ crawlLogs.state }} {{crawlLogs.updatedDate}}</span>
              </li>
              <li class="d-flex justify-content-between align-items-center">
                Fetched URL's:
                <span>{{ numberComma(crawlLogs.fetched) }}</span>
              </li>
              <li class="d-flex justify-content-between align-items-center">
                Unfetched URL's:
                <span>{{ numberComma(crawlLogs.unfetched) }}</span>
              </li>
            </ul>
          </template>
        </div>
        <div class="card-footer d-flex align-items-center justify-content-between">
          <router-link v-if="Object.keys(crawlLogs).length > 0" class="small text-white stretched-link" :to="loading ? '/scheduler' : {name: 'crawlSteps', params: { jobName: crawlLogs?.jobName, jobGroup: crawlLogs?.jobGroup, jobId: crawlLogs?.jobId}}">View Details</router-link>
<!--          <a :href="'/scheduler/logs/'+crawlLogs?.jobGroup+'/'+crawlLogs?.jobName+'/'+crawlLogs?.jobId" class="small text-white stretched-link">View Details</a>-->
          <router-link v-else :to="loading ? '/scheduler/create' : {name: 'crawlScheduleForm', query: { collectionId: collection.id, collectionName: collection.name }}" class="small text-white stretched-link">Create Crawl Schedule</router-link>
          <div class="small text-white"><i class="fas fa-angle-right"></i></div>
        </div>
      </div>
    </div>

    <div class="col-xl-4 col-md-6 mb-4">
      <div class="card bg-primary text-white">
        <div class="card-body d-flex flex-column">
          <h5 class="card-title text-uppercase text-start">Total User's Searches<i class="fas fa-search float-end"></i></h5>
          <span v-if="!loading" class="card-subtitle">Last 30 days</span>
          <div class="d-flex justify-content-center" v-if="loading">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          <h2 v-if="!loading" class="card-text text-white text-end mt-auto"><span>{{ numberComma(searchesCount.searchCount) }}</span></h2>
        </div>
        <div class="card-footer d-flex align-items-center justify-content-between">
          <router-link class="small text-white stretched-link" :to="{name: 'searchReport', params: {tabName: 'searchReport'}}">View Details</router-link>
          <div class="small text-white"><i class="fas fa-angle-right"></i></div>
        </div>
      </div>
    </div>
  </div>


  <div v-if="!loading" class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>Basic Information</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditBasicInfo = JSON.parse(JSON.stringify(collection)); isEditBasicInfo = !isEditBasicInfo" v-if="!isEditBasicInfo">Edit</button>
        <button v-if="isEditBasicInfo" class="btn btn-sm btn-success me-md-2" type="button" @click="saveBasicInfo()">Save</button>
        <button v-if="isEditBasicInfo" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditBasicInfo; isEditBasicInfo = false">Cancel</button>
      </div>
    </div>
    <div class="card-body">
      <fieldset :disabled="!isEditBasicInfo">
        <div class="row g-3 mb-3">
          <div class="col-md-6">
            <div class="form-floating">
<!--              <input v-model="collection.name" class="form-control" id="collectionName" :class="collection.name.length > 0 && isEditBasicInfo ? (isValidName ? 'is-valid' : 'is-invalid') : ''"  required placeholder="Collection Name" disabled>-->
              <input v-model="collection.name" class="form-control" id="collectionName" required placeholder="Collection Name" disabled>
              <label for="collectionName">Collection Name</label>
            </div>
          </div>
        </div>
        <div class="row g-3">
          <div class="col-md-12">
            <div class="form-floating">
                <textarea v-model="collection.description" type="text" class="form-control"
                          style="height: 100px"
                          id="description" required
                          placeholder="Description"></textarea>
              <label for="description" class="form-label">Description</label>
            </div>
          </div>
        </div>
      </fieldset>
    </div>
  </div>

  <div class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>Preview Search</b>
    </div>
    <div class="card-body">
<!--      <div class="input-group input-group-sm">
        <input type="text" class="form-control" placeholder="Site URL" aria-label="Preview Search" v-model="search">
        <a class="btn btn-primary btn-sm" :href="previewSearch()" target="_blank" role="button">Preview</a>
      </div>-->

<!--      <div class="input-group input-group-sm mt-3">
        <input type="text" class="form-control" placeholder="search string..." aria-label="Preview Search" v-model="search" @keyup.enter="searchPreview = previewSearch()">
        <button class="btn btn-primary btn-sm" type="button" id="button-preview" @click="searchPreview = previewSearch()">Search</button>
        <button class="btn btn-secondary btn-sm" type="button" id="button-resetPreview" @click="search = null">Reset</button>
      </div>
      <div class="col-md-12 mt-3">
        <pre class="shiki" v-html="searchPreview"></pre>
      </div>-->

      <div class="input-group input-group-sm mt-3">
        <input type="text" class="form-control" placeholder="search string..." aria-label="Preview Search" v-model="search" @keyup.enter="searchUrl = previewSearch()">
        <button class="btn btn-primary btn-sm" type="button" id="button-preview" @click="searchUrl = previewSearch()">Search</button>
        <button class="btn btn-secondary btn-sm" type="button" id="button-resetPreview" @click="search = null">Reset</button>
      </div>
      <div v-if="searchUrl" class="col-md-12 mt-3"><b>Search URL:</b> <a class="link-primary" :href="previewSearch()" target="_blank" role="button">{{searchUrl}}</a></div>
<!--      <div v-if="searchUrl" class="col-md-12 mt-3">-->
<!--        <iframe type="text/plain" style="'width: 100%; height: 100vh;'" :src="getSearch(searchUrl)" target="_parent" title="Search Preview" allowfullscreen></iframe>-->
<!--      </div>-->

    </div>
  </div>





<!--  <div class="card">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>JSON Review</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_collectionShowJson'" v-model="showJson">
      </div>
    </div>
    <div class="card-body" v-if="showJson">
      <pre>{{ print(collection) }}</pre>
    </div>
  </div>-->

  <!-- Validation Modal -->
  <div class="modal fade" :class="{ show: validationModelShow, 'd-block': validationModelShow }" id="createValidationModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1" aria-labelledby="createScheduleModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="createScheduleModalLabel">Required Fields Error</h5>
          <button type="button" class="btn-close" aria-label="Close" @click="validationModelShow = !validationModelShow"></button>
        </div>
        <div class="modal-body" :class="{'modal-open': validationModelShow}" >
          <strong class="text-danger">{{ validationMessage }}</strong>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="validationModelShow = !validationModelShow;">OK</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import CollectionService from "../../../services/collection.service";
import ServerStatusService from "../../../services/server-status.service";
import SchedulerService from "../../../services/scheduler.service";
import SearchLogService from "../../../services/searchLog.service";
import axios from "axios";
// const beautify = require('js-beautify')
// const shiki = require('shiki')



export default {
  name: "CollectionOverview",
  props: ['name', 'tabName'],
  components: {

  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: true,
      collection: null,
      collections: [],
      solrCount: null,
      searchesCount: null,
      latestCrawlStats: null,
      crawlLogs: {},
      tableOptions: {

      },
      validationModelShow: false,
      validationMessage: null,
      isValidName: true,
      isEditBasicInfo: false,
      beforeEditBasicInfo: null,
      search: '',
      searchUrl: null,
      searchPreview: null,
      /*testpreview: '<GSP VER="3.2"><Q>test</Q><PARAM name="q" value="test" original_value="test"/><PARAM name="site" value="aaa_v2" original_value="aaa_v2"/><PARAM name="output" value="xml" original_value="xml" url_escaped_value="xml" js_escaped_value="xml"/><PARAM name="proxyreload" value="0" original_value="0"/><PARAM name="oe" value="UTF-8" original_value="UTF-8" url_escaped_value="UTF-8" js_escaped_value="UTF-8"/><PARAM name="ie" value="UTF-8" original_value="UTF-8" url_escaped_value="UTF-8" js_escaped_value="UTF-8"/><PARAM name="ip" value="160.111.100.68" original_value="160.111.100.68"/></GSP>',
      preview: null*/
    }
  },
  created() {
    // watch the params of the route to fetch the data again
    this.$watch(
        () => this.$route.params.name,
        async () => {
          await this.fetchData()
        },
        // fetch the data when the view is created and the data is
        // already being observed
        { immediate: true }
    )
    // return this.v$.$touch;
  },
  /*async mounted() {
    this.preview = await this.highlighter(this.testpreview)
  },*/
  watch: {
    collection: {
      deep: true,
      handler: function () {
        if (this.collections) {
          if (this.collections.some(collection => {
            if (this.beforeEditBasicInfo?.name === this.$route.params.name) {
              return false
            } else {
              return collection.name === this.$route.params.name
            }
          })) {
            console.log(this.$route.params.name, "exists already")
            this.isValidName = false
          } else {
            this.isValidName = true
          }
        }
      }
    }
  },
  computed: {
    isValid() {
      return this.checkIsValid()
    }
  },
  methods: {
    async fetchData() {
      this.loading = true
      //console.log(">>>> params", this.$route.params, "query", this.$route.query)
      if (Object.keys(this.$route.params).length !== 0) {
        //console.log(">>>> params", this.$route.params, "query", this.$route.query)
        this.error = this.collection = null
        await this.getCollection()
        await this.getCollections()
        await this.getSearchLogCounts()
        await this.getSolrCollectionCounts()
        await this.getCrawlLogs()
      }
      this.loading = false
    },
    async getCollection() {
      // console.log("collection name", fetchedId)
      // let url = 'collection/search/getCollectionEntitiesByName'
      // let params = {projection: 'collectionFormData', name: name}

      //console.log("collectionId", JSON.stringify(this.$route.params.id))
      // let url = '/collection/search/getCollectionByName'
      // let params = {name: this.$route.params.name, projection: 'collectionFormData'}

      //console.log("[getCollections] url: " + url + ", prams: " + JSON.stringify(params), null, 2)

      await CollectionService.getCollections('/collection/search/getCollectionByName', {
        name: this.$route.params.name,
        projection: 'collectionFormData'
      })
          .then(response => {
            let data = response.data;
            this.collection = data;
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async getCollections() {
      await CollectionService.getCollections('/collection', {projection: 'collectionIdNameInfo', size: 1000})
          .then(response => {
            let data = response.data;

            if (this.collectionId) {
              this.collections = data._embedded.collection.filter(collection => collection.id !== this.collection.id );
            } else {
              this.collections = data._embedded.collection;
            }

            this.collections.sort((a, b) => a.name.localeCompare(b.name));
          })
          .catch(errors => {
            console.log(errors);
          });
    },
    async getSearchLogCounts() {
      await SearchLogService.get("/search-stats/last-30-days", {collectionId: this.collection.id})
          .then(response => {
            this.searchesCount = response.data
          }).catch(errors => {
            this.error = errors
          })
    },
    async getSolrCollectionCounts() {
      await ServerStatusService.getSolrCollectionCounts()
          .then(response => {
            let solrCounts = response.data.data;
            this.solrCount =
                solrCounts.find(obj => {
                  // console.log("solr count: ", obj)
                  return obj.name === this.$route.params.name
                })
          })
          .catch(errors => {
            this.error = errors
          })
    },
    async getCrawlLogs() {
      // http://localhost:8484/api/crawllog/search/getCrawlLogsByJobKey?jobKey=scheduled_crawl.collection1
      let jobKey = "scheduled_crawl." + this.$route.params.name
      await SchedulerService.getCrawlLogs('crawllog/search/getCrawlLogsByJobKey', {
        jobKey: jobKey,
        projection: 'crawlLogInfo'
      })
          .then(res => {
            // console.log("getCrawlLogs result:", res.data)
            let crawlLogs = res.data._embedded.crawllog

            if (crawlLogs.length > 0) {
              let crawlLog = crawlLogs[crawlLogs.length - 1]
              console.log("crawlLog result:", crawlLog)

              this.crawlLogs = {
                jobKey: crawlLog.jobKey,
                jobId: crawlLog.jobId,
                createdDate: crawlLog.createdDate,
                state: crawlLog.state
              }

              if (crawlLog.state === 'FINISHED') {
                this.crawlLogs.updatedDate = crawlLog.updatedDate
              }

              if (crawlLog?.jobConfig) {
                let json = JSON.parse(crawlLog.jobConfig)
                this.crawlLogs.jobGroup = json.jobGroup
                this.crawlLogs.jobName = json.jobName
              } else {
                this.crawlLogs.jobName = crawlLog.jobKey.split('.')[1]
                this.crawlLogs.jobGroup = crawlLog.jobKey.split('.')[0]
              }

              if (crawlLog?.dbStats) {
                // console.log("dbStats", crawlLog.dbStats)
                let json = JSON.parse(crawlLog.dbStats)
                // console.log("JSON parse result", json)
                crawlLog.dbStats = json

                Object.keys(crawlLog.dbStats.status).forEach(key => {
                  if (crawlLog.dbStats.status[key].statusValue === 'db_fetched') {
                    this.crawlLogs.fetched = crawlLog.dbStats?.status[key]?.count
                  }
                  if (crawlLog.dbStats.status[key].statusValue === 'db_unfetched') {
                    this.crawlLogs.unfetched = crawlLog.dbStats?.status[key]?.count
                  }
                })
              }
            }

          })
          .catch(errors => {
            // console.log(errors);
            this.error = errors
          })
    },
    previewSearch() {
      let url = 'https://ossearch.si.edu/search'
      url = process.env.VUE_APP_API_BASE_URL + '/search'
      let params = {
        q: this.search ? this.search : '*',
        site: this.collection?.name,
        client: this.collection?.name,
        output: 'xml',
        proxyreload: 0,
        getfields: '*'
      }

      url = new URL(url)
      url.search = new URLSearchParams(params)
      console.log("previewSearch", url.toString())

      return url.toString()

      /*const headers = { "Accept": "xml" };
      fetch(url.toString(), headers)
          .then(async response => {
            const data = await response.text();

            // check for error response
            if (!response.ok) {
              // get error message from body or default to response statusText
              const error = (data && data.message) || response.statusText;
              return Promise.reject(error);
            }

            this.totalVuePackages = data;
          })
          .catch(error => {
            this.errorMessage = error;
            console.error("There was an error!", error);
          });*/

      /*let xhr = new XMLHttpRequest();
      xhr.onreadystatechange = () => console.log(xhr.responseText)
      xhr.open('GET', url.toString());
      xhr.send();*/

    },
    getSearch(url) {
      axios.get(url.toString())
          .then(response => {
            console.log(response.data)
            return response.data
          })
          .catch(errors => {
            console.log(errors)
            // this.error = errors
          })
    },
    getLocalDateTime(utc) {
      let u = utc + 'Z'
      return new Date(u).toLocaleString()
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    /*async highlighter(xml) {
      let text = beautify.html_beautify(xml)
      await shiki.setCDN('https://unpkg.com/shiki/');
      await shiki
          .getHighlighter({
            theme: 'light-plus'
          })
          .then(highlighter => {
            text = highlighter.codeToHtml(text, { lang: 'xml' })
          })
      return text
    },*/
    checkIsValid () {
      if (!this.loading) {
        if (this.collection.name.length === 0 || !this.isValidName) {
          this.validationMessage = 'Must provide a valid collection name!'
          return false
        }
      }

      this.validationMessage = ''
      return true
    },
    async saveBasicInfo() {
      if (!this.isValid) {
        this.validationModelShow = !this.validationModelShow
      }  else {
        //let url = '/collection/'+this.collection.id
        let body = {
          name: this.collection.name,
          siteUrl: this.collection.siteUrl,
          description: this.collection.description,
          keywords: this.collection.keywords
        }
        await CollectionService.updateCollection('/collection/' + this.collection.id, JSON.stringify(body))
            .then(response => {
              let data = response.data;
              // this.collection = data;
              console.log("data", data)
            })
            .catch(errors => {
              //console.log(errors);
              this.error = errors
            });
        // await this.getCollection()
        await this.$router.push({name: 'collection', params: {name: this.collection.name, id: this.collection.id}})
        this.isEditBasicInfo = false
      }
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
