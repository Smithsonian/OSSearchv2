<template>
  <div class="card mt-3 mb-4">
    <div class="card-header">
      <i class="fas fa-sitemap me-1"></i>
      <b>Indexed URLs Report</b>
    </div>

    <div class="card-body">
      <div class="container-fluid">
        <DatatableServerSideSortingPagingSearch id="indexedUrlsTable" :loading="loading"
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
              <td class="d-sm-table-cell text-break text-sm-start" style="width: 45%;"><a :href="row['url']" target="_blank" class="link-primary">{{ row['url'] }}</a></td>
              <td class="d-sm-table-cell text-break text-sm-start" style="width: 45%;">{{ row['title'] }}</td>
<!--              <td class="d-sm-table-cell text-nowrap text-sm-start">{{ row['type'].join(', ') }}</td>-->
              <td class="d-sm-table-cell justify-content-evenly text-sm-center text-nowrap">
                <router-link class="btn btn-sm link-primary p-0 m-1"
                             :to="{ name: 'urlReport', query: { url: row['url']}, params: {name: name}}" target="_blank">Details
                </router-link>
                |
                <button type="button" class="btn btn-sm btn-link link-danger" data-bs-toggle="modal"
                        data-bs-target="#deleteUrlModal" @click="deleteUrl = row['url']">Delete
                </button>
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
      <button type="button" class="btn btn-secondary" data-bs-dismiss="modal" aria-label="Cancel"
              @click="deleteUrl = null">No
      </button>
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
  name: "IndexedUrlsReport",
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
        order: [[0, "asc"]],
        columns: [
          {label: 'URL', name: 'url'},
          {label: 'Title', name: 'title'},
          // {label: 'Type', name: 'type'},
          {label: 'Actions', name: 'actions', class: 'text-center', sortable: false},
        ],
        lengthMenu: [
          [10, 50, 75, 100, 500, 1000],
          [10, 50, 75, 100, 500, 1000]
        ],
      },
      search: '',
      data: null,
      page: 1,
      pageSize: 50,
      recordsTotal: 0,
      recordsFiltered: 0,
      deleteUrl: null
    }
  },
  computed: {
    filename() {
      return this.$options.name + '_' + moment().format("MM-DD-YYYY")
    },
    sort() {
      let sort = []
      this.tableOptions.order.forEach(col => {
        sort.push('sort=' + this.tableOptions.columns[col[0]].name + ' ' + col[1])
      })
      return sort
    },
    exportBaseUrl() {
      let url = null
      if (this?.collection?.id) {
        let params = this.getParams()
        params.export = true

        url = new URL(process.env.VUE_APP_API_BASE_URL + '/api/reports/solr/' + this.collection.id, window.location.origin)
        url.search = new URLSearchParams(params)

        this.sort.forEach(sortParam => {
          let param = sortParam.split("=")
          url.searchParams.append(param[0], param[1])
        })
        console.log(">>>>>>>>>>>export url", url.toString())
      }
      return url
    }
  },
  async mounted() {
    this.loading = true
    await this.getCollection()
    await this.getIndexedUrlsReport()
    this.loading = false
  },
  watch: {
    search: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getIndexedUrlsReport()
        this.loading = false
      }
    },
    pageSize: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getIndexedUrlsReport()
        this.loading = false
      }
    },
    page: {
      handler: async function () {
        this.loading = true
        await this.getIndexedUrlsReport()
        this.loading = false
      }
    },
    tableOptions: {
      deep: true,
      handler: async function () {
        console.log(">>> order", this.tableOptions.order)
        this.loading = true
        await this.getIndexedUrlsReport()
        this.loading = false
      }
    },
  },
  methods: {
    async getCollection() {
      await CollectionService.getCollections('/collection/search/getCollectionByName', {
        name: this.name,
        projection: 'collectionFormData'
      })
          .then(response => {
            this.collection = response.data;
            // console.log("collection", this.collection)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async getIndexedUrlsReport() {
      let params = this.getParams()

      let sortParams = ''
      if (this.sort.length > 0) {
        sortParams = '?' + this.sort.join(',').replaceAll('url', 'id')
      }

      await ReportsService.get('/reports/solr/' + this.collection.id + sortParams, params)
          .then(response => {
            this.data = response.data.data;
            this.recordsTotal = response.data.recordsTotal
            this.recordsFiltered = response.data.recordsFiltered
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
      await this.getIndexedUrlsReport()
      this.loading = false
    },
    getParams() {
      let params = {
        // sort: 'id,asc',
        page: (this.page - 1) * this.pageSize,
        rows: this.pageSize,
      }

      if (this.search) {
        params.search = this.search
      }

      return params
    }
  }
}
</script>

<style scoped lang="scss">

</style>
