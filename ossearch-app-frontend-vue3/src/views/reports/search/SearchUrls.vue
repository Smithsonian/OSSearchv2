<template>
  <div class="container-fluid px-4">
<!--    <h1 class="mt-4">Search Reports</h1>-->
    <Breadcrumb/>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-sitemap me-1"></i>
        <b>Search Report</b>
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
              <tr v-for="(row, i) in searchLogs" :key="i">
                <td v-for="(col, i) in tableOptions.columns" :key="i" :class="col.class ? col.class : '' " :style="col.style ? col.style : ''">{{ col.name === 'createdDate' ? getLocalDateTime(row[col.name]) : row[col.name] }}</td>
              </tr>
            </template>
          </DatatableServerSideSortingPagingSearch>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import moment from "moment/moment";
import CollectionService from "../../../services/collection.service";
import SearchLogService from "../../../services/searchLog.service";
import Breadcrumb from "../../../components/Breadcrumb";
import DateRangePicker from "../../../components/DateRangePicker";
import DatatableServerSideSortingPagingSearch from "../../../components/table/DatatableServerSideSortingPagingSearch";
// import Chart from "../../../components/Chart";
// import _ from 'lodash';

export default {
  name: "SearchUrls",
  components: {
    Breadcrumb,
    DateRangePicker,
    DatatableServerSideSortingPagingSearch,
    // Chart
  },
  data() {
    return {
      loading: false,
      error: null,
      startDate: moment().utc().subtract(1, 'years'),
      // startDate: moment('2019-03-01').utc().startOf('day'),
      endDate: moment().utc().endOf('day'),
      picker: {},
      apiParams: {
        // startDate: moment('2019-03-01').utc().startOf('day').format(),
        startDate: moment().subtract(1, 'years').utc().startOf('day').format(),
        endDate: moment().utc().endOf('day').format()
      },
      tableOptions: {
        order: [[6, "desc"]],
        columns: [
          {label: 'Collection', name: 'collection', style: 'width: 15%'},
          {label: 'Query', name: 'query', style: 'width: 15%'},
          // {label: 'Fields', name: 'fields'},
          // {label: 'Rows', name: 'numRows'},
          // {label: 'Start', name: 'start'},
          {label: 'Num Found', name: 'docsFound', style: 'width: 5%'},
          {label: 'Type', name: 'responseType', style: 'width: 5%'},
          {label: 'QTime (ms)', name: 'elapsedTime', style: 'width: 5%'},
          {label: 'Request IP', name: 'requestIp', style: 'width: 5%'},
          {label: 'Errors', name: 'errors', style: 'width: 5%'},
          {label: 'Date', name: 'createdDate', style: 'width: 8%'},
          // {label: 'Actions', data: 'actions', class: 'text-center'},
        ],
        lengthMenu: [
          [10, 25, 50, 100, 500, 1000],
          [10, 25, 50, 100, 500, 1000]
        ],
      },
      collections: [],
      urlData: [],
      search: '',
      searchLogs: null,
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
      let params = this.getParams()
      url = new URL(process.env.VUE_APP_API_BASE_URL + '/api/reports/search/allUrls', window.location.origin)
      url.search = new URLSearchParams(params)
      console.log("export url", url.toString())
      return url
    },
    rangeSelection() {
      const xaxis = {
        min: moment(this.endDate).subtract(1, 'months').toDate().getTime(),
        max: moment(this.endDate).toDate().getTime()
      }

      const seriesMax = Math.max(...this.chartSeries[0].data.map(o => o[0]))

      xaxis.max = seriesMax

      console.log("startDate", this.startDate.toISOString(), "endDate", this.endDate.toISOString(), "seriesMax", seriesMax)
      console.log("diff years", this.endDate.diff(this.startDate, 'years'))
      console.log("diff months", this.endDate.diff(this.startDate, 'months'))
      console.log("diff weeks", this.endDate.diff(this.startDate, 'weeks'))
      console.log("diff days", this.endDate.diff(this.startDate, 'days'))

      if (this.endDate.diff(this.startDate, 'years') > 1) {
        xaxis.min = moment(this.endDate).subtract(1, 'years').toDate().getTime()
        console.log("years", xaxis.min)
      } else if (this.endDate.diff(this.startDate, 'months') > 1) {
        xaxis.min = moment(this.endDate).subtract(3, 'months').toDate().getTime()
        console.log("months", xaxis.min)
      } else if (this.endDate.diff(this.startDate, 'weeks') > 1) {
        xaxis.min = moment(this.endDate).subtract(2, 'weeks').toDate().getTime()
        console.log("weeks", moment.unix(xaxis.min/1000).toISOString())
      } else if (this.endDate.diff(this.startDate, 'days') > 1) {
        xaxis.min = moment(this.endDate).subtract(1, 'days').toDate().getTime()
        console.log("days", xaxis.min)
      }

      return xaxis
    }
  },
  async mounted() {
    this.loading = true
    await this.getCollections()
    await this.getSearchLogs()
    this.loading = false
  },
  watch: {
    apiParams: {
      handler: async function () {
        this.loading = true
        await this.getSearchLogs()
        this.loading = false
      }
    },
    search: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getSearchLogs()
        this.loading = false
      }
    },
    pageSize: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getSearchLogs()
        this.loading = false
      }
    },
    page: {
      handler: async function () {
        this.loading = true
        await this.getSearchLogs()
        this.loading = false
      }
    },
    tableOptions: {
      deep: true,
      handler: async function () {
        console.log(">>> order", this.tableOptions.order)
        this.loading = true
        await this.getSearchLogs()
        this.loading = false
      }
    },
  },
  methods: {
    async getCollections() {
      await CollectionService.getCollections("/collection", {size: 1000, projection: 'collectionIdNameInfo'})
          .then(response => {
            console.log("collections", JSON.stringify(response.data, null, 2))
            this.collections = response.data._embedded.collection;
            this.collections.sort((a, b) => a.name.localeCompare(b.name));
          })
          .catch(errors => {
            this.error = errors
          })
    },
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
    async getSearchLogs() {
      let params = this.getParams()

      await SearchLogService.get("/searchlog/search/totalCountForAllCollectionsBetweenDatesByCollectionId", params)
          .then(response => {
            let data = response.data
            this.searchLogs = data._embedded.searchlog
            this.recordsTotal = data.page.totalElements
            this.recordsFiltered = data.page.totalElements
            // console.log("data", data)
          }).catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async getSearchLogChartData() {
      let params = this.getParams()
      delete params.page
      delete params.size
      delete params.sort
      delete params.projection
      this.chartSeries = [{name: this.name, data: []}]
      let chartSeries = []
      let datasets = []
      await SearchLogService.get("/searchlog/search/searchLogChartData", params)
          .then(response => {
            // console.log("searchLogChartData", JSON.stringify(response.data, null, 2))
            Object.keys(response.data).forEach(site => {
              // console.log("searchLogChartData site:", site, "data:", JSON.stringify(response.data[site], null, 2))
                datasets.push({
                  label: site,
                  data: response.data[site],
                })

                let data = []
                response.data[site].forEach(row => {
                  // console.log("row", row)
                  data.push([moment(row.date).toDate().getTime(),
                    row.count])
                })

                let series = {
                  name: site,
                  data: data,
                }
                chartSeries.push(series)
            })
          }).catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      this.chartSeries = chartSeries
    },
    updatePicker(picker) {
      // console.log("updatePicker")
      this.apiParams = {
        startDate: picker.startDate.utc().startOf('day').format(),
        endDate: picker.endDate.utc().endOf('day').format()
      }
      this.startDate = picker.startDate.utc().startOf('day')
      this.endDate = picker.endDate.utc().endOf('day')
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
        collectionId: this.collection?.id,
        projection: 'searchLogInfo',
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

<style scoped>

</style>
