<template>
  <div class="card mt-3">
    <div class="card-header">
      <i class="fas fa-sitemap me-1"></i>
      <b>Keyword Count Report</b>
      <div class="float-end">
        <DateRangePicker :startDate="startDate" :endDate="endDate" @dateRange="updatePicker"/>
      </div>
    </div>

    <div class="card-body">
      <div class="container-fluid">
        <DatatableServerSideSortingPagingSearch id="keywordCountSearchTable" :loading="loading"
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
              <td v-for="(col, i) in tableOptions.columns" :key="i" :class="col.class ? col.class : '' " :style="col.style ? col.style : ''">{{ col.name === 'createdDate' ? getLocalDateTime(row[col.name]) : row[col.name].toLocaleString() }}</td>
            </tr>
          </template>
        </DatatableServerSideSortingPagingSearch>
      </div>
    </div>
  </div>
</template>

<script>
import moment from "moment";
import DateRangePicker from "../../../../components/DateRangePicker";
import DatatableServerSideSortingPagingSearch from "../../../../components/table/DatatableServerSideSortingPagingSearch";
import CollectionService from "../../../../services/collection.service";
import SearchLogService from "../../../../services/searchLog.service";
//import ReportsService from "../../../../services/reports.service";

export default {
  name: "KeywordCountReport",
  props: ['name', 'tabName'],
  components: {
    DatatableServerSideSortingPagingSearch,
    DateRangePicker
  },
  data() {
    return {
      loading: false,
      error: null,
      collection: null,
      startDate: moment().utc().subtract(30, 'days'),
      // startDate: moment('2019-03-01').utc().startOf('day'),
      endDate: moment().utc().endOf('day'),
      picker: {},
      apiParams: {
        // startDate: moment('2019-03-01').utc().startOf('day').format(),
        startDate: moment().subtract(30, 'days').utc().startOf('day').format(),
        endDate: moment().utc().endOf('day').format()
      },
      tableOptions: {
        order: [[0, "asc"]],
        columns: [
          {label: 'Query', name: 'query'},
          {label: 'Count', name: 'wordCount', style: 'width: 10%', class: "text-end"},
          {label: 'Date Range', name: 'dateRange', style: 'width: 20%', class: "text-end"},
          // {label: 'Actions', data: 'actions', class: 'text-center'},
        ],
        lengthMenu: [
          [10, 25, 50, 100, 500, 1000],
          [10, 25, 50, 100, 500, 1000]
        ],
      },
      search: '',
      data: null,
      page: 1,
      pageSize: 10,
      recordsTotal: 0,
      recordsFiltered: 0,
    }
  },
  computed: {
    filename() {
      return this.$options.name + '(' + moment(this.apiParams.startDate).format("MM-DD-YYYY") + '_' + moment(this.apiParams.endDate).format("MM-DD-YYYY") + ')'
    },
    sort() {
      let sort = ""
      this.tableOptions.order.forEach(col => {
        sort = sort + this.tableOptions.columns[col[0]].name //.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`);
        sort = sort + "," +col[1]
      })
      return sort
    },
    exportBaseUrl() {
      let url = null
      if (this?.collection?.id) {
        let params = this.getParams()

        delete params.collectionId
        delete params.projection

        url = new URL(process.env.VUE_APP_API_BASE_URL + '/api/reports/keywordCounts/' + this.collection.id, window.location.origin)
        url.search = new URLSearchParams(params)
        console.log("export url", url.toString())
      }
      return url
    }
  },
  async mounted() {
    this.loading = true
    await this.getCollection()
    await this.getKeywordReport()
    this.loading = false
  },
  watch: {
    apiParams: {
      handler: async function () {
        this.loading = true
        await this.getKeywordReport()
        this.loading = false
      }
    },
    search: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getKeywordReport()
        this.loading = false
      }
    },
    pageSize: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getKeywordReport()
        this.loading = false
      }
    },
    page: {
      handler: async function () {
        this.loading = true
        await this.getKeywordReport()
        this.loading = false
      }
    },
    tableOptions: {
      deep: true,
      handler: async function () {
        console.log(">>> order", this.tableOptions.order)
        this.loading = true
        await this.getKeywordReport()
        this.loading = false
      }
    },
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
    async getKeywordReport() {
      let params = this.getParams()

      await SearchLogService.get("/searchlog/search/keywordCountsBetweenDatesByCollectionId", params)
          .then(response => {
            let data = response.data
            this.data = data.content
            this.recordsTotal = data.totalElements
            this.recordsFiltered = data.totalElements
            // this.data = data._embedded.searchlog
            // this.recordsTotal = data.page.totalElements
            // this.recordsFiltered = data.page.totalElements
            console.log("data", data)
          }).catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    updatePicker(picker) {
      console.log("updatePicker")
      this.apiParams = {
        startDate: picker.startDate.utc().startOf('day').format(),
        endDate: picker.endDate.utc().endOf('day').format()
      }
    },
    updateOrder(order) {
      console.log('>>>Ordering on column ', order)
      this.order = order
    },
    getMoment(date) {
      return moment(date)
    },
    getParams() {
      let params = {
        collectionId: this.collection.id,
        projection: 'searchLogKeywordCountsView',
        startDate: this.apiParams.startDate,
        endDate: this.apiParams.endDate,

        page: (this.page - 1),
        size: this.pageSize,
      }

      if (this.sort) {
        params.sort = this.sort
      }

      params.searchText = this.search ? this.search : ''

      return params
    },
    getLocalDateTime(utc) {
      let u = utc+'Z'
      return new Date(u).toLocaleString()
    },
  }
}
</script>

<style scoped lang="scss">

</style>
