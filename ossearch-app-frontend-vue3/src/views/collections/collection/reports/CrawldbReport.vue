<template>

  <div class="row mt-3">
    <template v-if="stats && stats.length === 0">
      <div class="col-xs-12 col-sm-6 col-md-6 col-lg-4 col-xl-3 mb-3">
        <div class="card text-white bg-primary">
          <div class="card-body d-flex flex-column">
            <h5 class="card-title text-uppercase">No Results</h5>
          </div>
        </div>
      </div>
    </template>
    <template v-else>
      <template v-for="status in stats" :key="status.statusName">
        <div class="col-xs-12 col-sm-6 col-md-6 col-lg-4 col-xl-3 mb-3">
          <div class="card text-white" :class="status.statusName.includes('unfetched') ? 'bg-danger' : status.statusName.includes('fetched') ? 'bg-success' : 'bg-primary'">
            <div class="card-body d-flex flex-column">
              <h5 class="card-title text-uppercase">{{ status.statusName.replace(/^db_/,'') }} URL's</h5>
              <span class="card-subtitle"><i class="fas fa-info-circle"></i> {{ desc[status.statusName] }}</span>
              <h2 class="card-text text-end">{{ status.statusCount.toLocaleString() }}</h2>
              <a href="#" class="stretched-link" @click.prevent="statusName = status.statusName"></a>
            </div>
            <!--          <div class="card-footer d-flex align-items-center justify-content-between">
                        <router-link class="small text-white stretched-link" to="/scheduler">View Details</router-link>
                        <div class="small text-white"><i class="fas fa-angle-right"></i></div>
                      </div>-->
          </div>
        </div>
      </template>
    </template>
  </div>

  <div class="card mb-4">
    <div class="card-header">
      <i class="fas fa-sitemap me-1"></i>
      <b>Crawldb Report</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click.prevent="this.$router.go()">Reset</button>
      </div>
    </div>

    <div class="card-body">
      <div class="container-fluid">
        <DatatableServerSideSortingPagingSearch id="crawldbTable" :loading="loading"
                                                :tableOptions="tableOptions"
                                                :columns="tableOptions.columns"
                                                :exportBaseUrl="exportBaseUrl"
                                                v-model:order="tableOptions.order"
                                                v-model:pageSize="pageSize"
                                                v-model:search="search"
                                                v-model:page="page"
                                                :recordsTotal="recordsTotal"
                                                :recordsFiltered="recordsFiltered" @updateOrder="updateOrder">
          <template v-slot:table-body>
            <tr v-for="(row, i) in data" :key="i">
              <td class="text-break" style="width: 100%"><a :href="row['url']" target="_blank" class="link-primary">{{ row['url'] }}</a></td>
              <td class="text-nowrap">{{ row['statusName'] }}</td>
              <td class="text-nowrap">{{ row['fetchTime'] }}</td>
              <td class="d-sm-table-cell justify-content-evenly text-sm-center text-nowrap">
                <router-link class="btn btn-sm link-primary p-0 m-1" :to="{ name: 'urlReport', query: { url: row['url']}, params: {name: name}}" target="_blank">Details</router-link> | <button type="button" class="btn btn-sm btn-link link-danger" data-bs-toggle="modal" data-bs-target="#deleteUrlModal" @click="deleteUrl = row['url']">Delete</button>
              </td>
            </tr>
          </template>
        </DatatableServerSideSortingPagingSearch>
      </div>
    </div>
  </div>

  <!-- Remove Urls Modal -->
  <Modal id="deleteUrlModal" modalClass="modal-md">
    <template v-slot:header>
      <h5 class="modal-title text-black">Delete Url</h5>
    </template>
    <template v-slot:body>
      <p class="text-danger">Are you sure you want to delete url!</p>
      <p class="text-danger"><b>{{ deleteUrl }}</b></p>
    </template>
    <template v-slot:button-cancel>
      <button type="button" class="btn btn-danger" data-bs-dismiss="modal" @click="removeUrl()">Yes</button>
    </template>
    <template v-slot:button-action>
      <button type="button" class="btn btn-secondary" data-bs-dismiss="modal" aria-label="Cancel" @click="deleteUrl = null">No</button>
    </template>
  </Modal>

</template>

<script>
import moment from "moment";
import DatatableServerSideSortingPagingSearch from "../../../../components/table/DatatableServerSideSortingPagingSearch";
import ReportsService from "../../../../services/reports.service";
import CollectionService from "../../../../services/collection.service";
import CrawlUtilsService from "../../../../services/crawlUtils.service";
import Modal from "../../../../components/Modal"

