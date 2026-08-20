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

    <div class="d-grid gap-2 d-md-flex justify-content-md-end mb-3">
      <button type="button" class="btn btn-primary" data-bs-dismiss="modal" @click="createCollection()">Create</button>
      <router-link class="btn btn-danger" to="/collections" replace>Cancel</router-link>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Basic Information</b>
      </div>
      <div class="card-body">
        <div class="row g-3 mb-3">
          <div class="col-md-6">
            <div class="form-floating">
              <input v-model="collection.name" class="form-control" :class="collection.name.length > 0 ? (isValidName ? 'is-valid' : 'is-invalid') : ''" id="collectionName" required placeholder="Collection Name">
              <label for="collectionName">Collection Name <b class="text-danger fst-italic">*</b></label>
              <div class="invalid-feedback" v-html="invalidNameMsg"/>
            </div>
          </div>
          <!--    <div class="col-md-6 mb-3">
                <div class="form-floating">
                  <input :value="siteUrl" @input="$emit('update:siteUrl', $event.target.value)" type="text" class="form-control" id="siteUrl"
                         required
                         placeholder="Site Url">
                  <label for="siteUrl" class="form-label">Base Site URL</label>
                  <div class="form-text">URL used for initial crawling start point (crawl seed url)</div>
                </div>
              </div>-->
        </div>
        <div class="row g-3">
          <div class="col-md-12">
            <div class="form-floating">
              <textarea v-model="collection.description" type="text" class="form-control"
                        style="height: 100px"
                        id="description" required
                        placeholder="Description"></textarea>
              <label for="description" class="form-label">Description</label>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading" class="card mt-4 mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Response Configuration</b>
      </div>
      <div class="card-body">
        <SearchConfigurationForm
            :isEditing="true"
            v-model:responseType="collection.responseType"
            v-model:resultsPerPage="collection.resultsPerPage"
            v-model:resultLimit="collection.resultLimit"
            v-model:includeFields="collection.includeFields"
        />
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-spider me-1"></i>
        <b>Seed URL's</b> <b class="text-danger fst-italic">(* required)</b>
      </div>
      <div class="card-body">
        <div v-if="isSaving" class="d-flex justify-content-center">
          <div class="spinner-border text-primary" role="status">
            <span class="sr-only">Saving...</span>
          </div>
        </div>

        <template v-else>
          <div class="row g-3 mb-3">
            <div class="col-md-12">
              The seed URL list should include a list of websites, one-per-line, which the crawler will start crawling from. The seed URL's must link to all pages you wish to crawl, directly or indirectly. The crawler will go through only the domains of these urls without leaving. Normally only the base domain url is needed (ex. http://mysite.com). If you wish to have other sites (ex. http://my-second-site.com) be part of this collection you have two choices add the site to the seed URL's list or create a separate collection for the site and include the site in the search results by configuring overlapping searches (see <a href="/FAQ#collapseQuestion_5">FAQ > How can collections “share” documents</a>)
            </div>
          </div>
          <div class="row g-3">
            <div class="col-md-12">
              <ImportAddEditCheckTable
                  v-model:tableOptions="tableOptions.seedUrls"
                  :tableData="collection.crawlConfig.seedUrls"
                  @updateTableData="collection.crawlConfig.seedUrls = $event"
                  :selected="selected"
                  @selected="updateSelected"
                  :isEditing="false"
              />
            </div>
          </div>
        </template>
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-spider me-1"></i>
        <b>Sitemap URL's</b>
      </div>
      <div class="card-body">
        <div v-if="isSaving" class="d-flex justify-content-center">
          <div class="spinner-border text-primary" role="status">
            <span class="sr-only">Saving...</span>
          </div>
        </div>

        <template v-else>
          <div class="row g-3 mb-3">
            <div class="col-md-6">
              <div class="form-check">
                <input class="form-check-input" type="checkbox" v-model="collection.crawlConfig.useSitemap" :checked="collection.crawlConfig.useSitemap"
                       id="useSitemap">
                <label class="form-check-label" for="useSitemap">
                  Use Sitemaps (NOTE: http://{host}/sitemaps.xml will be used by default)
                </label>
              </div>
            </div>
          </div>
          <div class="row g-3">
            <div class="col-md-12">
              <ImportAddEditCheckTable
                  v-model:tableOptions="tableOptions.sitemapUrls"
                  :tableData="collection.crawlConfig.sitemapUrls"
                  @updateTableData="collection.crawlConfig.sitemapUrls = $event"
                  :selected="selected"
                  @selected="updateSelected"
                  :isEditing="false"
              />
            </div>
          </div>
        </template>
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-clock me-1"></i>
        <b>Crawl Schedule</b>
      </div>
      <div class="card-body">
        <CollectionCrawlScheduleForm
            :isEditing="true"
            :collectionName="collection.name"
            v-model:crawlScheduleCron="collection.crawlConfig.crawlCronSchedule"
            :activeTab="getActiveTab()"
            :editorData="getEditorData()"
            @updateCronEditorData="updateCronEditorData"
        />
      </div>
    </div>

    <div class="d-grid gap-2 d-md-flex justify-content-md-end mb-3">
      <button type="button" class="btn btn-primary" data-bs-dismiss="modal" @click="createCollection()">Create</button>
      <router-link class="btn btn-danger" to="/collections" replace>Cancel</router-link>
    </div>
  </div>

  <!-- Validation Modal -->
  <div class="modal fade" :class="{ show: validationModelShow, 'd-block': validationModelShow }" id="createValidationModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1" aria-labelledby="createScheduleModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="createScheduleModalLabel">Required Fields Error</h5>
          <button type="button" class="btn-close" aria-label="Close" @click="validationModelShow = !validationModelShow"></button>
        </div>
        <div class="modal-body" :class="{'modal-open': validationModelShow}" >
          <strong class="text-danger">{{ validationMessage }}</strong>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="validationModelShow = !validationModelShow;">OK</button>
        </div>
      </div>
    </div>
  </div>

  <!-- Modal -->
  <div class="modal fade" :class="{ show: toggleModal, 'd-block': toggleModal }" id="createScheduleModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1" aria-labelledby="createScheduleModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="createScheduleModalLabel">Creating Collection...</h5>
<!--          <button type="button" class="btn-close" aria-label="Close" @click="toggleModal = !toggleModal"></button>-->
        </div>
        <div class="modal-body" :class="{'modal-open': toggleModal}" >
          <ul class="list-group">
            <li class="list-group-item d-flex justify-content-between align-items-start align-items-center">
              <div class="ms-2 me-auto">
                <div class="fw-bold">Creating Collection</div>
                <small v-if="modalCreatedStatus.collection.created">Collection {{ collection.name }} created with collectionID: {{ collection.id }}</small>
              </div>
              <template v-if="!modalCreatedStatus.collection.error">
                <span v-if="modalCreatedStatus.collection.created"><i class="fas fa-check text-success"></i></span>
                <div v-else class="spinner-border spinner-border-sm text-primary ms-auto" role="status" aria-hidden="true"></div>
              </template>
              <template v-else>
                <span><i class="fas fa-times text-danger"></i></span>
              </template>
            </li>
            <li class="list-group-item d-flex justify-content-between align-items-start align-items-center">
              <div class="ms-2 me-auto">
                <div class="fw-bold">Creating Crawl Schedule</div>
                <small v-if="modalCreatedStatus.crawlSchedule.created">Crawling is scheduled for {{ explain(collection.crawlConfig.crawlCronSchedule) }}</small>
              </div>
              <template v-if="!modalCreatedStatus.collection.error && !modalCreatedStatus.crawlSchedule.error">
                <span v-if="modalCreatedStatus.crawlSchedule.created"><i class="fas fa-check text-success"></i></span>
                <div v-else class="spinner-border spinner-border-sm text-primary ms-auto" role="status" aria-hidden="true"></div>
              </template>
              <template v-else>
                <span><i class="fas fa-times text-danger"></i></span>
              </template>
            </li>
          </ul>
          <div v-if="modalCreatedStatus.collection.error || modalCreatedStatus.crawlSchedule.error" class="mt-3">
            <p><strong v-if="modalCreatedStatus.collection.error" class="text-danger">Problem creating collection: {{ modalCreatedStatus.collection.error }}</strong></p>
            <p><strong v-if="modalCreatedStatus.crawlSchedule.error" class="text-danger">Problem creating crawl schedule: {{ modalCreatedStatus.crawlSchedule.error }}</strong></p>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-primary" :disabled="isSaving" @click="toggleModal = !toggleModal; this.$router.push({name: 'collection', params: { name: collection.name, id: collection.id }})">OK</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import Breadcrumb from "../../components/Breadcrumb";
import CollectionService from "../../services/collection.service";
import TokenService from "../../services/token.service";
import UserService from "../../services/user.service";
import ImportAddEditCheckTable from "../../components/forms/ImportAddEditCheckTable";
import SearchConfigurationForm from "../../components/collections/SearchConfigurationForm";
import CollectionCrawlScheduleForm from "../../components/collections/CollectionCrawlScheduleForm";
import SchedulerService from "../../services/scheduler.service";
import cronstrue from "cronstrue";
import cronValidator from "cron-expression-validator";

export default {
  name: "CollectionCreate",
  components: {
    Breadcrumb,
    SearchConfigurationForm,
    ImportAddEditCheckTable,
    CollectionCrawlScheduleForm
  },
  data() {
    return {
      loading: false,
      isSaving: false,
      error: null,
      showJson: false,
      collections: null,
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
        responseType: 'xml',
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
      toggleModal: false,
      createdCollectionLink: null,
      selected: [],
      tableOptions: {
        seedUrls: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          columns: [
            {label: 'Seed URL\'s', width: '75%'},
          ]
        },
        sitemapUrls: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          actionDisabledDefaultValues: ['./sitemap.xml (default)'],
          columns: [
            {label: 'Additional Sitemap URL\'s', width: '75%'},
          ]
        },
      },
      isValidName: true,
      invalidNameMsg: '',
      validationMessage: '',
      validationModelShow: false,
      modalCreatedStatus: {
        collection: { created: false, error: null },
        crawlSchedule: { created: false, error: null }
      },
    }
  },
  watch: {
    collection: {
      deep: true,
      handler: function () {
        if (this.collections) {
          if (this.collections.some(collection => collection.name === this.collection.name)) {
            console.log(this.collection.name, "exists already")
            this.invalidNameMsg = "Collection <b>" + this.collection.name + "</b> already exists!"
            this.isValidName = false
          } else {
            this.isValidName = true
            this.invalidNameMsg = ''
          }

          if (this.containsSpecialChars(this.collection.name)) {
            this.invalidNameMsg = "Collection <b>" + this.collection.name + "</b> should not contain spaces or special characters!"
            this.isValidName = false
          }
        }
      }
    }
  },
  computed: {
    isValid() {
      return this.checkIsValid()
    },
  },
  async mounted() {
    this.loading = true
    await this.setOwner();
    await this.getCollections()
  },
  methods: {
    async setOwner() {
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
    async getCollections() {
      await CollectionService.getCollections('/collection', {projection: 'collectionIdNameInfo', size: 1000})
          .then(response => {
            let data = response.data;

            if (this.collectionId) {
              this.collections = data._embedded.collection.filter(collection => collection.id !== this.collection.id );
            } else {
              this.collections = data._embedded.collection;
            }

            this.collections.sort((a, b) => a.name.localeCompare(b.name));
          })
          .catch(errors => {
            console.log(errors);
          });
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    updateSelected(event) {
      this.selected = event
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
    checkIsValid() {

      if (this.collection.name.length === 0 || !this.isValidName) {
        this.validationMessage = 'Must provide a valid collection name!'
        return false
      }

      if (this.collection.crawlConfig.seedUrls.length === 0) {
        this.validationMessage = 'Must provide at least one seed url!'
        return false
      }

      if (!cronValidator.isValidCronExpression(this.collection.crawlConfig.crawlCronSchedule)) {
        this.validationMessage = 'Invalid Crawl Schedule!'
        return false
      }

      this.validationMessage = ''
      return true
    },
    async createCollection() {
      if (!this.isValid) {
        this.validationModelShow = !this.validationModelShow
      } else {
        this.isSaving = true
        this.toggleModal = !this.toggleModal
        await this.addCollection()
        if (this.modalCreatedStatus.collection.created) {
          await this.createCrawlJobSchedule()
          if (this.modalCreatedStatus.crawlSchedule.created) {
            this.isSaving = false
          }
        }
      }
    },
    async addCollection() {

      console.log("addCollection body:", JSON.stringify(this.collection, null, 2))

      await CollectionService.addCollection("/collection2", JSON.stringify(this.collection))
          .then(response => {
            let data = response.data;
            this.collection = data
            this.createdCollectionLink = response.headers.location
            this.modalCreatedStatus.collection.created = true
            //console.log("addCollection response:",JSON.stringify(data,null,2))
            // this.$router.push({path: '/collections'})
          })
          .catch(errors => {
            console.log(errors);
            let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
            this.modalCreatedStatus.collection.error = content
          });

      console.log("createdCollectionLink", this.createdCollectionLink)
      console.log("collection", this.collection)
    },
    async createCrawlJobSchedule() {

      let jobData = {
        jobName: this.collection.name,
        jobGroup: 'scheduled_crawl',
        collectionId: this.collection.id,
        collectionName: this.collection.name,
        numberOfRounds: 50,
        crawlOptions: {
          index: true,
          sitemaps_from_hostdb: 'once',
          // num_threads: "150"
        },
        nutchStepArgs: {
          index: {
            deleteGone: true
          }
        },
        nutchProperties: {},
        cronExpression: this.collection.crawlConfig.crawlCronSchedule
      }

      //console.log("save saveCrawlJobSchedule", JSON.stringify(this.jobData, null, 2))
      await SchedulerService.createCrawlJob("/scheduler/schedule", JSON.stringify(jobData))
          .then(response => {
            let data = response.data;
            console.log("createCrawlJobSchedule", JSON.stringify(data, null, 2))
            this.modalCreatedStatus.crawlSchedule.created = true
          })
          .catch(errors => {
            console.log(errors);
            let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
            this.modalCreatedStatus.crawlSchedule.error = content
          });
    },
    explain(cronExpression) {
      return cronstrue.toString(cronExpression, {
        verbose: true,
        use24HourTimeFormat: true,
        dayOfWeekStartIndexZero: false
      })
    },
    containsSpecialChars(str) {
      const specialChars = ' `!@#$%^&*()+=[]{};\':"\\|,.<>/?~';

      const result = specialChars.split('').some(specialChar => {
        if (str.includes(specialChar)) {
          return true;
        }
        return false;
      });

      return result;
    }
  }
}
</script>

<style scoped>

</style>
