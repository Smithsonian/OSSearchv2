<template>
<!--  <div v-if="loading" class="container-fluid loading">
    Loading...
    <h2>Name: {{ $route.params.name }}</h2>
    <h2>Id: {{ $route.params.id }}</h2>
  </div>-->

  <div v-if="error" class="container-fluid error">
    {{ error }}
  </div>

  <div class="container-fluid px-4">
    <h1 class="mt-4">Collection: {{ name }} ({{ collection?.id }})</h1>
    <Breadcrumb/>

    <NavTabs :tabs="tabs" :activeTab="activeTab" @activeTab="setActiveTab"/>

    <router-view></router-view>

  </div>
</template>

<script>
import CollectionService from "../../services/collection.service";
import Breadcrumb from "../../components/Breadcrumb";
import NavTabs from "../../components/navtab/NavTabs";

export default {
  name: "Collection",
  props: ['name', 'tabName'],
  components: {
    Breadcrumb,
    NavTabs,
  },
  data() {
    return {
      loading: false,
      error: null,
      collection: null,
      tabs: [
        // {name: 'configuration', label: 'Configuration', icon: 'fas fa-tools'},
        {name: 'collectionOverview', label: 'Overview'},
        {name: 'searchTab', label: 'Search', icon: 'fas fa-search', type: 'dropdown',
          subTabs: [
            {name: 'collectionSearch', label: 'Response Config'},
            {name: 'collectionSearchEdanFieldMappingConfig', label: 'EDAN Field Mapping'},
            {name: 'collectionSearchFaceting', label: 'OSS Faceting'},
            {name: 'collectionSearchKeymatch', label: 'Keymatch\'s'},
            {name: 'collectionOverlappingSearches', label: 'Overlapping Search\'s'},
            {name: 'collectionSearchPageResults', label: 'Page Result\'s'},
          ]
        },
        {name: 'crawlingTab', label: 'Crawling', icon: 'fas fa-spider', type: 'dropdown',
          subTabs: [
            {name: 'collectionCrawlingConfig', label: 'Seed URL\'s'},
            {name: 'collectionCrawlingIncludeExcludeSiteUrls', label: 'Include/Exclude Site Urls'},
            {name: 'collectionCrawlingUrlExclusionPatterns', label: 'URL Exclusion Patterns'},
            {name: 'collectionCrawlingUrlNormalizerPatterns', label: 'URL Normalizer Patterns'},
            {name: 'collectionCrawlingSitemaps', label: 'Sitemaps'},
            {name: 'collectionCrawlSchedule', label: 'Schedule'},
          ]
        },
        {name: 'addRemoveUrls', label: 'Add/Remove URLs', icon: 'fas fa-filter'},
        {name: 'parseChecker', label: 'Parse Checker', icon: 'fas fa-tools'},
        {name: 'reportsTab', label: 'Reports', icon: 'fas fa-chart-area', type: 'dropdown',
          subTabs: [
            {name: 'searchReport', label: 'Search Report'},
            {name: 'keywordReport', label: 'Keyword Report'},
            {name: 'keywordCountReport', label: 'Keyword Count Report'},
            {name: 'indexedUrlsReport', label: 'Indexed URLs Report'},
            {name: 'urlReport', label: 'URL Report'},
            {name: 'crawldbReport', label: 'Crawldb Report'},
          ]
        },
        {name: 'collectionUsers', label: 'Users', icon: 'fas fa-users'},
        // {name: 'keywordReport', label: 'Keyword Report'},
        // {name: 'keywordCountReport', label: 'Keyword Count Report'},
        // {name: 'indexedUrlsReport', label: 'Indexed Urls Report'},
        // {name: 'urlReport', label: 'Url Report'},
        // {name: 'crawldbReport', label: 'Crawldb Report'},
        // {name: 'changeHistory', label: 'Change History', icon: 'fas fa-history'},
        // {name: 'backupRestore', label: 'Backup/Restore', icon: 'fas fa-database'}
      ],
      activeTab: 'configuration',
    }
  },
  created() {
    if (this.isAdmin) {
      this.tabs.push({name: 'changeHistory', label: 'Change History', icon: 'fas fa-history'})
      this.tabs.push({name: 'backupRestoreCollection', label: 'Backup/Restore', icon: 'fas fa-database'})
    }
    // watch the params of the route to fetch the data again
    this.$watch(
        () => this.$route.name,
        async () => {
          this.activeTab = this.$route.name
        },
        // fetch the data when the view is created and the data is
        // already being observed
        {immediate: true}
    )
  },
  computed: {
    currentUser() {
      return this.$store.state.auth.user;
    },
    isAdmin() {
      if (this.currentUser && this.currentUser['roles']) {
        return this.currentUser['roles'].includes('ROLE_ADMIN');
      }
      return false;
    }
  },
  async mounted() {
    await this.fetchData()
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
      await CollectionService.getCollections('/collection/search/getCollectionByName', {name: this.$route.params.name, projection: 'collectionIdNameInfo'})
          .then(response => {
            this.collection = response.data;
            // console.log("collection", this.collection)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    isActiveTab(tabName) {
      return this.activeTab === tabName
    },
    setActiveTab(tabName) {
      this.activeTab = tabName
      console.log("name", this.activeTab, "tabName", this.activeTab)
      this.$router.push({name: this.activeTab, params: {tabName: this.activeTab}})
    }
  }
}
</script>

<style scoped>

</style>
