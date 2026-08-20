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
      <b>Include/Exclude Site URL's</b>
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
        <div class="row g-3 mb-3">
          <div class="col-md-12">
            <p>If you wish to include or exclude specific site urls add them below.</p>

            <p><i class="fas fa-exclamation text-danger pe-2"></i><b class="text-danger">NOTE:</b> This is not meant to be used for excluding url patterns i.e to exclude all <a class="btn-link" href="#" role="button">http://mysite.com/nodes</a> urls, instead use the <router-link :to="{name: 'collectionCrawlingUrlExclusionPatterns', params: {tabName: 'collectionCrawlingUrlExclusionPatterns'}}">URL Exclusion Patterns</router-link></p>

            <ul class="list-unstyled">
              <li><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>If you are trying to:
                <ul>
                  <li><b>include</b> a url that should have been crawled or indexed but was not</li>
                  <li><b>exclude</b> a url that should not have been crawled or indexed</li>
                </ul>
              </li>
              <li>It's best to figure out why the url was not included or excluded first before adding them here. There
                can be several reasons why a url is not included or excluded.
              </li>
            </ul>

            <p><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>If you have a lot of urls to exclude, a general url exclusion or want to exclude urls that may be dynamically generated use <router-link :to="{name: 'collectionCrawlingUrlExclusionPatterns', params: {tabName: 'collectionCrawlingUrlExclusionPatterns'}}">URL Exclusion Patterns</router-link> instead.</p>

            <p><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>The best place to create your exclusions, when possible, is the <b>robots.txt</b> file; exclusions created here or in <router-link :to="{name: 'collectionCrawlingUrlExclusionPatterns', params: {tabName: 'collectionCrawlingUrlExclusionPatterns'}}">URL Exclusion Patterns</router-link> affect only search results provided by OSWSS. Search engines like Google, Bing, Yahoo, Baidu, etc. will not apply exclusions added here or in <router-link :to="{name: 'collectionCrawlingUrlExclusionPatterns', params: {tabName: 'collectionCrawlingUrlExclusionPatterns'}}">URL Exclusion Patterns</router-link></p>

            <ul class="list-unstyled">
              <li><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>Aside from <b>robots.txt</b> file; You can also add robots meta tag within the <b>&lt;head&gt;</b> tag of individual pages to prevent them from being crawled or indexed.
                <ul>
                  <li>example: <b>&lt;meta name="robots" content="nofollow, noindex" /&gt;</b></li>
                  <li><b>nofollow</b> can also be used on specific links within a page by using attribute <b>rel="nofollow"</b>.
                    <ul><li>example: <b>&lt;a href="http://mysite.com/some-link" rel="nofollow" /&gt;</b></li></ul>
                  </li>
                </ul>
              </li>
            </ul>
          </div>
        </div>
        <fieldset :disabled="!isEditCrawlConfig">
          <div class="row g-3 mb-3">
            <div class="col-md-12">
              <ImportAddEditCheckTable
                  v-model:tableOptions="tableOptions.includeSiteUrls"
                  :tableData="collection.crawlConfig.includeSiteUrls"
                  @updateTableData="collection.crawlConfig.includeSiteUrls = $event"
                  :isEditing="!isEditCrawlConfig"
              />
            </div>
          </div>

          <div class="row g-3">
            <div class="col-md-12">
              <ImportAddEditCheckTable
                  v-model:tableOptions="tableOptions.excludeSiteUrls"
                  :tableData="collection.crawlConfig.excludeSiteUrls"
                  @updateTableData="collection.crawlConfig.excludeSiteUrls = $event"
                  :isEditing="!isEditCrawlConfig"
              />
            </div>
          </div>


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
  name: "CollectionCrawlingIncludeExcludeSiteUrls",
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
        includeSiteUrls: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          columns: [
            {label: 'Include Site URL\'s', name: 'siteUrl', width: '75%'}
          ]
        },
        excludeSiteUrls: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          columns: [
            {label: 'Exclude Site URL\'s', name: 'siteUrl', width: '75%'}
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
              msg: 'Include/Exclude Site URL\'s Updated!'
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
