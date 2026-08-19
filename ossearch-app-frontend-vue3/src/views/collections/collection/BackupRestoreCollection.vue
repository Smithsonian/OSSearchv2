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
      <i class="fas fa-cloud-download-alt me-1"></i>
      <b>Backup</b>
    </div>
    <div class="card-body">
      <div v-if="isBackingup" class="d-flex justify-content-center">
        <div class="spinner-border text-primary" role="status">
          <span class="sr-only">Saving...</span>
        </div>
      </div>

      <template v-else>
        <div class="row g-3 mb-3">
          <div class="col-md">
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                role="switch"
                v-model="backupOptions.crawlSchedule"
                :checked="backupOptions.crawlSchedule"
                id="backupOptionsCrawlScheduleCheck"
              />
              <label
                class="form-check-label"
                for="backupOptionsCrawlScheduleCheck"
                >Include Crawl Schedule</label
              >
            </div>
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                role="switch"
                v-model="backupOptions.includeUsers"
                :checked="backupOptions.includeUsers"
                id="backupOptionsIncludeUsersCheck"
              />
              <label
                class="form-check-label"
                for="backupOptionsIncludeUsersCheck"
                >Include Owner/Users</label
              >
            </div>
          </div>
        </div>

        <button
          class="btn btn-primary me-2"
          type="button"
          @click="backupCollection()"
        >
          Backup
        </button>
      </template>
    </div>
  </div>

  <div class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-cloud-upload-alt me-1"></i>
      <b>Restore System Backups</b>
    </div>
    <div class="card-body">
      <h4>System Backups Available</h4>
      <Datatable
        :loading="loading"
        :tableData="availableBackups"
        :tableOptions="tableOptions"
        :responsive="true"
        id="availableBackupsTable"
      >
        <template v-slot:table-body>
          <tr v-for="(row, i) in availableBackups" :key="i">
            <td>{{ row.date }}</td>
            <td>{{ row.file }}</td>
            <td class="justify-content-evenly text-center">
              <div class="btn-group btn-group-sm align-items-center">
                <a
                  href="#"
                  class="btn link-primary"
                  data-bs-toggle="modal"
                  data-bs-target="#viewBackupModal"
                  @click.prevent="getBackupFile(row.file)"
                  ><i class="fas fa-eye"></i
                ></a>
                <a
                  href="#"
                  class="btn link-primary"
                  data-bs-toggle="modal"
                  data-bs-target="#restoreModal"
                  @click.prevent="selectedFiles = row.file"
                  ><i class="fa fa-upload"></i
                ></a>
                <a
                  href="#"
                  class="btn link-danger"
                  data-bs-toggle="modal"
                  data-bs-target="#deleteSystemBackupModal"
                  @click.prevent="backupFileForView = row"
                  ><i class="fa fa-times-circle"></i
                ></a>
              </div>
            </td>
          </tr>
        </template>
      </Datatable>

      <div
        class="btn-toolbar justify-content-between mb-3 mt-3"
        role="toolbar"
        aria-label="Toolbar with button groups"
      >
        <div
          class="btn-toolbar"
          role="toolbar"
          aria-label="Toolbar with button groups"
        >
          <button
            class="btn btn-primary me-2"
            type="button"
            @click="getAvailableBackups()"
          >
            Refresh
          </button>
        </div>
      </div>
    </div>
  </div>

  <div class="card mb-3">
    <div class="card-header">
      <i class="fas fa-upload me-1"></i>
      <b>Upload Backups to Restore</b>
    </div>
    <div class="card-body">
      <div v-if="loading" class="d-flex justify-content-center">
        <div class="spinner-border text-primary" role="status">
          <span class="sr-only">Loading...</span>
        </div>
      </div>
      <div v-else>

        <h5>Uploaded Backups to restore: {{ uploadedFiles.length }}</h5>

        <div id="uploadBackupDropZone">
          <FileDropZone
            v-model:files="uploadedFiles"
            :dropElementId="'uploadBackupDropZone'"
          >
            <template v-if="uploadedFiles.length" v-slot:content>
              <div class="row" v-for="(file, i) in uploadedFiles" :key="i">
                <div class="col-2">
                  {{ file.lastModifiedDate.toLocaleString() }}
                </div>
                <div class="col-7">{{ file.name }}</div>
                <div class="col-2">
                  <div class="progress position-relative">
                    <div
                      class="progress-bar"
                      role="progressbar"
                      :aria-valuenow="progressInfos[file.name]"
                      aria-valuemin="0"
                      aria-valuemax="100"
                      :style="{ width: progressInfos[file.name] + '%' }"
                    >
                      <!--                              {{ progressInfos[file.file]}}%-->
                      <small
                        class="justify-content-center d-flex position-absolute w-100"
                        :class="
                          progressInfos[file.name] < 50 ? 'text-dark' : ''
                        "
                        >{{ progressInfos[file.name] }}%</small
                      >
                    </div>
                  </div>
                </div>
                <div class="col-1 justify-content-evenly text-center">
                  <div class="btn-group btn-group-sm align-items-center">
                    <a
                      href="#"
                      class="btn link-primary"
                      data-bs-toggle="modal"
                      data-bs-target="#viewBackupModal"
                      @click.prevent="previewFile(file)"
                      ><i class="fas fa-eye"></i
                    ></a>
                    <a
                      href="#"
                      class="btn link-danger"
                      @click.prevent="removeUploadFile(file)"
                      ><i class="fa fa-times-circle"></i
                    ></a>
                  </div>
                </div>
              </div>
            </template>
          </FileDropZone>
        </div>

        <fieldset :disabled="!uploadedFiles.length">
          <div class="row align-items-end mt-3">
            <div class="col">
              <div class="form-check form-switch">
                <input
                  class="form-check-input"
                  type="checkbox"
                  role="switch"
                  v-model="restoreOptions.collection"
                  :checked="restoreOptions.collection"
                  id="restoreCollectionOptionCheck"
                />
                <label
                  class="form-check-label"
                  for="restoreCollectionOptionCheck"
                  >Restore Collection</label
                >
              </div>
              <div class="form-check form-switch">
                <input
                  class="form-check-input"
                  type="checkbox"
                  role="switch"
                  v-model="restoreOptions.crawlSchedule"
                  :checked="restoreOptions.crawlSchedule"
                  id="restoreOptionsCrawlScheduleCheck"
                />
                <label
                  class="form-check-label"
                  for="restoreOptionsCrawlScheduleCheck"
                  >Restore Crawl Schedule
                  <span class="text-danger">(if available)</span></label
                >
              </div>
              <div class="form-check form-switch">
                <input
                  class="form-check-input"
                  type="checkbox"
                  role="switch"
                  v-model="restoreOptions.users"
                  :checked="restoreOptions.users"
                  id="restoreOptionsUsersCheck"
                />
                <label class="form-check-label" for="restoreOptionsUsersCheck"
                  >Restore Owner/Users
                  <span class="text-danger">(if available)</span></label
                >
              </div>
            </div>
            <div class="col">
              <button
                class="btn btn-primary float-end"
                type="button"
                data-bs-toggle="modal"
                data-bs-target="#localBackupRestoreStatusModal"
                @click="uploadFiles()"
              >
                Restore
              </button>
            </div>
          </div>
        </fieldset>
      </div>
    </div>
  </div>

  <!-- View Backup Modal -->
  <Modal
    id="viewBackupModal"
    modalClass="modal-xl modal-dialog-scrollable"
    @cancel="backupFileForView = {}"
    data_bs_backdrop="''"
  >
    <template v-slot:header>
      <h5 class="modal-title text-black">
        Backup Details: {{ backupFileForView.filename }}
      </h5>
    </template>
    <template v-slot:body>
      <h3>Collection Details</h3>
      <pre>{{ print(backupFileForView?.data?.collection) }}</pre>
      <h3>CrawlSchedulerJobInfo Details</h3>
      <pre v-if="normalizeCrawlSchedulerJobInfo(backupFileForView?.data?.crawlSchedulerJobInfo).length">
        <div v-for="job in normalizeCrawlSchedulerJobInfo(backupFileForView?.data?.crawlSchedulerJobInfo)" :key="job.jobName + '_' + job.jobGroup">
          {{print(job)}}
        </div>
      </pre>
      <p v-else>No Crawl Scheduler Job Info Available</p>
    </template>
    <template v-slot:footer>
      <button
        type="button"
        class="btn btn-secondary"
        data-bs-dismiss="modal"
        @click.prevent="backupFileForView = {}"
      >
        Close
      </button>
    </template>
  </Modal>

  <!-- Restore Modal -->
  <Modal id="restoreModal">
    <template v-slot:header>
      <h5 class="modal-title text-black">Restore Options</h5>
    </template>
    <template v-slot:body>
      <div class="form-check">
        <input
          class="form-check-input"
          type="checkbox"
          role="checkbox"
          v-model="restoreOptions.collection"
          :checked="restoreOptions.collection"
          id="restoreCollectionOptionCheck"
        />
        <label class="form-check-label" for="restoreCollectionOptionCheck"
          >Restore Collection</label
        >
      </div>
      <div class="form-check">
        <input
          class="form-check-input"
          type="checkbox"
          role="checkbox"
          v-model="restoreOptions.crawlSchedule"
          :checked="restoreOptions.crawlSchedule"
          id="restoreCrawlScheduleOptionCheck"
        />
        <label class="form-check-label" for="restoreCrawlScheduleOptionCheck"
          >Restore Crawl Schedule
          <span class="text-danger">(if available)</span></label
        >
      </div>
      <div class="form-check">
        <input
          class="form-check-input"
          type="checkbox"
          role="checkbox"
          v-model="restoreOptions.users"
          :checked="restoreOptions.users"
          id="restoreOptionsUsersCheck"
        />
        <label class="form-check-label" for="restoreOptionsUsersCheck"
          >Restore Owner/Users
          <span class="text-danger">(if available)</span></label
        >
      </div>
    </template>
    <template v-slot:button-cancel>
      <button
        type="button"
        class="btn btn-secondary"
        data-bs-dismiss="modal"
        aria-label="Cancel"
        @click="selectedFiles = null"
      >
        Cancel
      </button>
    </template>
    <template v-slot:button-action>
      <button
        class="btn btn-primary float-end"
        type="button"
        data-bs-toggle="modal"
        data-bs-target="#localBackupRestoreStatusModal"
        @click="restoreLocalBackup(selectedFiles)"
      >
        Restore
      </button>
    </template>
  </Modal>

  <!-- Delete System Backup Modal -->
  <Modal
      id="deleteSystemBackupModal"
      @cancel="backupFileForView = {}"
  >
    <template v-slot:header>
      <h5 class="modal-title text-black">
        Delete System Backup File
      </h5>
    </template>
    <template v-slot:body>
      <h5 class="text-danger">Are you sure you want to delete this system backup?</h5>
      {{backupFileForView.file}}
    </template>
    <template v-slot:footer>
      <button
          type="button"
          class="btn btn-danger"
          data-bs-dismiss="modal"
          @click.prevent="deleteBackup(backupFileForView.file)"
      >
        Yes
      </button>
      <button
          type="button"
          class="btn btn-secondary"
          data-bs-dismiss="modal"
          @click.prevent="backupFileForView = {}"
      >
        No
      </button>
    </template>
  </Modal>

  <!-- Local Backup Restore Status Modal -->
  <Modal
    id="localBackupRestoreStatusModal"
    modalClass="modal-xl modal-dialog-scrollable"
    @cancel="
      localRestoreStatus = null;
      uploadedFiles = [];
    "
  >
    <template v-slot:header>
      <h5 class="modal-title text-black">Restore Details:</h5>
    </template>
    <template v-slot:body>
      <div v-if="!localRestoreStatus" class="d-flex justify-content-center">
        <div class="spinner-border text-primary" role="status">
          <span class="sr-only">Loading...</span>
        </div>
      </div>
      <table
        v-else
        id="restoreStatusTable"
        class="table table-sm align-middle table-header-hover table-hover"
      >
        <thead class="table-primary">
          <tr>
            <th>Collection</th>
            <th>Status</th>
            <th>Errors</th>
            <th>Crawl Schedule</th>
            <th>Status</th>
            <th>Errors</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="br in localRestoreStatus" :key="br">
            <td>
              <router-link
                :to="{
                  name: 'collection',
                  params: { name: br.collection.name, id: br.collection.id },
                }"
                target="_blank"
                >{{ br.collection.name }}</router-link
              >
            </td>
            <td>
              <span
                v-if="br.collection.status === 'failed'"
                class="badge rounded-pill bg-danger text-danger bg-opacity-25"
                >{{ br.collection.status }}</span
              >
              <span
                v-else-if="br.collection.status === 'created'"
                class="badge rounded-pill bg-success text-success bg-opacity-25"
                >{{ br.collection.status }}</span
              >
              <span
                v-else
                class="badge rounded-pill bg-warning text-warning bg-opacity-25"
                >{{ br.collection.status }}</span
              >
            </td>
            <td>
              <template v-if="br.collection.error">
                <a
                  class="link-danger"
                  href="#"
                  data-bs-toggle="modal"
                  data-bs-target="#localRestoreStatusErrorModal"
                  @click="localRestoreStatusError = br.collection.error"
                  >details</a
                >
              </template>
              <template v-else> none </template>
            </td>
            <td>
              <template v-if="br.crawlSchedulerJobInfo?.length">
                <div
                  v-for="job in br.crawlSchedulerJobInfo"
                  :key="job.jobName + '_' + job.jobGroup"
                >
                  <router-link
                    :to="{
                      name: 'crawlScheduleDetails',
                      params: {
                        jobName: job.jobName,
                        groupName: job.jobGroup,
                      },
                    }"
                    target="_blank"
                    >{{ job.jobName }}</router-link
                  >
                </div>
              </template>
              <template v-else>none</template>
            </td>
            <td>
              <template v-if="br.crawlSchedulerJobInfo?.length">
                <div
                  v-for="job in br.crawlSchedulerJobInfo"
                  :key="job.jobName + '_' + job.jobGroup"
                >
                  <span
                    v-if="job.status === 'failed'"
                    class="badge rounded-pill bg-danger text-danger bg-opacity-25"
                    >{{ job.status }}</span
                  >
                  <span
                    v-else-if="job.status === 'created'"
                    class="badge rounded-pill bg-success text-success bg-opacity-25"
                    >{{ job.status }}</span
                  >
                  <span
                    v-else
                    class="badge rounded-pill bg-warning text-warning bg-opacity-25"
                    >{{ job.status }}</span
                  >
                </div>
              </template>
              <template v-else>none</template>
            </td>
            <td>
              <template v-if="br.crawlSchedulerJobInfo?.length">
                <div
                  v-for="job in br.crawlSchedulerJobInfo"
                  :key="job.jobName + '_' + job.jobGroup"
                >
                  <template v-if="job.error">
                    <a
                      class="link-danger"
                      href="#"
                      data-bs-toggle="modal"
                      data-bs-target="#localRestoreStatusErrorModal"
                      @click="localRestoreStatusError = job.error"
                      >details</a
                    >
                  </template>
                  <template v-else>none</template>
                </div>
              </template>
              <template v-else>none</template>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
    <template v-slot:footer>
      <button
        type="button"
        class="btn btn-secondary"
        data-bs-dismiss="modal"
        @click.prevent="
          localRestoreStatus = null;
          uploadedFiles = [];
        "
      >
        Close
      </button>
    </template>
  </Modal>

  <!-- Local Backup Restore Status Error Modal -->
  <Modal
    id="localRestoreStatusErrorModal"
    modalClass="modal-xl modal-dialog-scrollable"
  >
    <template v-slot:header>
      <h5 class="modal-title text-black">Error Details:</h5>
    </template>
    <template v-slot:header-button-close>
      <button
        type="button"
        class="btn-close"
        data-bs-dismiss="modal"
        data-bs-target="#localBackupRestoreStatusModal"
        aria-label="Close"
        @click="localRestoreStatusError = null"
      ></button>
    </template>
    <template v-slot:body>
      <ul>
        <li v-for="error in localRestoreStatusError" :key="error">
          {{ error }}
        </li>
      </ul>
    </template>
    <template v-slot:footer>
      <button
        class="btn btn-primary float-end"
        type="button"
        data-bs-toggle="modal"
        data-bs-target="#localBackupRestoreStatusModal"
        @click="localRestoreStatusError = null"
      >
        Return to Restore Status
      </button>
    </template>
  </Modal>
