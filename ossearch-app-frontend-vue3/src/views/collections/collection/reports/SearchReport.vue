<template>
  <div class="card mt-3 mb-4">
    <div class="card-header">
      <i class="fas fa-sitemap me-1"></i>
      <b>Search Report</b>
      <div class="float-end">
        <DateRangePicker :startDate="startDate" :endDate="endDate" @dateRange="updatePicker"/>
      </div>
    </div>

    <div class="card-body">
      <div class="container-fluid mb-3">
        <Chart id="mainChart" type="bar" height="230" :series="chartSeries" :options="chartOptionsMain"/>
        <Chart id="selectionChart" type="area" height="130" :series="chartSeries" :options="chartOptionsSelection"/>
      </div>
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
            <template v-if="searchLogs && searchLogs.length">
            <tr v-for="(row, i) in searchLogs" :key="i">
              <td v-for="(col, i) in tableOptions.columns" :key="i" :class="col.class ? col.class : '' " :style="col.style ? col.style : ''">{{ col.name === 'createdDate' ? getLocalDateTime(row[col.name]) : row[col.name] }}</td>
            </tr>
            </template>
            <template v-else>
              <tr>
                <td :colspan="tableOptions.columns.length + 2" class="text-center">No Data</td>
              </tr>
            </template>
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
import Chart from "../../../../components/Chart";
import _ from "lodash";

