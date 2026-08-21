<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-sitemap me-1"></i>
      <b>Search Page Results</b>
      <div class="float-end">
        <button type="button" class="btn btn-sm btn-primary bi-plus-lg float-end"
                @click="beforeEdit = JSON.parse(JSON.stringify(pageResult)); selectPageResult(pageResult, true)">Page
          Result
        </button>
      </div>
    </div>

    <div class="card-body">
      <Datatable :loading="loading"
                 :tableData="collection?.pageResults"
                 :tableOptions="tableOptions"
                 id="collectionSearchPageResultsTable"
      >
        <template v-slot:table-body>
          <tr v-for="(pageResult, i) in collection?.pageResults" :key="i">
            <td>{{ pageResult.name }}</td>
            <td class="text-center">{{ getLocalDateTime(pageResult.dateCreated) }}</td>
            <td class="justify-content-evenly text-center">
              <div class="btn-group btn-group-sm align-items-center">
                <a href="#" class="btn link-primary p-0 m-1"
                   @click="beforeEdit = JSON.parse(JSON.stringify(pageResult)); selectPageResult(pageResult, false)">
                  <i class="fa fa-edit"></i>
                </a>
                <a href="#" class="btn link-danger p-0" data-bs-toggle="modal" data-bs-target="#deletePageResultModal"
                   title="Delete" @click="prepareDelete(pageResult)">
                  <i class="fa fa-times-circle"></i>
                </a>
              </div>
            </td>
          </tr>
        </template>
      </Datatable>
    </div>
  </div>

  <div v-if="showForm" class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>{{ cardTitle }}</b>
      <div class="float-end">
        <button class="btn btn-sm btn-success me-md-2" type="button" @click="save(selectedPageResult)">Save</button>
        <button class="btn btn-sm btn-danger float-end" type="button" @click="selectedPageResult = beforeEdit = null; showForm = false">
          Cancel
        </button>
      </div>
    </div>
    <div class="card-body">
      <div class="row g-3 mb-3">
        <div class="col-md-6">
          <div class="form-floating">
            <input v-model="selectedPageResult.name" class="form-control" id="collectionName"
                   :class="isValid ? 'is-valid' : 'is-invalid'" required placeholder="Collection Name"
                   ref="selectedPageResult">
            <label for="collectionName">Page Result Name</label>
            <div id="validationServer03Feedback" class="invalid-feedback">
              {{ validationMessage }}
            </div>
          </div>
        </div>
      </div>
      <div class="row g-3 mb-3">
        <div class="col-md-6">
          <div class="form-check">
            <input v-model="selectedPageResult.useSearchButton" :checked="selectedPageResult.useSearchButton"
                   type="checkbox" class="form-check-input" id="useSearchButtonCheck">
            <label class="form-check-label" for="useSearchButtonCheck">Use Search Button?</label>
          </div>
        </div>
      </div>
      <div v-if="selectedPageResult.useSearchButton" class="row g-3 mb-3">
        <div class="col-md-6">
          <div class="form-floating">
            <input v-model="selectedPageResult.buttonText" type="text"
                   class="form-control form-control-sm"
                   placeholder="value" aria-label="value" aria-describedby="buttonTextInput">
            <label v-if="selectedPageResult.useSearchButton" class="form-check-label" for="buttonTextInput">Search
              Button Text</label>
          </div>
        </div>
      </div>
      <div class="row g-3">
        <div class="col-md-12">
          <div class="d-flex flex-column">
            <label for="description" class="form-label text-start">Full Page HTML Editor
              <div class="float-end">
                <button class="btn btn-sm btn-primary me-2" data-bs-toggle="collapse" data-bs-target="#collapseTokens"
                        aria-expanded="false" aria-controls="collapseTokens">Help<i class="fas fa-info-circle ms-2"></i>
                </button>
                <button class="btn btn-sm btn-primary me-2" @click.prevent="preview = !preview"><span>HTML</span><i
                    :class="!preview ? 'fas fa-eye' : 'fas fa-eye-slash'"/></button>
                <button class="btn btn-sm btn-primary" @click.prevent="format">Format</button>
              </div>
            </label>
            <strong>Valid HTML: <span
                :class="isValidHTML(selectedPageResult.fullHtml) === true ? 'text-success' : 'text-danger'">{{ isValidHTML(selectedPageResult.fullHtml) }}</span></strong>
          </div>
          <div class="collapse" id="collapseTokens">
            <p>Using this option required minimum html knowledge, all <code>href</code> urls must be encoded (i.e.
              <code>&</code> encoded as <code>&amp;amp;</code> , etc.), all tags must be closed in order to render page
              result properly otherwise it could break the way results are displayed.</p>
            <div class="table-responsive">
              <table class="table table-sm align-middle">
                <thead>
                <tr>
                  <td>Token</td>
                  <td>Description</td>
                  <td>HTML Element Attributes</td>
                </tr>
                </thead>
                <tbody>
                <tr v-for="token in tokens" :key="token.name">
                  <td><b>{{ token.token }}</b></td>
                  <td>{{ token.desc }}</td>
                  <td>
                    <template v-if="token.attrList.ids">id's:
                      <ul>
                        <li v-for="attrb in token.attrList.ids" :key="attrb">{{ attrb }}</li>
                      </ul>
                    </template>
                    <template v-if="token.attrList.classes">classes:
                      <ul>
                        <li v-for="attrb in token.attrList.classes" :key="attrb">{{ attrb }}</li>
                      </ul>
                    </template>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
      <div class="row g-3 mb-3">
        <div class="d-inline-flex">
          <Codemirror ref="cmEditor" class="form-control"
                      v-model="selectedPageResult.fullHtml"
                      :options="cmOption"
                      :extensions="extensions"
                      :style="{width: preview ? '50%' : '100%', height: '75vh'}"
          />
          <iframe v-if="preview" :style="'width: '+ (preview ? '50%' : '0px') + ';height: 75vh;'"
                  :srcdoc="selectedPageResult.fullHtml" title="Search Page Preview" allowfullscreen></iframe>
        </div>
      </div>
    </div>
  </div>

  <div v-if="showForm" class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>Preview Search Page Results</b>
    </div>
    <div class="card-body">
      <!--      <div class="input-group input-group-sm">
              <input type="text" class="form-control" placeholder="Site URL" aria-label="Preview Search" v-model="search">
              <a class="btn btn-primary btn-sm" :href="previewSearch()" target="_blank" role="button">Preview</a>
            </div>
            <div class="col-md-12 mt-3">
              <pre class="shiki" v-html="testpreview"></pre>
            </div>-->

      <strong v-if="!selectedPageResult._links" class="text-danger">To preview save first!</strong>
      <fieldset :disabled="!selectedPageResult._links">
        <div class="input-group input-group-sm">
          <input type="text" class="form-control" placeholder="search string..." aria-label="Preview Search"
                 v-model="search" @keyup.enter="searchPreview = previewSearch()">
          <button class="btn btn-primary btn-sm" type="button" id="button-preview"
                  @click="searchPreview = previewSearch()">Search
          </button>
          <button class="btn btn-secondary btn-sm" type="button" id="button-resetPreview" @click="search = null">Reset
          </button>
        </div>
        <div v-if="searchPreview" class="col-md-12 mt-3"><b>Search URL:</b> <a class="link-primary"
                                                                               :href="previewSearch()" target="_blank"
                                                                               role="button">{{ searchPreview }}</a>
        </div>
        <div v-if="searchPreview" class="col-md-12 mt-3">
          <iframe type="text/plain" style="'width: 100%; height: 100vh;'" :src="searchPreview" target="_parent"
                  title="Search Preview" allowfullscreen></iframe>
        </div>
      </fieldset>
    </div>
  </div>

  <!-- Validation Modal -->
  <div class="modal fade" :class="{ show: validationModelShow, 'd-block': validationModelShow }"
       id="createValidationModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1"
       aria-labelledby="createScheduleModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="createScheduleModalLabel">Validation Error</h5>
          <button type="button" class="btn-close" aria-label="Close"
                  @click="validationModelShow = !validationModelShow"></button>
        </div>
        <div class="modal-body" :class="{'modal-open': validationModelShow}">
          <ul v-if="selectedPageResult">
            <li v-if="!isValid"><strong class="text-danger">Page Result Name is not valid!</strong></li>
            <li v-if="isValidHTML(selectedPageResult?.fullHtml) !== true"><strong class="text-danger">HTML is not valid!
              {{ isValidHTML(selectedPageResult?.fullHtml) }}</strong></li>
          </ul>

        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="validationModelShow = !validationModelShow;">OK
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Delete Page Result Modal -->
  <div class="modal fade" id="deletePageResultModal" tabindex="-1" aria-labelledby="deletePageResultModalLabel"
       aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="deletePageResultModalLabel">Delete Page Result</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          Are you sure you want to delete page result "{{ selectedPageResult?.name }}"?
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="button" class="btn btn-danger" data-bs-dismiss="modal"
                  @click="deletePageResult(selectedPageResult)">Delete
          </button>
        </div>
      </div>
    </div>
  </div>


