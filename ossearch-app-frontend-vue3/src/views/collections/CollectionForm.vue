<template>

<!--  <div v-if="loading" class="container-fluid px-4 loading">
    Loading...
&lt;!&ndash;    <h2>Name: {{ $route.params.name }}</h2>
    <h2>Id: {{ $route.params.id }}</h2>&ndash;&gt;
  </div>-->

  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div v-if="!loading" class="container-fluid px-4">
    <h1 class="mt-4">Create New Collection</h1>
    <Breadcrumb :path="$route.fullPath"/>

    <div class="btn-toolbar justify-content-between mb-3" role="toolbar" aria-label="Toolbar with button groups">
      <div class="btn-toolbar float-end" role="toolbar" aria-label="Toolbar with button groups">
        <button type="button" class="btn btn-danger" data-bs-dismiss="modal" @click="addCollection()">Create</button>
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Basic Information</b>
      </div>
      <div class="card-body">
        <CollectionBasicInfo
            :isEditing="isEditBasicInfo"
            v-model:name="collection.name"
            v-model:siteUrl="collection.siteUrl"
            v-model:description="collection.description"
            v-model:keywords="collection.keywords"
        />
      </div>
    </div>
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Configuration</b>
      </div>
      <div class="card-body">
        <SearchConfigurationForm
            :isEditing="isEditSearchConfig"
            v-model:responseType="collection.responseType"
            v-model:resultsPerPage="collection.resultsPerPage"
            v-model:resultLimit="collection.resultLimit"
            v-model:includeFields="collection.includeFields"
        />
      </div>
    </div>
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Dynamic Navigation Configuration</b>
      </div>
      <div class="card-body">
        <DynamicNavigation
            :isEditing="isEditDynamicNav"
            :collectionId="collection.id"
            v-model:useFacets="collection.useFacets"
            v-model:dynamicNavigations="collection.dynamicNavigations"
        />
      </div>
    </div>
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Keymatch Configuration</b>
      </div>
      <div class="card-body">
        <Keymatch
            :isEditing="isEditKeymatch"
            :collectionId="collection.id"
            v-model:keymatches="collection.keymatches"
        />
      </div>
    </div>
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Include Other Collections</b>
      </div>
      <div class="card-body">
        <p class="text-danger"><b>Note: This may require the included collection(s) to be re-indexed to take effect</b></p>
        <IncludeCollectionsTable
            :isEditing="isEditIncludeCollections"
            v-model:includedCollections="collection.includedCollections"
        />
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Part Of Other Collections</b>
      </div>
      <div class="card-body">
        <p class="text-danger"><b>Note: This may require this collection to be re-indexed to take effect</b></p>
        <IncludeCollectionsTable
            :isEditing="isEditPartOfCollections"
            v-model:partOfCollections="collection.partOfCollections"
        />
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-spider me-1"></i>
        <b>Crawl Configuration</b>
      </div>
      <div class="card-body">
        <CollectionCrawlConfigurationForm
            :isEditing="isEditCrawlConfig"
            :collectionName="collection.name"
            :siteUrl="collection.siteUrl"
            v-model:seedUrls="collection.crawlConfig.seedUrls"
            v-model:crawlDbPath="collection.crawlConfig.crawlDbPath"
            v-model:crawlSeedPath="collection.crawlConfig.crawlSeedPath"
            v-model:includeSiteUrls="collection.crawlConfig.includeSiteUrls"
            v-model:excludeSiteUrls="collection.crawlConfig.excludeSiteUrls"
            v-model:useSitemap="collection.crawlConfig.useSitemap"
            v-model:sitemapUrls="collection.crawlConfig.sitemapUrls"
            v-model:excludeSitemapUrls="collection.crawlConfig.excludeSitemapUrls"
            v-model:urlExclusionPatterns="collection.crawlConfig.urlExclusionPatterns"
            v-model:regexUrlFilters="collection.crawlConfig.regexUrlFilters"
        />
      </div>
    </div>

<!--    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-clock me-1"></i>
        <b>Crawl Schedule</b>
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
    </div>-->

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-users me-1"></i>
        <b>Managers</b>
      </div>
      <div class="card-body">
        <CollectionManagers
            :isEditing="isEditManagers"
            :collectionId="collection.id"
            v-model:users="collection.users"
            v-model:owner="collection.owner"
        />
      </div>
    </div>
<!--    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>JSON Review</b>
        <div class="form-check form-switch float-end">
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_collectionFormShowJson'" v-model="showJson">
        </div>
      </div>
      <div class="card-body" v-if="showJson">
        <pre>{{ print(collection) }}</pre>
      </div>
    </div>-->
  </div>

  <!-- Modal -->
  <div class="modal fade" :class="{ show: toggleModal, 'd-block': toggleModal }" id="createScheduleModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1" aria-labelledby="createScheduleModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="createScheduleModalLabel">Create Crawl Schedule</h5>
          <button type="button" class="btn-close" aria-label="Close" @click="toggleModal = !toggleModal"></button>
        </div>
        <div class="modal-body" :class="{'modal-open': toggleModal}" >
          Create crawl schedule and start crawling?
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-primary" @click="$router.push({name: 'crawlScheduleForm', query: { collectionId: collection.id, collectionName: collection.name }})">Yes</button>
          <button type="button" class="btn btn-secondary" @click="toggleModal = !toggleModal; this.$router.push({path: '/collections'})">Not Now</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import CollectionBasicInfo from "../../components/collections/CollectionBasicInfo";