</template>

<script>
import Modal from "../../../components/Modal";
import EventBus from "../../../common/EventBus";
import { normalizeCrawlSchedulerJobInfo } from "../../../common/backupRestoreUtils";
import CollectionService from "../../../services/collection.service";
import api from "@/services/api";
import Datatable from "../../../components/table/Datatable.vue";
import FileDropZone from "../../../components/FileDropZone.vue";

export default {
  name: "BackupRestoreCollection",
  props: ["name", "tabName"],
  components: {
    FileDropZone,
    Datatable,
    Modal,
  },
  data() {
    return {
      loading: false,
      error: null,
      isRestoring: false,
      isBackingup: false,
      collection: null,
      availableBackups: null,
      backupFileForView: {},
      backupOptions: {
        includeUsers: true,
        crawlSchedule: true,
      },
      restoreOptions: {
        collection: true,
        crawlSchedule: true,
        users: true,
      },
      selectedFiles: undefined,
      tableOptions: {
        order: [[1, "desc"]],
        columns: [
          { label: "Date", name: "date", style: "width: 15%" },
          { label: "Backup File Name", name: "file" },
          {
            label: "Actions",
            name: "Actions",
            class: "text-center",
            style: "width: 10%",
          },
        ],
      },
      localRestoreStatus: null,
      localRestoreStatusError: null,
      uploadedFiles: [],
      progressInfos: {},
      message: [],
    };
  },
  created() {
    // watch the params of the route to fetch the data again
    this.$watch(
      () => this.$route.params.name,
      async () => {
        await this.fetchData();
      },
      // fetch the data when the view is created and the data is
      // already being observed
      { immediate: true }
    );
    // return this.v$.$touch;
  },
  watch: {
    error: {
      deep: true,
      handler: function () {
        let content =
          (this.error.response &&
            this.error.response.data &&
            this.error.response.data.message) ||
          this.error.message ||
          this.error.toString();
        if (this.error.response && this.error.response.status === 403) {
          EventBus.dispatch("logout");
        } else {
          alert("ERROR: " + content);
        }
      },
    },
    uploadedFiles: {
      deep: true,
      handler: async function () {
        if (this.uploadedFiles.length > 0) {
          let collectionName = await this.uploadedFiles[0]
            .text()
            .then((value) => JSON.parse(value).collection.name)
            .catch((errors) => {
              // console.log(errors);
              this.error = errors;
            });
          //console.log(collectionName);
          if (collectionName !== this.$route.params.name) {
            this.error = this.uploadedFiles[0].name + ' does not contain backup data for this collection!\nExpected "' + this.$route.params.name + '" got "' + collectionName + '"!';
            this.uploadedFiles = [];
          } else {
            this.uploadedFiles.forEach((file) => (this.progressInfos[file.name] = 0));
          }
        }
      },
    },
  },
  methods: {
    async fetchData() {
      this.loading = true;
      if (Object.keys(this.$route.params).length !== 0) {
        // console.log(">>>> params", this.$route.params, "query", this.$route.query)
        this.error = this.collection = null;
        await this.getCollection();
        await this.getAvailableBackups();
      }
      this.loading = false;
    },
    async getCollection() {
      await CollectionService.getCollections(
        "/collection/search/getCollectionByName",
        {
          name: this.$route.params.name,
          projection: "collectionFormData",
        }
      )
        .then((response) => {
          let data = response.data;
          this.collection = data;
          //console.log("data", data);
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
    },
    async getAvailableBackups() {
      await api
        .get("/utils/backup/collection/" + this.collection.id, {
          params: { listAvailableBackups: "true" },
        })
        .then((response) => {
          //console.log(">>>>> response:", response);
          let data = response.data;
          this.availableBackups = data;
          //console.log("data", data);
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
    },
    async backupCollection() {
      this.loading = true;
      await api
        .get("/utils/backup/collection/" + this.collection.id, {
          params: {
            withCrawlSchedule: this.backupOptions.crawlSchedule,
            includeUsers: this.backupOptions.includeUsers,
            projection: "collectionExport",
          },
          responseType: "blob",
        })
        .then((response) => {
          //console.log(">>>>>>>>>", response);
          let fileName =
            response.headers["content-disposition"].split("filename=")[1];
          let FILE = window.URL.createObjectURL(new Blob([response.data]));
          let docUrl = document.createElement("a");
          docUrl.href = FILE;
          docUrl.setAttribute("download", fileName);
          document.body.appendChild(docUrl);
          docUrl.click();

          EventBus.dispatch("toast", {
            type: "success",
            msg: `Backup created for "${this.collection.name}".`,
          });
        })
        .catch(async (errors) => {
          // console.log(errors);
          this.error = errors;
          const reason = await this.readBackupError(errors);
          EventBus.dispatch("toast", {
            type: "danger",
            msg: `Backup failed for "${this.collection.name}": ${reason}`,
          });
        });
      await this.getAvailableBackups();
      this.loading = false;
    },
    /**
     * Error responses for this endpoint arrive as a Blob because the request uses
     * responseType: "blob", so the JSON body has to be read out before it can be shown.
     */
    async readBackupError(errors) {
      const data = errors.response && errors.response.data;
      if (data instanceof Blob) {
        try {
          const parsed = JSON.parse(await data.text());
          return parsed.error || parsed.message || errors.message;
        } catch (e) {
          return errors.message;
        }
      }
      return (data && (data.error || data.message)) || errors.message;
    },
    async getBackupFile(file) {
      this.loading = true;
      let name = this.collection.name + "_" + this.collection.id;
      this.backupFileForView.filename = file;
      await api
        .get("/utils/backup/collection/" + name + "/" + file)
        .then((response) => {
          //console.log(">>>>> response:", response);
          let data = response.data;
          this.backupFileForView.data = data;
          //console.log("data", data);
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
      this.loading = false;
    },
    async deleteBackup(file) {
      this.loading = true;
      await api
        .delete("/utils/backup/collection/" + this.collection.id + "/" + file)
        .then((response) => {
          //console.log(">>>>> response:", response);
          let data = response.data;
          this.availableBackups = data;
          //console.log("data", data);
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
      this.loading = false;
    },
    print(value) {
      return JSON.stringify(value, null, 2);
    },
    normalizeCrawlSchedulerJobInfo,
    upload(file, onUploadProgress) {
      let formData = new FormData();

      formData.append("files", file);

      return api.post("/utils/restore/collection/bulk/upload", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
        onUploadProgress,
      });
    },
    async restoreLocalBackup(file) {
      let data = {
        files: [file],
        restoreCollection: this.restoreOptions.collection,
        restoreCrawlSchedule: this.restoreOptions.crawlSchedule,
        restoreUsers: this.restoreOptions.users,
      };

      await api
        .post("/utils/restore/collection/bulk/local", data)
        .then((response) => {
          //console.log(">>>>>>>>>", response);
          this.localRestoreStatus = response.data;
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
    },
    async previewFile(file) {
      await file
        .text()
        .then(
          (value) =>
            (this.backupFileForView = {
              name: file.name,
              date: file.lastModifiedDate.toLocaleString(),
              data: JSON.parse(value),
            })
        )
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
    },
    removeUploadFile(file) {
      let filteredArray = this.uploadedFiles.filter(
        (f) => f.name !== file.name
      );
      this.uploadedFiles = filteredArray;
    },
    async uploadFiles() {
      for (const file of this.uploadedFiles) {
        this.progressInfos[file.name] = 0;
        let onUploadProgress = (event) => {
          this.progressInfos[file.name] = Math.round(
            (100 * event.loaded) / event.total
          );
        };

        let formData = new FormData();
        formData.append("restoreCollection", this.restoreOptions.collection);
        formData.append(
          "restoreCrawlSchedule",
          this.restoreOptions.crawlSchedule
        );
        formData.append("restoreUsers", this.restoreOptions.users);

        formData.append("files", file);

        await api
          .post("/utils/restore/collection/bulk/upload", formData, {
            headers: {
              "Content-Type": "multipart/form-data",
            },
            onUploadProgress,
          })
          .then((response) => {
            this.message.push(response.data[0]);
            this.localRestoreStatus = this.message;
          })
          // .then((files) => {
          //   //this.fileInfos = files.data;
          // })
          .catch(() => {
            this.progressInfos[file.name] = 0;
            this.message = "Could not upload the file:" + file.name;
          });
      }
    },
  },
};
</script>

<style lang="scss" scoped>
/*.card{
  height: 100%;
}*/
</style>