export default {
  name: "SearchReport",
  props: ['name', 'tabName'],
  components: {
    DatatableServerSideSortingPagingSearch,
    DateRangePicker,
    Chart
  },
  data() {
    return {
      loading: false,
      error: null,
      collection: null,
      startDate: moment().subtract(1, 'years').utc().startOf('day'),
      // startDate: moment('2019-03-01').utc().startOf('day'),
      endDate: moment().utc().endOf('day'),
      picker: {},
      apiParams: {
        // startDate: moment('2019-03-01').utc().startOf('day').format(),
        startDate: moment().subtract(1, 'years').utc().startOf('day').format(),
        endDate: moment().utc().endOf('day').format()
      },
      tableOptions: {
        order: [[4, "desc"]],
        columns: [
          {label: 'Query', name: 'query', style: 'width: 15%'},
          // {label: 'Fields', name: 'fields'},
          // {label: 'Rows', name: 'numRows'},
          // {label: 'Start', name: 'start'},
          {label: 'Num Found', name: 'docsFound', style: 'width: 5%'},
          // {label: 'Type', name: 'responseType', style: 'width: 5%'},
          {label: 'QTime (ms)', name: 'elapsedTime', style: 'width: 5%'},
          // {label: 'Request IP', name: 'requestIp', style: 'width: 5%'},
          {label: 'Errors', name: 'errors', style: 'width: 5%'},
          {label: 'Date', name: 'createdDate', style: 'width: 8%'},
          // {label: 'Actions', data: 'actions', class: 'text-center'},
        ],
        lengthMenu: [
          [10, 25, 50, 100, 500, 1000],
          [10, 25, 50, 100, 500, 1000]
        ],
      },
      search: '',
      searchLogs: null,
      page: 1,
      pageSize: 10,
      recordsTotal: 0,
      recordsFiltered: 0,
      chartSeries: [{name: this.name, data: []}],
      chartOptionsMain: {
        chart: {
          id: 'mainChart',
          type: 'line',
          height: 230,
          toolbar: {
            autoSelected: 'pan',
            show: false
          },
        },
        theme: {
          palette: 'palette1' // upto palette10
        },
        stroke: {
          width: 3
        },
        dataLabels: {
          enabled: false
        },
        fill: {
          opacity: 1,
        },
        markers: {
          size: 0
        },
        xaxis: {
          type: 'datetime',
          min: this.startDate,
          max: this.endDate
        },
        yaxis: {
          forceNiceScale: true
        },
        noData: {
          text: 'Loading...'
        }
      },
      chartOptionsSelection: {
        chart: {
          id: 'selectionChart',
          type: 'area',
          height: 130,
          brush: {
            target: 'mainChart',
            enabled: true,
            autoScaleYaxis: true
          },
          /*selection: {
            enabled: true,
            xaxis: this.rangeSelection
          }*/
        },
        theme: {
          palette: 'palette1' // upto palette10
        },
        fill: {
          type: 'gradient',
          gradient: {
            opacityFrom: 0.91,
            opacityTo: 0.1,
          }
        },
        xaxis: {
          type: 'datetime',
          min: this.startDate,
          max: this.endDate,
          tooltip: {
            enabled: false
          }
        },
        yaxis: {
          tickAmount: 3,
          forceNiceScale: true
        },
        noData: {
          text: 'Loading...'
        }
      },
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

        url = new URL(process.env.VUE_APP_API_BASE_URL + '/api/reports/search/' + this.collection.id, window.location.origin)
        url.search = new URLSearchParams(params)
        console.log("export url", url.toString())
      }
      return url
    },
    rangeSelection() {
      let xaxis = {
        min: moment(this.endDate).subtract(1, 'months').toDate().getTime(),
        max: moment(this.endDate).toDate().getTime()
      }

      let seriesMax = Math.max(...this.chartSeries[0].data.map(o => o[0]))
      let seriesMin = Math.min(...this.chartSeries[0].data.map(o => o[0]))

      xaxis.max = seriesMax
      xaxis.min = seriesMin

      console.log("startDate", moment(seriesMin).toISOString(), "endDate", moment(seriesMax).toISOString(), "seriesMax", seriesMax)
      console.log("diff years", moment(seriesMax).diff(moment(seriesMin), 'years'))
      console.log("diff months", moment(seriesMax).diff(moment(seriesMin), 'months'))
      console.log("diff weeks", moment(seriesMax).diff(moment(seriesMin), 'weeks'))
      console.log("diff days", moment(seriesMax).diff(moment(seriesMin), 'days'))

      if (moment(seriesMax).diff(moment(seriesMin), 'years') > 1) {
        xaxis.min = moment(seriesMax).subtract(1, 'years').toDate().getTime()
        console.log("years", xaxis.min)
      } else if (moment(seriesMax).diff(moment(seriesMin), 'months') > 1) {
        xaxis.min = moment(seriesMax).subtract(3, 'months').toDate().getTime()
        console.log("months", xaxis.min)
      } else if (moment(seriesMax).diff(moment(seriesMin), 'weeks') > 1) {
        xaxis.min = moment(seriesMax).subtract(2, 'weeks').toDate().getTime()
        console.log("weeks", moment.unix(xaxis.min/1000).toISOString())
      } else if (moment(seriesMax).diff(moment(seriesMin), 'days') > 1) {
        xaxis.min = moment(seriesMax).subtract(1, 'days').toDate().getTime()
        console.log("days", xaxis.min)
      }

      //console.log("xaxis", xaxis)
      return xaxis
    }
  },
  async mounted() {
    this.loading = true
    await this.getCollection()
    await this.getSearchLogs()
    await this.getSearchLogChartData()
    this.loading = false
  },
  watch: {
    apiParams: {
      handler: async function () {
        this.loading = true
        await this.getSearchLogs()
        await this.getSearchLogChartData()
        this.loading = false
      }
    },
    search: {
      handler: async function () {
        this.page = 1
        this.loading = true
        await this.getSearchLogs()
        await this.getSearchLogChartData()
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
        // console.log(">>> order", this.tableOptions.order)
        this.loading = true
        await this.getSearchLogs()
        this.loading = false
      }
    },
    chartSeries: {
      handler: async function () {
        let chartOptionsSelection = _.cloneDeep(this.chartOptionsSelection)
        chartOptionsSelection.chart.selection = {
          enabled: true,
          xaxis: this.rangeSelection
        }
        this.chartOptionsSelection = chartOptionsSelection

        let duration = moment.duration(this.endDate.diff(this.startDate))
        let msg = []
        if (duration.years()) {
          msg.push(duration.years() + " years")
        }
        if (duration.months()) {
          msg.push(duration.months() + " months")
        }
        if (duration.days()) {
          msg.push(duration.days() + " days")
        }

        // console.log("msg >>>>>>>>>>>>>>>>>>>", msg.join(","))

        let chartOptionsMain = _.cloneDeep(this.chartOptionsMain)
        chartOptionsMain.title = {
          text: 'Daily Search Counts',
          align: 'center'
        }
        chartOptionsMain.subtitle = {
          text: this.startDate.toDate().toLocaleDateString() +' - '+ this.endDate.toDate().toLocaleDateString() + ' (~'+msg.join(", ")+')',
          align: 'center'
        }
        this.chartOptionsMain = chartOptionsMain
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

      if (chartSeries && chartSeries.length) {
        this.chartSeries = chartSeries
      } else {
        this.chartSeries = [{name: this.name, data: []}]

        let nodata = {
          text: 'No Data'
        }

        let chartOptionsSelection = _.cloneDeep(this.chartOptionsSelection)
        chartOptionsSelection.noData = nodata
        this.chartOptionsSelection = chartOptionsSelection

        let chartOptionsMain = _.cloneDeep(this.chartOptionsMain)
        chartOptionsMain.noData = nodata
        this.chartOptionsMain = chartOptionsMain
      }
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
      // console.log('>>>Ordering on column ', order)
      this.order = order
    },
    getMoment(date) {
      return moment(date)
    },
    getParams() {
      let params = {
        collectionId: this.collection.id,
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
    chartEvent(chartContext, selection) {
      console.log(">>>>>>> EVENT", chartContext, selection.xaxis, selection.yaxis)
    },
  }
}
</script>

<style scoped lang="scss">

</style>