import SearchConfigurationForm from "../../components/collections/SearchConfigurationForm";
import DynamicNavigation from "../../components/collections/DynamicNavigation";
import Keymatch from "../../components/collections/Keymatch";
import IncludeCollectionsTable from "../../components/collections/IncludeCollectionsTable";
import CollectionCrawlConfigurationForm from "../../components/collections/CollectionCrawlConfigurationForm"
import CollectionManagers from "../../components/collections/CollectionManagers";
// import CollectionCrawlScheduleForm from "../components/forms/CollectionCrawlScheduleForm"
import Breadcrumb from "../../components/Breadcrumb";
import CollectionService from "../../services/collection.service";
import TokenService from "../../services/token.service";
import UserService from "../../services/user.service";

export default {
  name: "CollectionForm",
  components: {
    Breadcrumb,
    CollectionBasicInfo,
    SearchConfigurationForm,
    DynamicNavigation,
    Keymatch,
    IncludeCollectionsTable,
    CollectionCrawlConfigurationForm,
    // CollectionCrawlScheduleForm,
    CollectionManagers,
  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: false,
      collection: {
        id: null,
        name: '',
        description: '',
        siteUrl: '',
        keywords: [],
        crawlConfig: {
          crawlDbPath: '',
          crawlSeedPath: '',
          seedUrls: [],
          includeSiteUrls: [],
          excludeSiteUrls: [],
          urlExclusionPatterns: [],
          regexUrlFilters: [],
          // sitemapUrls: ['./sitemap.xml (default)'],
          sitemapUrls: [],
          useSitemap: true,
          excludeSitemapUrls: [],
          crawlCronSchedule: '0 0 0 ? * FRI *',
          cronEditorData: "{\"name\":\"Weekly\",\"editorData\":{\"dateTime\":\"00:00\",\"daysOfWeek\":[6]}}"
        },
        responseType: 'html',
        resultsPerPage: 10,
        resultLimit: 0,
        includeFields: ['title', 'content', 'url'],
        useFacets: false,
        dynamicNavigations: [],
        keymatches: [],
        includedCollections: [],
        partOfCollections: [],
        users: [],
        owner: {},
      },
      isEditBasicInfo: true,
      isEditSearchConfig: true,
      isEditDynamicNav: true,
      isEditKeymatch: true,
      isEditIncludeCollections: true,
      isEditPartOfCollections: true,
      isEditCrawlConfig: true,
      isEditCrawlSchedule: true,
      isEditManagers: true,
      toggleModal: false,
      createdCollectionLink: null
    }
  },
  // watch: {
  //   collection: {
  //     deep: true,
  //     handler: function () {
  //       console.log(">>>>>>>>>>>collection: " + JSON.stringify(this.collection, null, 2))
  //       this.$emit('update:collectionData', this.collection)
  //       console.log(">>>>>>>>>>>collectionData: " + JSON.stringify(this.collectionData, null, 2))
  //     }
  //   },
  //   selectedCollection: {
  //     deep: true,
  //     handler: function () {
  //       this.collection = JSON.parse(JSON.stringify(this.selectedCollection))
  //       console.log(">>>>>>>>>>>selectedCollection(1): " + JSON.stringify(this.selectedCollection, null, 2))
  //       // this.$emit('update:collectionData', this.collection)
  //     }
  //   },
  // },
  async mounted() {
    this.loading = true
    let loggedInUser = TokenService.getUser()
    await UserService.getUserByUserName(loggedInUser.username)
        .then(response => {
          let data = response.data;
          //console.log("loggedInUser", loggedInUser, "owner", data)
          this.collection.owner = data
          delete this.collection.owner._links
          //console.log("collection owner", this.collection.owner)
          this.loading = false
        })
        .catch(errors => {
          //console.log(errors);
          this.error = errors
        });

  },
  methods: {
    async addCollection() {

      this.collection.users.forEach(user => {
        delete user._links
      })

      console.log("addCollection body:",JSON.stringify(this.collection,null,2))

      await CollectionService.addCollection("/collection2", JSON.stringify(this.collection))
          .then(response => {
            let data = response.data;
            this.collection = data
            this.createdCollectionLink = response.headers.location
            //console.log("addCollection response:",JSON.stringify(data,null,2))
            // this.$router.push({path: '/collections'})
          })
          .catch(errors => {
            console.log(errors);
            this.error = errors
          });

      console.log("createdCollectionLink", this.createdCollectionLink)
      console.log("collection", this.collection)

      this.toggleModal = !this.toggleModal
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    /*getActiveTab() {
      let cronEditorData = JSON.parse(this.collection.crawlConfig.cronEditorData)
      console.log('getActiveTab', cronEditorData.name)
      return cronEditorData.name
    },
    getEditorData() {
      let cronEditorData = JSON.parse(this.collection.crawlConfig.cronEditorData)
      console.log('getEditorData', cronEditorData.editorData)
      return cronEditorData.editorData
    },
    updateCronEditorData(event) {
      console.log('updateCronEditorData', event)
      this.collection.crawlConfig.cronEditorData = event;
    },*/
  }
}
</script>

<style scoped>

</style>