</template>

<script>
import EventBus from "../../../../common/EventBus";
import CollectionService from "../../../../services/collection.service";
import Datatable from "../../../../components/table/Datatable";

import {Codemirror} from 'vue-codemirror'
import {oneDark} from '@codemirror/theme-one-dark'
import {html} from '@codemirror/lang-html'
import {codeToHtml} from 'shiki'

const beautify = require('js-beautify')

export default {
  name: "CollectionSearchPageResults",
  props: ['name', 'tabName'],
  components: {
    Datatable,
    Codemirror
  },
  data() {
    return {
      loading: false,
      error: null,
      showForm: false,
      showJson: true,
      cardTitle: null,
      tableOptions: {
        order: [[0, "asc"]],
        columns: [
          {label: 'Name', name: 'name'},
          {label: 'Created Date', name: 'dateCreated', class: 'text-center', style: 'width: 15%'},
          {label: 'Actions', name: 'actions', class: 'text-center', sortable: false, style: 'width: 10%'},
        ],
      },
      preview: false,
      extensions: [html(), oneDark],
      cmOption: {
        placeholder: "Code goes here...",
        mode: 'text/html',
        theme: 'oneDark',
        // style: {
        //   width: this.preview ? '50%' : '100%',
        //   height: '200px'
        // },
        tabSize: 4,
        styleActiveLine: true,
        line: true,
        foldGutter: true,
        styleSelectedText: true,
        lineNumbers: true,
        lineWrapping: true,
        matchBrackets: true,
        collapseIdentical: true,
        showCursorWhenSelecting: true,
      },
      collection: null,
      pageResults: [],
      pageResult: {
        logoPath: '',
        name: '',
        nameNoSpace: '',
        stylesheet: '',
        linkColor: '',
        header: '',
        footer: '',
        buttonText: 'Search',
        boxMaxChar: '',
        includeField: [],
        useSearchButton: false,
        head: '',
        fullHtml: beautify.html_beautify('<!DOCTYPEhtml><html><head><title></title></head><body></body></html>')
      },
      selectedPageResult: null,
      itemIndex: null,
      validationModelShow: false,
      validationMessage: null,
      beforeEdit: null,
      search: '',
      searchPreview: null,
      testpreview: null,
      tokens: [
        {
          token: '[[oss-pr-form]]',
          desc: 'This token is representing form location',
          attrList: {ids: ['oss-form', 'oss-form-wrapper']}
        },
        {
          token: '[[oss-pr-pagination]]',
          desc: 'This token is representing pagination location',
          attrList: {classes: ['oss-pagination-link', 'oss-pagination-link-active', 'oss-pagination-next-link', 'oss-pagination-prev-link', 'oss-pagination']}
        },
        {
          token: '[[oss-pr-results]]',
          desc: 'This token is representing results location',
          attrList: {
            ids: ['oss-result-wrapper'],
            classes: ['oss-result-message', 'oss-result-title', 'oss-result-url', 'oss-result-snippet', 'oss-result-set-container']
          }
        }
      ]
    }
  },
  created() {
    // watch the params of the route to fetch the data again
    this.$watch(
        () => this.$route.params.name,
        async () => {
          await this.fetchData()
        },
        // fetch the data when the view is created and the data is
        // already being observed
        {immediate: true}
    )
    // return this.v$.$touch;
  },
  async mounted() {
    this.testpreview = await this.highlighter(beautify.html_beautify('<GSP VER="3.2"><Q>test</Q><PARAM name="q" value="test" original_value="test"/><PARAM name="site" value="aaa_v2" original_value="aaa_v2"/><PARAM name="output" value="xml" original_value="xml" url_escaped_value="xml" js_escaped_value="xml"/><PARAM name="proxyreload" value="0" original_value="0"/><PARAM name="oe" value="UTF-8" original_value="UTF-8" url_escaped_value="UTF-8" js_escaped_value="UTF-8"/><PARAM name="ie" value="UTF-8" original_value="UTF-8" url_escaped_value="UTF-8" js_escaped_value="UTF-8"/><PARAM name="ip" value="160.111.100.68" original_value="160.111.100.68"/></GSP>'))
  },
  computed: {
    isValid() {
      this.setValidationMsg(null);

      // First check if the name is empty
      if (!this.selectedPageResult?.name || this.selectedPageResult.name.length === 0) {
        this.setValidationMsg("Must not be empty! Please provide a valid name!");
        return false;
      }

      // Check for duplicates only if collection and pageResults exist
      if (this.collection && this.collection.pageResults) {
        let nameChanged = this.collection.pageResults.some(pageResult => {
          console.log("checking nameChange", pageResult.name, "=", this.selectedPageResult.name, pageResult.name === this.selectedPageResult.name);
          if (this.beforeEdit?.name === this.selectedPageResult.name) {
            return false;
          } else {
            return pageResult.name === this.selectedPageResult.name;
          }
        });

        console.log("beforeName:", this.beforeEdit?.name, "selectedName:", this.selectedPageResult.name, "nameChanged:", nameChanged);

        if (nameChanged) {
          this.setValidationMsg(this.selectedPageResult.name + " exists already! Please provide a valid name!");
          return false;
        }
      }

      return true;
    },
  },
  methods: {
    async fetchData() {
      this.loading = true
      if (Object.keys(this.$route.params).length !== 0) {
        this.error = this.collection = null
        await this.getCollection()
        // await this.getCollectionPageResults()
      }
      this.loading = false
    },
    async getCollection() {
      await CollectionService.getCollections('/collection/search/getCollectionByName', {
        name: this.$route.params.name,
        projection: 'collectionSearchData',
        // projection: 'collectionFormData'
      })
          .then(response => {
            let data = response.data;
            this.collection = data;
            /*if (Object.keys(this.collection).includes('pageResults')) {
              this.pageResults = this.collection.pageResults
            } else {
              this.pageResults = [{name: "example", useSearchButton: true, buttonText: "Search", fullHtml: this.testpreview, dateCreated: 'dateCreated'}]
            }*/
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async save(pageResult) {
      if (!this.isValid || this.isValidHTML(pageResult.fullHtml) !== true) {
        console.log("Not Saving...", this.isValid, this.isValidHTML(pageResult.fullHtml));
        this.validationModelShow = !this.validationModelShow;
      } else {
        console.log("Starting save operation...");
        console.log("Current pageResult:", pageResult);
        console.log("Has _links?", !!pageResult._links);

        let body = pageResult;

        try {
          let response;
          if (pageResult._links) {  // Remove the check for ID
            console.log("Updating existing page result");
            response = await CollectionService.updateCollection(pageResult._links.self.href, JSON.stringify(body));
          } else {
            console.log("Creating new page result");
            body.collection = this.collection._links.self.href;
            response = await CollectionService.addCollection('/pageResult', JSON.stringify(body));
          }

          let data = response.data;
          console.log("Response data:", data);

          // Make sure we preserve the _links from the response
          this.selectedPageResult = { ...data };
          this.beforeEdit = JSON.parse(JSON.stringify(data));

          EventBus.dispatch('toast', {
            type: 'success',
            msg: 'Page result "' + data.name + '" saved successfully!'
          });

          // Wait for the fetch to complete
          await this.fetchData();
        } catch (error) {
          console.error("Save error:", error);
          let content = (error.response && error.response.data && error.response.data.message) || error.message || error.toString();
          EventBus.dispatch('toast', {
            type: 'danger',
            msg: 'Saving "' + pageResult.name + '" failed! ' + content
          });
        }
      }
    },
    async deletePageResult(pageResult) {
      if (pageResult?._links?.self?.href) {
        await CollectionService.deleteCollection(pageResult._links.self.href)
            .then(() => {
              EventBus.dispatch('toast', {
                type: 'success',
                msg: 'Page result "' + pageResult.name + '" deleted successfully!'
              });
              // Reset selected page result
              this.selectedPageResult = null;
              this.beforeEdit = null;
              // Refresh the data
              this.fetchData();
            })
            .catch(errors => {
              let content = (errors.response && errors.response.data && errors.response.data.message) || errors.message || errors.toString();
              EventBus.dispatch('toast', {
                type: 'danger',
                msg: 'Deleting page result "' + pageResult.name + '" failed! ' + content
              });
            });
      }
    },
    selectPageResult(pageResult, isCreate) {
      this.cardTitle = isCreate ? "Create New Search Page Result" : "Edit Search Page Result"
      // Make sure we preserve the _links when copying
      this.selectedPageResult = isCreate ? { ...this.pageResult } : { ...pageResult };
      this.selectedPageResult.fullHtml = this.decodeHtml(this.selectedPageResult.fullHtml)
      this.showForm = true;
      this.setFocus()
    },
    prepareDelete(pageResult) {
      this.selectedPageResult = pageResult;
      // Clear any existing form display
      this.beforeEdit = null;
      this.showForm = false;
    },
    decodeHtml(html) {
      let txt = document.createElement("textarea");
      txt.innerHTML = html;
      return txt.value;
    },
    isValidHTML(html) {
      const parser = new DOMParser();
      const doc = parser.parseFromString(html, 'text/html');
      if (doc.documentElement.querySelector('parsererror')) {
        return doc.documentElement.querySelector('parsererror').innerText;
      } else {
        return true;
      }
    },
    previewSearch() {
      // relative to the server the admin UI is loaded from; every app
      // server proxies /search to its local backend
      let url = process.env.VUE_APP_API_BASE_URL + '/search'
      let params = {
        q: this.search ? this.search : '*',
        client: this.selectedPageResult?.name,
        site: this.collection?.name,
        proxystylesheet: this.collection?.name,
        proxyreload: 0,
        getfields: '*',
        btnG: 'Search'
      }

      url = new URL(url, window.location.origin)
      url.search = new URLSearchParams(params)
      console.log("previewSearch", url.toString())

      return url.toString()

      /*const headers = { "Accept": "xml" };
      fetch(url.toString(), headers)
          .then(async response => {
            const data = await response.text();

            // check for error response
            if (!response.ok) {
              // get error message from body or default to response statusText
              const error = (data && data.message) || response.statusText;
              return Promise.reject(error);
            }

            this.totalVuePackages = data;
          })
          .catch(error => {
            this.errorMessage = error;
            console.error("There was an error!", error);
          });*/

      /*axios.get(url.toString())
          .then(response => {
            console.log(response.data)
            this.searchPreview = response.data
          })
          .catch(errors => {
            console.log(errors)
            // this.error = errors
          })*/

      /*let xhr = new XMLHttpRequest();
      xhr.onreadystatechange = () => console.log(xhr.responseText)
      xhr.open('GET', url.toString());
      xhr.send();*/

    },
    getLocalDateTime(utc) {
      let u = utc + 'Z'
      return new Date(u).toLocaleString()
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    setValidationMsg(msg) {
      this.validationMessage = msg
    },
    async highlighter(xml) {
      let text = beautify.html_beautify(xml)
      text = codeToHtml(text, {lang: 'xml', theme: 'light-plus'})
      return text
    },
    format() {
      this.selectedPageResult.fullHtml = beautify.html_beautify(this.selectedPageResult.fullHtml)
    },
    setFocus() {
      setTimeout(() => {
        this.$refs.selectedPageResult.focus()
      }, 200)
      // this.$nextTick(() => this.$refs.selectedPageResult.focus())
    }
  }
}
</script>

<style scoped>

</style>