<template>
  <div v-if="loading" class="d-flex justify-content-center">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  </div>

  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <template v-if="!loading">
    <div class="btn-toolbar justify-content-between mb-3 mt-3" role="toolbar" aria-label="Toolbar with button groups">
        <div class="btn-toolbar float-end" role="toolbar" aria-label="Toolbar with button groups">
  <!--        <button type="button" class="btn btn-primary me-2" @click="updateCollection()">Update Collection</button>-->
          <button type="button" class="btn btn-danger" data-bs-toggle="modal" data-bs-target="#deleteCollectionModal">Delete Collection</button>
        </div>
      </div>

    <!-- Reindex Modal -->
<!--    <div class="modal fade" id="nutchReindexlModal" tabindex="-1" aria-labelledby="nutchReindexlModal" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Reindex</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <h5>Reindex <b>{{ collection.name }}</b> collection?</h5>
            <p class="text-danger"><b>WARNING: This will delete all solr data for this collection!</b></p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            <button type="button" class="btn btn-primary" data-bs-dismiss="modal" @click="reindex()">Understood</button>
          </div>
        </div>
      </div>
    </div>-->

    <!-- Delete Collection Modal -->
    <Modal id="deleteCollectionModal">
      <template v-slot:header>
        <h5 class="modal-title text-black">Delete Collection</h5>
      </template>
      <template v-slot:body>
        <p>Are you sure you want to delete collection <b>{{ collection.name }}</b>!</p>
        <p class="text-danger"><b>This can not be undone!</b></p>
      </template>
      <template v-slot:button-action>
        <button type="button" class="btn btn-danger" data-bs-dismiss="modal"
                @click.prevent="deleteCollection(collection._links.self.href)">Delete
        </button>
      </template>
    </Modal>

  <!--    <div class="modal fade" id="deleteCollectionModal" tabindex="-1" aria-labelledby="deleteCollectionModal" aria-hidden="true">
        <div class="modal-dialog modal-lg modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Delete Collection</h5>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
              <p>Are you sure you want to delete collection <b>{{ collection.name }}</b>!</p>
              <p class="text-danger"><b>This can not be undone!</b></p>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
              <button type="button" class="btn btn-danger" data-bs-dismiss="modal" @click.prevent="deleteCollection(collection._links.self.href)">Delete</button>
            </div>
          </div>
        </div>
      </div>-->

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Basic Information</b>
        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditBasicInfo = JSON.parse(JSON.stringify(collection)); isEditBasicInfo = !isEditBasicInfo" v-if="!isEditBasicInfo">Edit</button>
          <button v-if="isEditBasicInfo" class="btn btn-sm btn-success me-md-2" type="button" @click="saveBasicInfo()">Save</button>
          <button v-if="isEditBasicInfo" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditBasicInfo; isEditBasicInfo = false">Cancel</button>
        </div>
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
        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditSearchConfig = JSON.parse(JSON.stringify(collection)); isEditSearchConfig = !isEditSearchConfig" v-if="!isEditSearchConfig">Edit</button>
          <button v-if="isEditSearchConfig" class="btn btn-sm btn-success me-md-2" type="button" @click="saveSearchConfig()">Save</button>
          <button v-if="isEditSearchConfig" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditSearchConfig; isEditSearchConfig = false">Cancel</button>
        </div>
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
        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditDynamicNav = JSON.parse(JSON.stringify(collection)); isEditDynamicNav = !isEditDynamicNav" v-if="!isEditDynamicNav">Edit</button>
          <button v-if="isEditDynamicNav" class="btn btn-sm btn-success me-md-2" type="button" @click="saveDynamicNav()">Save</button>
          <button v-if="isEditDynamicNav" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditDynamicNav; isEditDynamicNav = false">Cancel</button>
        </div>
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
        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditKeymatch = JSON.parse(JSON.stringify(collection)); isEditKeymatch = !isEditKeymatch" v-if="!isEditKeymatch">Edit</button>
          <button v-if="isEditKeymatch" class="btn btn-sm btn-success me-md-2" type="button" @click="saveKeymatches()">Save</button>
          <button v-if="isEditKeymatch" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditKeymatch; isEditKeymatch = false">Cancel</button>
        </div>
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
        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditIncludeCollections = JSON.parse(JSON.stringify(collection)); isEditIncludeCollections = !isEditIncludeCollections" v-if="!isEditIncludeCollections">Edit</button>
          <button v-if="isEditIncludeCollections" class="btn btn-sm btn-success me-md-2" type="button" @click="saveIncludeCollections()">Save</button>
          <button v-if="isEditIncludeCollections" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditIncludeCollections; isEditIncludeCollections = false">Cancel</button>
        </div>
      </div>
      <div class="card-body">
        <p class="text-danger"><b>Note: This may require the included collection(s) to be re-indexed to take effect</b></p>
        <IncludeCollectionsTable
            :isEditing="isEditIncludeCollections"
            :collectionId="collection.id"
            v-model:includedCollections="collection.includedCollections"
        />
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Part Of Other Collections</b>
        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditPartOfCollections = JSON.parse(JSON.stringify(collection)); isEditPartOfCollections = !isEditPartOfCollections" v-if="!isEditPartOfCollections">Edit</button>
          <button v-if="isEditPartOfCollections" class="btn btn-sm btn-success me-md-2" type="button" @click="savePartOfCollections()">Save</button>
          <button v-if="isEditPartOfCollections" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditPartOfCollections; isEditPartOfCollections = false">Cancel</button>
        </div>
      </div>
      <div class="card-body">
        <p class="text-danger"><b>Note: This may require this collection to be re-indexed to take effect</b></p>
        <IncludeCollectionsTable
            :isEditing="isEditPartOfCollections"
            :collectionId="collection.id"
            v-model:includedCollections="collection.partOfCollections"
        />
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-spider me-1"></i>
        <b>Crawl Configuration</b>
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
        <CollectionCrawlConfigurationForm v-else
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

    <div class="card mb-4">
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

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-users me-1"></i>
        <b>Managers</b>
        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditManagers = JSON.parse(JSON.stringify(collection)); isEditManagers = !isEditManagers" v-if="!isEditManagers">Edit</button>
          <button v-if="isEditManagers" class="btn btn-sm btn-success me-md-2" type="button" @click="saveManagers()">Save</button>
          <button v-if="isEditManagers" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditManagers; isEditManagers = false">Cancel</button>
        </div>
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
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_collectionShowJson'" v-model="showJson">
        </div>
      </div>
      <div class="card-body" v-if="showJson">
        <pre>{{ print(collection) }}</pre>
      </div>
    </div>-->
  </template>