export default {
  name: "CrawldbReport",
  props: ['name', 'tabName'],
  components: {
    DatatableServerSideSortingPagingSearch,
    Modal
  },
  data() {
    return {
      loading: false,
      error: null,
      collection: null,
      tableOptions: {
        order: [[2, "asc"]],
        columns: [
          {label: 'URL', name: 'url'},
          {label: 'Status', name: 'statusName'},
          {label: 'Fetch Time', name: 'fetchTime'},
          {label: 'Actions', name: 'actions', class: 'text-center', sortable: false},
        ],
        lengthMenu: [
          [10, 50, 75, 100, 500, 1000],
          [10, 50, 75, 100, 500, 1000]
        ],
      },
      stats: null,
      search: '',
      statusName: '',
      // sort: 'url,asc',
      data: null,
      page: 1,
      pageSize: 10,
      recordsTotal: 0,
      recordsFiltered: 0,
      deleteUrl: null,
      // exportBaseUrl: null
      desc: {
        db_unfetched: "URL was not fetched yet.",
        db_fetched: "URL was successfully fetched, indexed, and is available for search.",
        db_gone: "URL no longer exists.",
        db_redir_temp: "URL temporarily redirects to other page.",
        db_redir_perm: "URL permanently redirects to other page.",
        db_notmodified: "URL was successfully fetched and found not modified.",
        db_duplicate: "URL was marked as being a duplicate of another page"
      }
    }
  },
  computed: {
    filename() {
     return this.$options.name + '_'+ moment().format("MM-DD-YYYY")
    },
    sort() {
      let sort = []
      this.tableOptions.order.forEach(col => {
        sort.push('sort='+this.tableOptions.columns[col[0]].name + ',' + col[1])
      })
      return sort
    },
    exportBaseUrl() {
      let url = null
      if (this?.collection?.id) {
        let params = this.getParams()
        params.rows = params.size
        delete params.size
        delete params.projection
        delete params.collectionId
        params.export = true

        // url = new URL(process.env.VUE_APP_API_BASE_URL + '/api/webpage/search/findByWebPagesByCollectionIdReport')
        url = new URL(process.env.VUE_APP_API_BASE_URL + '/api/reports/crawldb/' + this.collection.id, window.location.origin)
        url.search = new URLSearchParams(params)

        this.sort.forEach(sortParam => {
          let param = sortParam.split("=")
          url.searchParams.append(param[0], param[1])
        })
      }
      console.log("export url", url)
      return url
    }
  },
  async mounted() {
    this.loading = true
    await this.getCollection()
    await this.getCrawlDbStats()
    await this.getCrawlDbTableData()
    // await this.setExportBaseUrl()
    this.loading = false
  },
  watch: {
    search: {
      handler: async function () {
        this.page = 1
        this.loading = true
        this.statusName = ''
        await this.getCrawlDbStats()
        await this.getCrawlDbTableData()
        // await this.setExportBaseUrl()
        this.loading = false
      }
    },
    statusName: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getCrawlDbStats()
        await this.getCrawlDbTableData()
        // await this.setExportBaseUrl()
        this.loading = false
      }
    },
    pageSize: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getCrawlDbTableData()
        // await this.setExportBaseUrl()
        this.loading = false
      }
    },
    page: {
      handler: async function () {
        this.loading = true
        await this.getCrawlDbTableData()
        // await this.setExportBaseUrl()
        this.loading = false
      }
    },
    tableOptions: {
      deep: true,
      handler: async function () {
        console.log(">>> order", this.tableOptions.order)
        this.loading = true
        await this.getCrawlDbTableData()
        // await this.setExportBaseUrl()
        this.loading = false
      }
    },
    stats: {
      handler: async function () {
        this.stats.sort((a, b) => {
          var sortingArr = ["db_fetched", "db_unfetched", "db_gone", "db_redir_temp", "db_redir_perm", "db_notmodified", "db_duplicate", "db_orphan"];
          return sortingArr.indexOf(a?.statusName) - sortingArr.indexOf(b?.statusName);
        })
      }
    }
  },
  methods: {
    async getCollection() {
      await CollectionService.getCollections('/collection/search/getCollectionByName', {name: this.name, projection: 'collectionFormData'})
          .then(response => {
            this.collection = response.data;
            // console.log("collection", this.collection)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async getCrawlDbStats() {
      let params = this.getParams()
      delete params.page
      delete params.size
      delete params.projection

      await ReportsService.get('/reports/crawldb/stats/'+this.collection.id, params)
          .then(response => {
            this.stats = response.data;
            // console.log("data", this.data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async getCrawlDbTableData() {
      let params = this.getParams()

      let sortParams = ''
      if (this.sort.length > 0) {
        sortParams = '?' + this.sort.join('&')
      }

      // await ReportsService.get('/reports/crawldb/'+this.collectionId, params)
      await ReportsService.get('/webpage/search/findByWebPagesByCollectionId'+sortParams , params)
          .then(response => {
            this.data = response.data._embedded.webpage;
            this.recordsTotal = response.data.page.totalElements
            this.recordsFiltered = response.data.page.totalElements
            // console.log("data", this.data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    updateOrder(order) {
      console.log('>>>Ordering on column ', order)
      this.order = order
    },
    async removeUrl() {
      let body = [this.deleteUrl];
      let params = {
        jobName: this.name
      }
      await CrawlUtilsService.post('/crawl/utils/urls/remove', params, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
            this.deleteUrl = []
          })
          .catch(errors => {
            console.log(errors.message);
            console.log(errors.response);
            console.log(errors.response.data);
            console.log(errors.response.status);
            console.log(errors.response.headers);
            this.error = errors
          });
      await new Promise(resolve => setTimeout(resolve, 3000));
      this.loading = true
      await this.getCrawlDbTableData()
      this.loading = false
    },
    getParams() {
      let params = {
        // sort: this.sort,
        page: this.page - 1,
        size: this.pageSize,
        collectionId: this.collection.id,
        projection: 'webpageInfoTableData'
      }

      if (this.search) {
        params.search = this.search
      }

      if (this.statusName) {
        params.statusName = this.statusName
      }

      return params
    },
    // async setExportBaseUrl() {
    //   let url = null
    //   if (this?.collection?.id) {
    //     let params = this.getParams()
    //     delete params.page
    //     delete params.size
    //     delete params.projection
    //     params.export = true
    //
    //     let url = new URL(process.env.VUE_APP_API_BASE_URL + '/api/webpage/search/findByWebPagesByCollectionIdReport')
    //     url.search = new URLSearchParams(params)
    //
    //     this.sort.forEach(sortParam => {
    //       let param = sortParam.split("=")
    //       url.searchParams.append(param[0], param[1])
    //     })
    //
    //     console.log("export url", url)
    //
    //     return url
    //   }
    // }
  }
}
</script>

<style scoped lang="scss">
.card{
  height: 100%;
}
</style>
