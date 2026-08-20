<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div v-if="loading" class="d-flex justify-content-center">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  </div>

  <div v-if="!loading" class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-clock me-1"></i>
      <b>Crawl Schedule</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditCrawlSchedule = JSON.parse(JSON.stringify(collection)); isEditCrawlSchedule = !isEditCrawlSchedule" v-if="!isEditCrawlSchedule">Edit</button>
        <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-success me-md-2" type="button" @click="saveCrawlSchedule()">Save</button>
        <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditCrawlSchedule; isEditCrawlSchedule = false">Cancel</button>
      </div>
    </div>
    <div class="card-body">
      <CollectionCrawlScheduleForm
          :isEditing="isEditCrawlSchedule"
          :collectionName="collection.name"
          v-model:crawlScheduleCron="collection.crawlConfig.crawlCronSchedule"
          :activeTab="getActiveTab()"
          :editorData="getEditorData()"
          @updateCronEditorData="updateCronEditorData"
      />
    </div>
  </div>
</template>

<script>
import CollectionService from "../../../../services/collection.service";
import CollectionCrawlScheduleForm from "../../../../components/collections/CollectionCrawlScheduleForm";
import EventBus from "../../../../common/EventBus";

export default {
  name: "CollectionCrawlSchedule",
  props: ['name', 'tabName'],
  components: {
    CollectionCrawlScheduleForm
  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: false,
      collection: null,
      isSavingCrawlConfig: false,
      isEditCrawlSchedule: false,
      beforeEditCrawlSchedule: null,
    }
  },
  created () {
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
  methods: {
    async fetchData() {
      this.loading = true
      if (Object.keys(this.$route.params).length !== 0) {
        //console.log(">>>> params", this.$route.params, "query", this.$route.query)
        this.error = this.collection = null
        await this.getCollection()
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

    getActiveTab() {
      let cronEditorData = JSON.parse(this.collection.crawlConfig.cronEditorData)
      //console.log('getActiveTab', cronEditorData.name)
      return cronEditorData.name
    },
    getEditorData() {
      let cronEditorData = JSON.parse(this.collection.crawlConfig.cronEditorData)
      //console.log('getEditorData', cronEditorData.editorData)
      return cronEditorData.editorData
    },
    updateCronEditorData(event) {
      //console.log('updateCronEditorData', event)
      this.collection.crawlConfig.cronEditorData = event;
    },
    async saveCrawlSchedule() {
      // let url = this.collection.crawlConfig._links.self.href
      let body = {
        crawlCronSchedule: this.collection.crawlConfig.crawlCronSchedule,
        cronEditorData: this.collection.crawlConfig.cronEditorData
      }
      await CollectionService.updateCollection(this.collection.crawlConfig._links.self.href, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'Crawl Schedule Updated!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      this.isEditCrawlSchedule = false
    },
  }
}
</script>

<style scoped>

</style>