</template>

<script>
import CollectionBasicInfo from "../../components/collections/CollectionBasicInfo";
import SearchConfigurationForm from "../../components/collections/SearchConfigurationForm";
import DynamicNavigation from "../../components/collections/DynamicNavigation";
import Keymatch from "../../components/collections/Keymatch";
import IncludeCollectionsTable from "../../components/collections/IncludeCollectionsTable";
import CollectionCrawlConfigurationForm from "../../components/collections/CollectionCrawlConfigurationForm"
import CollectionManagers from "../../components/collections/CollectionManagers";
import CollectionCrawlScheduleForm from "../../components/collections/CollectionCrawlScheduleForm";
import CollectionService from "../../services/collection.service";
import Modal from "../../components/Modal";

export default {
  name: "CollectionDetails",
  props: ['name', 'tabName'],
  components: {
    CollectionBasicInfo,
    SearchConfigurationForm,
    DynamicNavigation,
    Keymatch,
    IncludeCollectionsTable,
    CollectionCrawlConfigurationForm,
    CollectionCrawlScheduleForm,
    CollectionManagers,
    Modal,
  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: false,
      collection: null,
      isEditBasicInfo: false,
      isEditSearchConfig: false,
      isEditDynamicNav: false,
      isEditKeymatch: false,
      isEditIncludeCollections: false,
      isEditPartOfCollections: false,
      isEditCrawlConfig: false,
      isSavingCrawlConfig: false,
      isEditCrawlSchedule: false,
      isEditManagers: false,
      // beforeEdit: null
      beforeEditBasicInfo: null,
      beforeEditSearchConfig: null,
      beforeEditDynamicNav: null,
      beforeEditKeymatch: null,
      beforeEditIncludeCollections: null,
      beforeEditPartOfCollections: null,
      beforeEditCrawlConfig: null,
      beforeEditCrawlSchedule: null,
      beforeEditManagers: null,

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
  // mounted() {
  //   console.log("collection name:", this.$route.params.name, "id:", this.collection.id)
  //   this.getCollection()
  // },
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

      await CollectionService.getCollections('/collection/search/getCollectionByName', {name: this.$route.params.name, projection: 'collectionFormData'})
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
    includeSiteUrls(event) {
      // $emit('update:includeSiteUrls', $event)
      console.log("addUrls event", event)
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },

    // async updateCollection(url, body) {
    //   console.log("collectionId", JSON.stringify(this.collection.id))
    //
    //   console.log("[updateCollection] url: " + url + ", body: " + JSON.stringify(body), null, 2)
    //
    //   await CollectionService.updateCollection(url, JSON.stringify(body))
    //       .then(response => {
    //         let data = response.data;
    //         this.collection = data;
    //         console.log("data", data)
    //       })
    //       .catch(errors => {
    //         console.log(errors);
    //         this.error = errors
    //       });
    //
    //   await this.getCollection()
    // },
    async saveBasicInfo() {
      //let url = '/collection/'+this.collection.id
      let body = {
        name: this.collection.name,
        siteUrl: this.collection.siteUrl,
        description: this.collection.description,
        keywords: this.collection.keywords
      }
      await CollectionService.updateCollection('/collection/'+this.collection.id, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      // await this.getCollection()
      await this.$router.push({ name: 'collection', params: { name:this.collection.name, id: this.collection.id }})
      this.isEditBasicInfo = false
    },
    async saveSearchConfig() {
      // let url = '/collection/'+this.collection.id
      let body = {
        responseType: this.collection.responseType,
        resultsPerPage: this.collection.resultsPerPage,
        resultLimit: this.collection.resultLimit,
        includeFields: this.collection.includeFields
      }
      await CollectionService.updateCollection('/collection/'+this.collection.id, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      await this.getCollection()
      this.isEditSearchConfig = false
    },
    async saveDynamicNav() {

      let origDynamicNavigations = []

      await CollectionService.getCollections('/collection/'+this.collection.id+"/dynamicNavigations")
          .then(response => {
            let data = response.data;
            data._embedded.dynamicNavigation.forEach(dynamicNavigation => {
              origDynamicNavigations.push(dynamicNavigation._links.self.href)
            });
            //console.log("origDynamicNavigations", origDynamicNavigations, "currentDynamicNavigations", this.collection.dynamicNavigations)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      let dynamicNavigations = await this.updateDynamicNav()

      //console.log("(2) updated dynamicNavigations", dynamicNavigations)

      let deleteDynamicNavigations = origDynamicNavigations.filter((dynamicNavigation) => !dynamicNavigations.includes(dynamicNavigation))

      //console.log(">>> for delete dynamicNavigations", deleteDynamicNavigations)

      await this.deleteDynamicNav(deleteDynamicNavigations)

      // update useFacet
      //let url = '/collection/'+this.collection.id
      let body = {
        useFacets: this.collection.useFacets
      }
      await CollectionService.updateCollection('/collection/'+this.collection.id, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            this.collection = data;
            //console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      await this.getCollection()
      this.isEditDynamicNav = false
    },
    async updateDynamicNav() {
      let dynamicNavigations = []
      let promises = []

      this.collection.dynamicNavigations.forEach(dynamicNavigation => {
        //console.log("dynamicNavigation:", JSON.stringify(dynamicNavigation))
        //let url = "/dynamic-navigation"
        let body = dynamicNavigation

        if (dynamicNavigation._links && dynamicNavigation.id) {
          //url = dynamicNavigation._links.self.href
          promises.push(CollectionService.updateCollection(dynamicNavigation._links.self.href, JSON.stringify(body)))
        } else {
          body.collection = this.collection._links.self.href
          promises.push(CollectionService.addCollection('/dynamic-navigation', JSON.stringify(body)))
        }
      })

      await Promise.all(promises)
          .then(results => {
            results.forEach(response => {
              let data = response.data;
              let dynamicNavigation_link = data._links.self.href
              //console.log("dynamicNavigation_link", dynamicNavigation_link)
              dynamicNavigations.push(dynamicNavigation_link)
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
      //console.log("(1) return dynamicNavigations", dynamicNavigations)
      return dynamicNavigations;
    },
    async deleteDynamicNav(deleteDynamicNavigations) {
      let promises = []

      deleteDynamicNavigations.forEach(dynamicNavigation => {
        //console.log(">>> DELETE dynamicNavigation:", JSON.stringify(dynamicNavigation))
        promises.push(CollectionService.deleteCollection(dynamicNavigation))
      })

      await Promise.all(promises)
          .then(results => {
            results.forEach(response => {
              let data = response.data;
              console.log("delete resp", data)
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },

    async saveKeymatches() {

      let origKeymatches = []

      await CollectionService.getCollections('/collection/'+this.collection.id+"/keymatches")
          .then(response => {
            let data = response.data;
            data._embedded.keymatch.forEach(keymatch => {
              origKeymatches.push(keymatch._links.self.href)
            });
            //console.log("origKeymatches", origKeymatches, "currentKeymatches", this.collection.keymatches)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      let keymatches = await this.updateKeymatches()

      //console.log("(2) updated keymatches", keymatches)

      let deleteKeymatches = origKeymatches.filter((keymatch) => !keymatches.includes(keymatch))

      //console.log(">>> for delete keymatches", deleteKeymatches)

      await this.deleteKeymatches(deleteKeymatches)

      await this.getCollection()
      this.isEditKeymatch = false
    },
    async updateKeymatches() {
      let keymatches = []
      let promises = []

      this.collection.keymatches.forEach(keymatch => {
        //console.log("keymatch:", JSON.stringify(keymatch))
        //let url = "/keymatch"
        let body = keymatch

        if (keymatch._links && keymatch.id) {
          // url = keymatch._links.self.href
          promises.push(CollectionService.updateCollection(keymatch._links.self.href, JSON.stringify(body)))
        } else {
          body.collection = this.collection._links.self.href
          promises.push(CollectionService.addCollection('/keymatch', JSON.stringify(body)))
        }
      })

      await Promise.all(promises)
          .then(results => {
            results.forEach(response => {
              let data = response.data;
              let keymatch_link = data._links.self.href
              //console.log("keymatch_link", keymatch_link)
              keymatches.push(keymatch_link)
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
      //console.log("(1) return keymatches", keymatches)
      return keymatches;
    },
    async deleteKeymatches(deleteKeymatches) {
      let promises = []

      deleteKeymatches.forEach(keymatch => {
        //console.log(">>> DELETE keymatch:", JSON.stringify(keymatch))
        promises.push(CollectionService.deleteCollection(keymatch))
      })

      await Promise.all(promises)
          .then(results => {
            results.forEach(response => {
              let data = response.data;
              console.log("delete resp", data)
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },


    async saveIncludeCollections() {
      // let url = '/collection/'+this.collection.id+'/includedCollections'
      let body = []

      this.collection.includedCollections.forEach(collection => {
        body.push(collection._links.self.href.replace('{?projection}',''))
      })

      await CollectionService.addIncludedCollections('/collection/'+this.collection.id+'/includedCollections', body.join("\n"))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      await this.getCollection()
      this.isEditIncludeCollections = false
    },

    async savePartOfCollections() {
      // let url = '/collection/'+this.collection.id+'/partOfCollections'
      let body = []

      this.collection.partOfCollections.forEach(collection => {
        body.push(collection._links.self.href.replace('{?projection}',''))
      })

      await CollectionService.addPartOfCollections('/collection/'+this.collection.id+'/partOfCollections', body.join("\n"))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      await this.getCollection()
      this.isEditIncludeCollections = false
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
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      await this.getCollection()
      this.isSavingCrawlConfig = false
      this.isEditCrawlConfig = false
    },


    // async saveUrlExclusionPatterns() {
    //
    //   let origUrlExclusionPatterns = []
    //
    //   await CollectionService.getCollections(this.collection.crawlConfig._links.urlExclusionPatterns.href)
    //       .then(response => {
    //         let data = response.data;
    //         data._embedded.urlExclusionPattern.forEach(urlExclusionPattern => {
    //           origUrlExclusionPatterns.push(urlExclusionPattern._links.self.href)
    //         });
    //         //console.log("origUrlExclusionPatterns", origUrlExclusionPatterns, "currentUrlExclusionPatterns", this.collection.crawlConfig.urlExclusionPatterns)
    //       })
    //       .catch(errors => {
    //         //console.log(errors);
    //         this.error = errors
    //       });
    //
    //   let urlExclusionPatterns = await this.updateUrlExclusionPatterns()
    //
    //   //console.log("(2) updated urlExclusionPatterns", urlExclusionPatterns)
    //
    //   let deleteUrlExclusionPatterns = origUrlExclusionPatterns.filter((urlExclusionPattern) => !urlExclusionPatterns.includes(urlExclusionPattern))
    //
    //   //console.log(">>> for delete urlExclusionPatterns", deleteUrlExclusionPatterns)
    //
    //   await this.deleteUrlExclusionPatterns(deleteUrlExclusionPatterns)
    // },
    // async updateUrlExclusionPatterns() {
    //   let urlExclusionPatterns = []
    //   let promises = []
    //
    //   //console.log(">>>>>>>>>>>>>>>currentUrlExclusionPatterns\n", this.collection.crawlConfig.urlExclusionPatterns)
    //
    //   this.collection.crawlConfig.urlExclusionPatterns.forEach(urlExclusionPattern => {
    //     //console.log("urlExclusionPattern:", JSON.stringify(urlExclusionPattern))
    //     let url = "/url-exclusion-pattern"
    //     let body = urlExclusionPattern
    //
    //     if (urlExclusionPattern._links && urlExclusionPattern.id) {
    //       url = urlExclusionPattern._links.self.href
    //       promises.push(CollectionService.updateCollection(url, JSON.stringify(body)))
    //     } else {
    //       body.crawlConfig = this.collection.crawlConfig._links.self.href
    //       promises.push(CollectionService.addCollection(url, JSON.stringify(body)))
    //     }
    //   })
    //
    //   await Promise.all(promises)
    //       .then(results => {
    //         results.forEach(response => {
    //           let data = response.data;
    //           let urlExclusionPattern_link = data._links.self.href
    //           //console.log("urlExclusionPattern_link", urlExclusionPattern_link)
    //           urlExclusionPatterns.push(urlExclusionPattern_link)
    //         })
    //       })
    //       .catch(errors => {
    //         //console.log(errors);
    //         this.error = errors
    //       })
    //   //console.log("(1) return urlExclusionPatterns", urlExclusionPatterns)
    //   return urlExclusionPatterns;
    // },
    // async deleteUrlExclusionPatterns(deleteUrlExclusionPatterns) {
    //   let promises = []
    //
    //   deleteUrlExclusionPatterns.forEach(urlExclusionPattern => {
    //     //console.log(">>> DELETE urlExclusionPattern:", JSON.stringify(urlExclusionPattern))
    //     promises.push(CollectionService.deleteCollection(urlExclusionPattern))
    //   })
    //
    //   await Promise.all(promises)
    //       .then(results => {
    //         results.forEach(response => {
    //           let data = response.data;
    //           console.log("delete resp", data)
    //         })
    //       })
    //       .catch(errors => {
    //         //console.log(errors);
    //         this.error = errors
    //       })
    // },


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
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      this.isEditCrawlSchedule = false
    },


    async saveManagers() {
      // let url = '/collection/'+this.collection.id+'/users'

      let body = []

      this.collection.users.forEach(user => {
        body.push(user._links.self.href.replace('{?projection}',''))
      })

      // console.log("saveManagers body:", body);
      await CollectionService.updateManagers('/collection/'+this.collection.id+'/users', body.join("\n"))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("saveManagers data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      /*let url = '/collection/'+this.collection.id
      let body = {
        users: this.collection.users
      }
      await this.updateCollection(url, body)*/
      this.isEditManagers = false
    },

    deleteCollection(url) {
      CollectionService.deleteCollection(url)
          .then(response => {
            console.log("deleteCollection response:", response.data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      this.$router.push("/collections")
    },
  },
}
</script>

<style scoped>

</style>
