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
      <b>URL Exclusion Patterns</b>
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
              <h3>URL Exclusion Patterns</h3>
              <p>In some cases website owners may want pages to be crawled, but may not want those pages included in the search index. OSS provides the means for website owners to enter &quot;exclusions&quot;, either specific URLs or URL patterns, that OSS will ignore when crawling or indexing documents to the search collection.</p>

              <p><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>The best place to create your exclusions, when possible, is the <b>robots.txt</b> file; exclusions created here or in <router-link :to="{name: 'collectionCrawlingIncludeExcludeSiteUrls', params: {tabName: 'collectionCrawlingIncludeExcludeSiteUrls'}}">Include/Exclude Site URL's</router-link> affect only search results provided by OSWSS. Search engines like Google, Bing, Yahoo, Baidu, etc. will not apply exclusions added here or in <router-link :to="{name: 'collectionCrawlingIncludeExcludeSiteUrls', params: {tabName: 'collectionCrawlingIncludeExcludeSiteUrls'}}">Include/Exclude Site URL's</router-link></p>

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

              <p><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>For regex URL exclusion patterns the first matching pattern determines whether a URL is included or ignored during indexing.</p>
              <p><b class="text-danger"><i class="fas fa-exclamation-circle"></i> Order matters drag rows to achive desired order</b></p>

            </div>
          </div>
        </div>

        <fieldset :disabled="!isEditCrawlConfig">

<!--          <div class="row g-3 mb-3">
            <div class="col-md-12">
              <ImportAddEditCheckSortableTable
                  v-model:tableOptions="tableOptions.regexUrlFilters"
                  :tableData="collection.crawlConfig.regexUrlFilters"
                  :isEditing="!isEditCrawlConfig"
                  @updateTableData="collection.crawlConfig.regexUrlFilters = $event"
              >
                <template v-slot:caption>
                  <caption>For regex URL exclusion patterns the first matching pattern determines whether a URL is included or ignored during indexing. <b class="text-danger">NOTE: Order matters drag rows to achive desired order</b></caption>
                </template>
              </ImportAddEditCheckSortableTable>
            </div>
          </div>-->

          <div class="row g-3">
            <div class="col-md-12">
              <ImportAddEditCheckSortableTable
                  v-model:tableOptions="tableOptions.urlExclusionPatterns"
                  :tableData="collection.crawlConfig.urlExclusionPatterns"
                  :isEditing="!isEditCrawlConfig"
                  height="750"
                  @updateTableData="collection.crawlConfig.urlExclusionPatterns = $event"
              >
                <!--          <template v-slot:caption>
                            <caption class="caption">For regex URL exclusion patterns the first matching pattern determines whether a URL is included or ignored during indexing. <b class="text-danger">NOTE: Order matters drag rows to achive desired order</b></caption>
                          </template>-->
              </ImportAddEditCheckSortableTable>
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
            <p>OSS implements several exclusion patterns as well as accepting simple URL paths. Below are some of the most commonly used expressions and examples.</p>
            <p>For more information and testing regex expressions see <a href="https://regex101.com/" target="_blank">https://regex101.com/</a> </p>

            <div class="table-responsive-sm" v-for="exclusionType in exclusionTable" :key="exclusionType">
              <table class="table table-sm table-striped table-bordered caption-top">
<!--                <caption>{{ exclusionType.type }}</caption>-->
                <thead class="table-secondary">
                <tr><th class="text-center table-primary" colspan="3" v-html="exclusionType.description"></th></tr>
                <tr>
                  <th v-if="exclusionType.type === 'contains'" class="text-center">&lt;string&gt;</th>
                  <th v-if="exclusionType.type === 'regex'" class="text-center">expression</th>
                  <th>Description</th>
                  <th>Example</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="example in exclusionType.table" :key="example" style="vertical-align: middle;">
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
import ImportAddEditCheckSortableTable from "../../../../components/forms/ImportAddEditCheckSortableTable";
import EventBus from "../../../../common/EventBus";

export default {
  name: "CollectionCrawlingUrlExclusionPatterns",
  props: ['name', 'tabName'],
  components: {
    ImportAddEditCheckSortableTable
  },
  data() {
    let patternTypes = [
      {label: 'contains', value: 'contains'},
      {label: 'regex', value: 'regex'}
    ]
    let regexTypes = [
      {label: 'include', value: 'include'},
      {label: 'exclude', value: 'exclude'}
    ]
    let excludeScope = [
      {label: 'crawling', value: 'crawl'},
      {label: 'indexing', value: 'index'},
      {label: 'crawling & indexing', value: 'all'}
    ]
    return {
      loading: false,
      error: null,
      showQuickReference: false,
      collection: null,
      isEditCrawlConfig: false,
      isSavingCrawlConfig: false,
      beforeEditCrawlConfig: null,
      selected: [],
      tableOptions: {
        urlExclusionPatterns: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          itemKey: 'orderId',
          columns: [
            {label: 'URL Exclusion Patterns', name: 'expression', width: '70%'},
            {label: 'Type', name: 'type', class: 'text-center', type: 'select', options: patternTypes, default: "regex", width: '10%'},
            {label: 'Ignore Case', name: 'ignoreCase', type: 'checkbox', class: 'text-center', width: '9%'},
            {label: 'Exclusion Scope', name: 'scope', class: 'text-center', type: 'select', options: excludeScope, default: "all", width: '15%'}
          ]
        },
        regexUrlFilters: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          itemKey: "orderId",
          columns: [
            {label: 'Type', name: 'type', class: 'text-center', type: 'select', options: regexTypes, default: "exclude", width: '10%'},
            {label: 'Regex URL Exclusion Patterns', name: 'expression'},
          ]
        },
        exclusionTable: {
          columns: [
            {label: 'Type', name: 'type'},
            {label: 'Description', name: 'description'},
          ]
        }
      },
      exclusionTable: [
        {
          type: "contains",
          description: "exclude urls that contains a &lt;string&gt;",
          table: [
            {exp: "/blog/", desc: "exclude all urls that contain the string <b>/blog/</b>", meaning: '<a class="btn-link" href="#" role="button">http://mysite.com/blog/myblog?page=1"</a> would be excluded'},
            {exp: "?page=", desc: "exclude all urls that contain the string <b>?page=</b>", meaning: '<ul><li><a className="btn-link" href="#" role="button">http://mysite.com/blog?page=1"</a> would be excluded</li><li><a className="btn-link" href="#" role="button">http://mysite.com/blog/my-blog?page=2&key1=value1"</a> would be excluded</li></ul>'},
            {exp: "?page=2", desc: "exclude all urls that contain the string <b>?page=2</b>", meaning: '<ul><li><a className="btn-link" href="#" role="button">http://mysite.com/blog?page=1"</a> would <b>not</b> be excluded</li><li><a className="btn-link" href="#" role="button">http://mysite.com/blog/my-blog?page=2&key1=value1"</a> would be excluded</li></ul>'}

          ]
        },
        {
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
      ]
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
              msg: 'URL Exclusions Updated!'
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
