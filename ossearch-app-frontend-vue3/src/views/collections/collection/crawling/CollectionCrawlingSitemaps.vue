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
      <i class="fas fa-spider me-1"></i>
      <b>Sitemap URL's</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditCrawlConfig = JSON.parse(JSON.stringify(collection)); isEditCrawlConfig = !isEditCrawlConfig" v-if="!isEditCrawlConfig">Edit</button>
        <button v-if="isEditCrawlConfig" class="btn btn-sm btn-success me-md-2" type="button" @click="saveCrawlConfig()">Save</button>
        <button v-if="isEditCrawlConfig" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditCrawlConfig; isEditCrawlConfig = false">Cancel</button>
      </div>
    </div>
    <div class="card-body">
      <div v-if="isSavingCrawlConfig" class="d-flex justify-content-center">
        <div class="spinner-border text-primary" role="status">
          <span class="sr-only">Saving...</span>
        </div>
      </div>

      <template v-else>
        <fieldset :disabled="!isEditCrawlConfig">

          <div class="row g-3 mb-3">
            <div class="col-md-6">
              <div class="form-check">
                <input class="form-check-input" type="checkbox" v-model="collection.crawlConfig.useSitemap" :checked="collection.crawlConfig.useSitemap"
                       id="useSitemap">
                <label class="form-check-label" for="useSitemap">
                  Use Sitemaps (NOTE: http://{host}/sitemaps.xml will be used by default)
                </label>
              </div>
              <!--        <div class="form-floating mb-3">
                        <input type="text" class="form-control" id="sitemapUrl"
                               :disabled="!useSitemap" placeholder="Sitemap URL" :value="sitemapUrl"
                               @input="$emit('update:sitemapUrl', $event.target.value)">
                        <label for="sitemapUrl" class="form-label">Sitemap URL (default <b>{{ sitemapUrlComp }}</b>)</label>
                      </div>-->
            </div>
          </div>
          <div v-if="collection.crawlConfig.useSitemap" class="row g-3">
            <div class="col-md-12">
              <ImportAddEditCheckTable v-if="collection.crawlConfig.useSitemap"
                                       v-model:tableOptions="tableOptions.sitemapUrls"
                                       :tableData="collection.crawlConfig.sitemapUrls"
                                       @updateTableData="collection.crawlConfig.sitemapUrls = $event"
                                       :selected="selected"
                                       @selected="updateSelected"
                                       :isEditing="!isEditCrawlConfig"
              />
            </div>
          </div>
<!--          <div v-if="collection.crawlConfig.useSitemap" class="row g-3 mb-3">
            <div class="col-md-12">
              <ImportAddEditCheckTable
                  v-model:tableOptions="tableOptions.excludeSitemapUrls"
                  :tableData="collection.crawlConfig.excludeSitemapUrls"
                  @updateTableData="collection.crawlConfig.excludeSitemapUrls = $event"
                  :isEditing="isEditCrawlConfig"
              />
            </div>
          </div>-->

        </fieldset>
      </template>
    </div>
  </div>
</template>

<script>
import CollectionService from "../../../../services/collection.service";
import ImportAddEditCheckTable from "../../../../components/forms/ImportAddEditCheckTable";
import EventBus from "../../../../common/EventBus";

export default {
  name: "CollectionCrawlingSitemaps",
  props: ['name', 'tabName'],
  components: {
    ImportAddEditCheckTable
  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: false,
      collection: null,
      isEditCrawlConfig: false,
      isSavingCrawlConfig: false,
      beforeEditCrawlConfig: null,
      selected: [],
      tableOptions: {
        sitemapUrls: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          actionDisabledDefaultValues: ['./sitemap.xml (default)'],
          columns: [
            {label: 'Additional Sitemap URL\'s', width: '75%'},
          ]
        },
        excludeSitemapUrls: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          columns: [
            {label: 'Exclude Sitemap URL\'s', name: 'sitemapUrl', width: '75%'}
          ]
        },
      }
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
    updateSelected(event) {
      this.selected = event
    },
    async saveCrawlConfig() {
      this.isSavingCrawlConfig = true
      // await this.saveUrlExclusionPatterns()

      // console.log("crawlConfig", JSON.stringify(crawlConfig,null,2), "\n>>>>urlExclusionPatterns", JSON.stringify(urlExclusionPatterns, null, 2))

      // let url = this.collection.crawlConfig._links.self.href
      let body = this.collection.crawlConfig
      // delete body.urlExclusionPatterns

      await CollectionService.updateCollection(this.collection.crawlConfig._links.self.href, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'Sitemaps Updated!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      await this.getCollection()
      this.isSavingCrawlConfig = false
      this.isEditCrawlConfig = false
    },
  }
}
</script>

<style scoped>

</style>
