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
      <b>URL Normalizer Regex Patterns</b>
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
        <div class="row g-3">
          <div class="col-md-12">
            <div>
              <h3>Default URL Normalizer Regex Patterns</h3>
              <p>OSS implements several URL normalizer regex patterns globally by default during all crawling steps. Default URL normalizer patters are in the table below.</p>
              <div class="table-responsive-sm">
                <table class="table table-sm" style="width:100%;">
                  <thead class="table-primary">
                    <tr>
                      <th v-for="(header, i) in tableOptions.defaultURLNormalizeRulesTable.columns" :key="i" style="width: 50%">{{ header.label}}</th>
                    </tr>
                  </thead>
                  <tbody>

                  <template v-if="!loading">

                    <tr v-for="rule in defaultURLNormalizerRules['regex-normalize'].regex" :key="rule">
                      <td>{{ rule.pattern }}</td>
                      <td>{{ rule.substitution }}</td>
                    </tr>

                  </template>

                  </tbody>
                </table>

              </div>
            </div>
          </div>
        </div>

        <fieldset :disabled="!isEditCrawlConfig">

          <div class="row mt-3 g-3">
            <div class="col-md-12">
              <h3>Custom Scoped URL Normalizer Regex Patterns</h3>
              <p>Custom scoped URL normalizer regex patterns to apply to urls while crawling. Enter scoped URL normalizer regex patters in the table below.</p>
              <p>For example URL normalizer regex patterns where several site urls have dynamically generated parameters added to the url but all the urls have the exact same page content see <a href="https://regex101.com/r/K9bImQ/1" target="_blank">https://regex101.com/r/K9bImQ/1</a>. Cases like the one in this example can cause infinite crawling. Using the URL normalizer regex pattern and substitution can help prevent this. In this example we would use an empty string for substitution so that any matching pattern will be stripped from the url. We would also apply this URL normalizer rule to the default, crawldb, or outlink scope.</p>

              <ul class="list-unstyled">
                <li><b>Scope Descriptions:</b>
                  <ul>
                    <template v-for="scope in normalizerScopeList" :key="scope">
                      <li><b>{{ scope.label }}:</b> {{ scope.desc }}</li>
                    </template>
                  </ul>
                </li>
              </ul>

              <ul class="list-unstyled">
                <li><b class="text-danger"><i class="fas fa-exclamation-circle"></i> *** Scoped URL Normalizer Patters are <u>NOT</u> enabled by default! *** You may need to enable the normalize flag or disable the nonormalize flag of the crawl step associated with the scope you are using.</b></li>
                <li><b class="text-danger">See <a :href="'/scheduler/scheduled_crawl/'+$route.params.name"><em>Advanced Crawling</em> section in Scheduled Crawl Configuration Details</a> for crawl steps and args.</b></li>
              </ul>

              <ImportAddEditCheckTable
                  v-model:tableOptions="tableOptions.urlNormalizerPatterns"
                  :tableData="collection.crawlConfig.urlNormalizerPatterns"
                  :isEditing="!isEditCrawlConfig"
                  height="750"
                  @updateTableData="collection.crawlConfig.urlNormalizerPatterns = $event"
              >
              </ImportAddEditCheckTable>
            </div>
          </div>

        </fieldset>

      </template>
    </div>
  </div>

  <div class="card mb-4">
    <div class="card-header">
      <i class="bi bi-braces me-1"></i>
      <b>Regex Quick Reference</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_showQuickReference'" v-model="showQuickReference">
      </div>
    </div>
    <div class="card-body" v-if="showQuickReference">
      <div class="row g-3">
        <div class="col-md-12">
          <div>
            <p>Below are some of the most commonly used regex expressions. For more information and testing regex expressions see <a href="https://regex101.com/" target="_blank">https://regex101.com/</a> </p>
            <div class="table-responsive-sm">
              <table class="table table-sm table-bordered accordion" style="width:100%;">
                <thead class="table-primary">
                <tr>
                  <th class="text-center">expression</th>
                  <th>Description</th>
                  <th>Example</th>
                </tr>
                </thead>
                <tbody>

                <tr v-for="example in normalizeTable.table" :key="example" style="vertical-align: middle;">
                  <td class="text-center">{{ example.exp }}</td>
                  <td v-html="example.desc"></td>
                  <td v-html="example.meaning"></td>
                </tr>

                </tbody>
              </table>
            </div>

          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import CollectionService from "../../../../services/collection.service";
