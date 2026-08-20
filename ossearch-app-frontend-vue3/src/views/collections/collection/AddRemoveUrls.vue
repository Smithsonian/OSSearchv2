<template>
  <div class="card mt-3">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>Add/Remove Urls</b>
    </div>
    <div class="card-body">
      <div class="row g-3 mb-3">
        <div class="col-md-12">
          <p>If you wish to immediately add or remove specific site urls from the search index.</p>

          <ul class="list-unstyled">
            <li><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>If you are trying to:
              <ul>
                <li><b>add</b> a url that should have been crawled or indexed but was not</li>
                <li><b>remove</b> a url that should not have been crawled or indexed</li>
              </ul>
            </li>
            <li>It's best to figure out why the url was not included or excluded first before adding or removing them here. There
              can be several reasons why a url is not included or excluded.
            </li>
          </ul>
        </div>
      </div>

      <ImportAddEditCheckTable
          v-model:tableOptions="tableOptions.urls"
          :tableData="urls"
          @updateTableData="urls = $event"
          :isEditing="false"
      />
      <div class="btn-group-sm float-end">
        <button type="button" class="btn btn-sm btn-success me-2" data-bs-toggle="modal" data-bs-target="#addUrlsModal">Add Urls</button>
        <button type="button" class="btn btn-sm btn-danger" data-bs-toggle="modal" @click="removeUrls()">Remove Urls</button>
      </div>
    </div>
  </div>

  <!--  <div class="btn-toolbar justify-content-between mb-3 mt-3" role="toolbar" aria-label="Toolbar with button groups">
    <div class="btn-toolbar float-start" role="toolbar" aria-label="Toolbar with button groups">
      <button type="button" class="btn btn-success me-2" data-bs-toggle="modal" data-bs-target="#addUrlsModal">Add Urls</button>
      <button type="button" class="btn btn-danger me-2" data-bs-toggle="modal" data-bs-target="#removeUrlsModal">Remove Urls</button>
    </div>
  </div>-->

  <!-- Add Urls Modal -->
  <Modal id="addUrlsModal">
    <template v-slot:header>
      <h5 class="modal-title text-black">Crawl URL's Immediately?</h5>
    </template>
    <template v-slot:body>
      <p>Run a single crawl round after the URL's are injected into the crawldb. This will also trigger indexing. Otherwise added URL's will be crawled and indexed during the next scheduled crawl.</p>
<!--      <div id="crawl" class="form-check mt-3">
        <input class="form-check-input" type="checkbox" id="crawlChecked" v-model="crawl">
        <label class="form-check-label" for="crawlChecked">
          Crawl Added URL's Immediately?
        </label>
      </div>-->
    </template>
    <template v-slot:footer>
      <button type="button" class="btn btn-primary" data-bs-dismiss="modal"
              @click="addUrls(true)">Yes
      </button>
      <button type="button" class="btn btn-secondary" data-bs-dismiss="modal"
              @click="addUrls(false)">No
      </button>
    </template>
  </Modal>

  <!-- Remove Urls Modal -->
  <!--  <Modal id="removeUrlsModal" modalClass="modal-lg">
      <template v-slot:header>
        <h5 class="modal-title text-black">Remove Urls</h5>
      </template>
      <template v-slot:body>
        &lt;!&ndash;        <p>Are you sure you want to delete collection <b>{{ collection.name }}</b>!</p>
                <p class="text-danger"><b>This can not be undone!</b></p>&ndash;&gt;
        <ImportAddEditCheckTable
            v-model:tableOptions="tableOptions.urls"
            :tableData="urls"
            @updateTableData="urls = $event"
            :isEditing="false"
        />
      </template>
      <template v-slot:button-action>
        <button type="button" class="btn btn-danger" data-bs-dismiss="modal"
                @click="removeUrls()">Remove
        </button>
      </template>
    </Modal>-->

</template>

<script>
import CrawlUtilsService from "../../../services/crawlUtils.service";
import Modal from "../../../components/Modal";
import ImportAddEditCheckTable from "../../../components/forms/ImportAddEditCheckTable";
import EventBus from "../../../common/EventBus";

export default {
  name: "AddRemoveUrls",
  props: ['name', 'tabName'],
  components: {
    Modal,
    ImportAddEditCheckTable,
  },
  data() {
    return {
      error: null,
      urls: [],
      crawl: true,
      tableOptions: {
        urls: {
          enableImport: true,
          enableAddRow: true,
          enableActions: true,
          columns: [
            {label: 'URLS', width: '33%'}
          ]
        }
      }
    }
  },
  methods: {
    async addUrls(crawl) {
      let body = this.urls;
      let params = {
        jobName: this.name,
        crawl: crawl
      }
      await CrawlUtilsService.post('/crawl/utils/urls/add', params, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
            this.urls = []
            this.crawl = false
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'Added URL\'s to Collection!'
            })
          })
          .catch(errors => {
            console.log(errors);
            console.log(errors.message);
            console.log(errors.response);
            console.log(errors.response.data);
            console.log(errors.response.status);
            console.log(errors.response.headers);
            this.error = errors
          });
    },
    async removeUrls() {
      let body = this.urls;
      let params = {
        jobName: this.name
      }
      await CrawlUtilsService.post('/crawl/utils/urls/remove', params, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
            this.urls = []
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'Removed URL\'s from Collection!'
            })
          })
          .catch(errors => {
            console.log(errors.message);
            console.log(errors.response);
            console.log(errors.response.data);
            console.log(errors.response.status);
            console.log(errors.response.headers);
            this.error = errors
          });
    },
  }
}
</script>

<style scoped>

</style>