import EventBus from "../../../../common/EventBus";
import ImportAddEditCheckTable from "../../../../components/forms/ImportAddEditCheckTable.vue";

export default {
  name: "CollectionCrawlingUrlNormalizerPatterns",
  props: ['name', 'tabName'],
  components: {
    ImportAddEditCheckTable
  },
  data() {
    let normalizerScope = [
      {label: "default", value: "_default", desc: "Scope used by default for all URL normalizers and crawl steps."},
      {label: "partition", value: "partition", desc: "Scope used by Generate step"},
      {label: "generate_host_count", value: "generate_host_count", desc: "Scope used by Generate Step."},
      {label: "fetcher", value: "fetcher", desc: "Scope used by Fetch Step and when processing redirect URLs."},
      {label: "crawldb", value: "crawldb", desc: "Scope used when updating the CrawlDb with new URLs. Used by Updatedb Step"},
      {label: "linkdb", value: "linkdb", desc: "Scope used when updating the LinkDb with new URLs. Used by Invertlinks Step"},
      {label: "inject", value: "inject", desc: "Scope used by Inject Step"},
      {label: "outlink", value: "outlink", desc: "Scope used when constructing new outlink instances. Used by Parse Step"},
      {label: "indexer", value: "indexer", desc: "Scope used when indexing URLs. Used by Indexing Step."},
    ]
    return {
      loading: false,
      error: null,
      showQuickReference: false,
      collection: null,
      defaultURLNormalizerRules: null,
      isEditCrawlConfig: false,
      isSavingCrawlConfig: false,
      beforeEditCrawlConfig: null,
      selected: [],
      normalizerScopeList: normalizerScope,
      tableOptions: {
        urlNormalizerPatterns: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          itemKey: 'orderId',
          columns: [
            {label: 'Pattern', name: 'pattern', width: '40%'},
            {label: 'Substitution', name: 'substitution', width: '35%'},
            {label: 'Scope', name: 'scope', class: 'text-center', type: 'select', options: normalizerScope, default: "_default", width: '15%'}
          ]
        },
        normalizeTable: {
          columns: [
            {label: 'Type', name: 'type'},
            {label: 'Description', name: 'description'},
          ]
        },
        defaultURLNormalizeRulesTable: {
          columns: [
            {label: 'Pattern', name: 'pattern'},
            {label: 'Substitution', name: 'description'},
          ]
        }
      },
      normalizeTable: {
        type: "regex",
        description: "exclude urls using regex pattern matching.",
        table: [
          {exp: "\\", desc: "Backslash", meaning: 'Used to escape a special character<br/>Example:<ul><li> To exclude all urls like <a className="btn-link" href="#" role="button">https://www.aaa.si.edu/uv/?manifest=</a><br/>the regex would be <b>^https://www.aaa.si.edu/uv/\\?manifest=.*$</b><br/>notice the ? needs to be escaped because it is a special character<br/>? becomes \\?</li>'},
          {exp: "^", desc: "Caret", meaning: "Beginning of a string"},
          {exp: "$", desc: "Dollar sign", meaning: "End of a string"},
          {exp: ".", desc: "Period or dot", meaning: "Matches any single character"},
          {exp: "|", desc: "Vertical bar or pipe symbol", meaning: "Matches previous OR next character/group"},
          {exp: "?", desc: "Question mark", meaning: "Match zero or one of the previous"},
          {exp: "*", desc: "Asterisk or star", meaning: "Match zero, one or more of the previous"},
          {exp: "+", desc: "Plus sign", meaning: "Match one or more of the previous"},
          {exp: "( )", desc: "Opening and closing parenthesis", meaning: "Group characters"},
          {exp: "[ ]", desc: "Opening and closing square bracket", meaning: "Matches a range of characters"},
          {exp: "{ }", desc: "Opening and closing curly brace", meaning: "Matches a specified number of occurrences of the previous"}
        ]
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
        await this.getDefaultURLNormalizerRules()
      }
      this.loading = false
    },
    async getCollection() {

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
    async getDefaultURLNormalizerRules() {

      await CollectionService.getCollections('/crawl/utils/urlnormalizer', null)
          .then(response => {
            let data = response.data;
            this.defaultURLNormalizerRules = data;
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async saveCrawlConfig() {
      this.isSavingCrawlConfig = true
      let body = this.collection.crawlConfig

      await CollectionService.updateCollection(this.collection.crawlConfig._links.self.href, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'URL Normalizers Updated!'
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
