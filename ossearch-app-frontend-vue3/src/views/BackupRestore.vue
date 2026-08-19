<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="container-fluid px-4">
    <Breadcrumb />

    <div class="card mb-3">
      <div class="card-header">
        <i class="fas fa-cloud-download-alt me-1"></i>
        <b>Backup Collections</b>
      </div>
      <div class="card-body">
        <DualListBox
          :source="collectionsAvailable"
          :destination="collectionsToBackup"
          label="name"
          @onChangeList="onChangeList"
        >
          <template v-slot:source>
            <h5>Collections Available for Backup</h5>
          </template>
          <template v-slot:destination>
            <h5>Selected For Backup</h5>
          </template>
        </DualListBox>


        <fieldset :disabled="!collectionsToBackup.length">
          <div class="row justify-content-end align-items-end mt-3">
            <div class="col-4 ps-5">
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
            <div class="col-2">
              <button
                class="btn btn-primary float-end"
                type="button"
                @click.prevent="backupCollections()"
              >
                <template v-if="backingup">
                  <span
                    class="spinner-border spinner-border-sm"
                    role="status"
                    aria-hidden="true"
                  ></span>
                  Backing up...
                </template>
                <template v-else> Backup </template>
              </button>
            </div>
          </div>
        </fieldset>
      </div>
    </div>

    <div class="card mb-3">
      <div class="card-header">
        <i class="fas fa-cloud-upload-alt me-1"></i>
        <b>Restore System Backups</b>
      </div>
      <div class="card-body">
        <div v-if="loading" class="d-flex justify-content-center">
          <div class="spinner-border text-primary" role="status">
            <span class="sr-only">Loading...</span>
          </div>
        </div>
        <div v-else-if="availableBackupsError" class="alert alert-danger">
          <i class="fas fa-exclamation-triangle me-1"></i>
          {{ availableBackupsError }}
          <button class="btn btn-sm btn-outline-danger ms-2" @click="getAvailableBackups()">
            Retry
          </button>
        </div>
        <div v-else class="row">
          <div class="col-5">
            <h5>System Backups Available</h5>
            <TreeView
              v-model:tree="treeDisplayData"
              v-model:selected="serverFiles"
            />
          </div>

          <div class="col-7">
            <h5>Selected System Backups to Restore:</h5>
            <div class="tableFixHead">
              <Datatable
                :loading="loading"
                :tableData="serverFiles"
                :tableOptions="tableOptions.localRestore"
                :responsive="true"
                id="availableBackupsTable"
              >
                <template v-slot:table-body>
                  <template v-if="serverFiles.length > 0">
                    <tr v-for="(file, i) in serverFiles" :key="i">
                      <td>{{ file[Object.keys(file)[0]].date }}</td>
                      <td>{{ file[Object.keys(file)[0]].name }}</td>
                      <td class="justify-content-evenly text-center">
                        <div class="btn-group btn-group-sm align-items-center">
                          <a
                            href="#"
                            class="btn link-primary"
                            data-bs-toggle="modal"
                            data-bs-target="#viewUploadBackupModal"
                            @click.prevent="getBackupFile(file)"
                            ><i class="fas fa-eye"></i
                          ></a>
                          <a
                            href="#"
                            class="btn link-danger"
                            @click.prevent="serverFiles.splice(i, 1)"
                            ><i class="fa fa-times-circle"></i
                          ></a>
                        </div>
                      </td>
                    </tr>
                  </template>
                  <template v-else>
                    <tr>
                      <td
                        :colspan="tableOptions.localRestore.columns.length + 2"
                        class="text-center"
                      >
                        Select Some Server Backups Fist
                      </td>
                    </tr>
                  </template>
                </template>
              </Datatable>
            </div>

            <fieldset :disabled="!serverFiles.length">
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
                    <label
                      class="form-check-label"
                      for="restoreOptionsUsersCheck"
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
                    @click="restoreLocalBackup()"
                  >
                    Restore
                  </button>
                </div>
              </div>
            </fieldset>
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

          <Datatable
            :loading="loading"
            :tableData="uploadedFiles"
            :tableOptions="tableOptions.uploadRestore"
            :responsive="true"
            :tableHover="false"
            :tableBorder="true"
            id="uploadBackupsTable"
          >
            <template v-slot:table-body>
              <FileDropZone
                v-model:files="uploadedFiles"
                :dropElementId="'uploadBackupsTable_tbody'"
                :multiple="true"
              >
                <template v-slot:dropzone-active>
                  <tr>
                    <td
                      :colspan="tableOptions.uploadRestore.columns.length + 2"
                      class="text-center"
                    >
                      <div class="drag-drop-zone">
                        <i class="fas fa-cloud-upload-alt icon"></i>
                        <p>Drop to upload</p>
                      </div>
                    </td>
                  </tr>
                </template>
                <template v-if="uploadedFiles.length" v-slot:content>
                  <tr v-for="(file, i) in uploadedFiles" :key="i">
                    <td>{{ file.lastModifiedDate.toLocaleString() }}</td>
                    <td>{{ file.name }}</td>
                    <td>
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
                    </td>
                    <td class="justify-content-evenly text-center">
                      <div class="btn-group btn-group-sm align-items-center">
                        <a
                          href="#"
                          class="btn link-primary"
                          data-bs-toggle="modal"
                          data-bs-target="#viewUploadBackupModal"
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
                    </td>
                  </tr>
                  <tr>
                    <td
                      :colspan="tableOptions.uploadRestore.columns.length + 2"
                      class="text-center"
                    >
                      <div>
                        <i class="fas fa-cloud-upload-alt me-1"></i>
                        <p>Drop more backup files here</p>
                      </div>
                    </td>
                  </tr>
                </template>
                <template v-else v-slot:dropzone>
                  <tr>
                    <td
                      :colspan="tableOptions.uploadRestore.columns.length + 2"
                      class="text-center"
                    >
                      <div class="drag-drop-zone">
                        <i class="fas fa-cloud-upload-alt icon"></i>
                        <p>Click to upload</p>
                        <p class="sub-text">or drag and drop backup files</p>
                      </div>
                    </td>
                  </tr>
                </template>
              </FileDropZone>
            </template>
          </Datatable>

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
  </div>

  <!-- View Backup Modal -->
  <Modal
    id="viewUploadBackupModal"
    modalClass="modal-xl modal-dialog-scrollable"
    @cancel="backupFileForView = null"
  >
    <template v-slot:header>
      <h5 class="modal-title text-black">
        Backup Details: {{ backupFileForView?.name }}
      </h5>
    </template>
    <template v-slot:body>
      <h3>Collection Details</h3>
      <pre>{{ print(backupFileForView?.data?.collection) }}</pre>
      <h3>CrawlSchedulerJobInfo Details</h3>
      <pre v-if="normalizeCrawlSchedulerJobInfo(backupFileForView?.data?.crawlSchedulerJobInfo).length">
        <div v-for="job in normalizeCrawlSchedulerJobInfo(backupFileForView?.data?.crawlSchedulerJobInfo)" :key="job.jobName + '_' + job.jobGroup">
          {{ print(job) }}
        </div>
      </pre>
      <p v-else>No Crawl Scheduler Job Info Available</p>
    </template>
    <template v-slot:footer>
      <button
        type="button"
        class="btn btn-secondary"
        data-bs-dismiss="modal"
        @click.prevent="backupFileForView = null"
      >
        Close
      </button>
    </template>
  </Modal>

  <!-- Local Backup Restore Status Modal -->
  <Modal
    id="localBackupRestoreStatusModal"
    modalClass="modal-xl modal-dialog-scrollable"
    @cancel="localRestoreStatus = null; uploadedFiles = []; serverFiles = []"
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
      <table v-else id="restoreStatusTable" class="table table-sm align-middle table-header-hover table-hover">
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
              <router-link :to="{ name: 'collection', params: { name: br.collection.name, id: br.collection.id }, }" target="_blank">{{ br.collection.name }}</router-link>
            </td>
            <td>
              <span v-if="br.collection.status === 'failed'" class="badge rounded-pill bg-danger text-danger bg-opacity-25">{{ br.collection.status }}</span>
              <span v-else-if="br.collection.status === 'created'" class="badge rounded-pill bg-success text-success bg-opacity-25">{{ br.collection.status }}</span>
              <span v-else class="badge rounded-pill bg-warning text-warning bg-opacity-25">{{ br.collection.status }}</span>
            </td>
            <td>
              <template v-if="br.collection.error">
                <a class="link-danger" href="#" data-bs-toggle="modal" data-bs-target="#localRestoreStatusErrorModal" @click="localRestoreStatusError = br.collection.error">details</a>
              </template>
              <template v-else> none </template>
            </td>
            <td>
              <template v-if="br.crawlSchedulerJobInfo?.length">
                <div v-for="job in br.crawlSchedulerJobInfo" :key="job.jobName + '_' + job.jobGroup">
                  <router-link :to="{ name: 'crawlScheduleDetails', params: { jobName: job.jobName, groupName: job.jobGroup, }, }" target="_blank">{{ job.jobName }}</router-link>
                </div>
              </template>
              <template v-else>none</template>
            </td>
            <td>
              <template v-if="br.crawlSchedulerJobInfo?.length">
                <div v-for="job in br.crawlSchedulerJobInfo" :key="job.jobName + '_' + job.jobGroup">
                  <span v-if="job.status === 'failed'" class="badge rounded-pill bg-danger text-danger bg-opacity-25">{{ job.status }}</span>
                  <span v-else-if="job.status === 'created'" class="badge rounded-pill bg-success text-success bg-opacity-25">{{ job.status }}</span>
                  <span v-else class="badge rounded-pill bg-warning text-warning bg-opacity-25">{{ job.status }}</span>
                </div>
              </template>
              <template v-else>none</template>
            </td>
            <td>
              <template v-if="br.crawlSchedulerJobInfo?.length">
                <div v-for="job in br.crawlSchedulerJobInfo" :key="job.jobName + '_' + job.jobGroup">
                  <template v-if="job.error">
                    <a class="link-danger" href="#" data-bs-toggle="modal" data-bs-target="#localRestoreStatusErrorModal" @click="localRestoreStatusError = job.error">details</a>
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
      <button type="button" class="btn btn-secondary" data-bs-dismiss="modal" @click.prevent="localRestoreStatus = null; uploadedFiles = []; serverFiles = []">Close</button>
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
import Breadcrumb from "../components/Breadcrumb.vue";
import EventBus from "../common/EventBus";
import { normalizeCrawlSchedulerJobInfo } from "../common/backupRestoreUtils";
import CollectionService from "../services/collection.service";
import DualListBox from "../components/DualListBox.vue";
import api from "../services/api";
import TreeView from "../components/TreeView.vue";
import Modal from "../components/Modal.vue";
import Datatable from "../components/table/Datatable.vue";
import FileDropZone from "../components/FileDropZone.vue";

export default {
  name: "BackupRestore",
  components: {
    Datatable,
    Modal,
    TreeView,
    DualListBox,
    Breadcrumb,
    FileDropZone,
  },
  data() {
    return {
      loading: false,
      backingup: false,
      availableBackupsError: null,
      backupErrors: [],
      error: null,
      collections: [],
      collectionsAvailable: [],
      collectionsToBackup: [],
      backupOptions: {
        includeUsers: false,
        crawlSchedule: true,
      },
      restoreOptions: {
        collection: true,
        crawlSchedule: true,
        users: true,
      },
      availableBackups: null,
      treeDisplayData: [
        {
          // id: 0,
          name: "Collections",
          type: "folder",
          // checkable: false,
          children: [],
        },
      ],
      serverFiles: [],
      uploadedFiles: [],
      progressInfos: {},
      message: [],
      backupFileForView: null,
      tableOptions: {
        localRestore: {
          order: [[1, "desc"]],
          columns: [
            { label: "Last Modified Date", name: "date", style: "width: 25%" },
            { label: "Backup File Name", name: "file" },
            {
              label: "Actions",
              name: "Actions",
              class: "text-center",
              style: "width: 10%",
            },
          ],
        },
        uploadRestore: {
          order: [[1, "desc"]],
          columns: [
            { label: "Last Modified Date", name: "date", style: "width: 25%" },
            { label: "Backup File Name", name: "file" },
            { label: "Progress", name: "progress" },
            {
              label: "Actions",
              name: "Actions",
              class: "text-center",
              style: "width: 10%",
            },
          ],
        },
      },
      localRestoreStatus: null,
      localRestoreStatusError: null,
    };
  },
  computed: {
    currentUser() {
      return this.$store.state.auth.user;
    },
    isAdmin() {
      if (this.currentUser && this.currentUser.roles) {
        return this.currentUser.roles.includes("ROLE_ADMIN");
      }
      return false;
    },
  },
  async mounted() {
    this.loading = true;
    await this.getCollections();
    await this.getAvailableBackups();
    this.loading = false;
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
      handler: function () {
        this.uploadedFiles.forEach(
          (file) => (this.progressInfos[file.name] = 0)
        );
      },
    },
  },
  methods: {
    async getCollections() {
      await CollectionService.getCollections("/collection", {
        projection: "collectionIdNameInfo",
        size: 1000,
      })
        .then((response) => {
          this.collections = response.data._embedded.collection;
          this.collections.sort((a, b) => a.name.localeCompare(b.name));
          this.collectionsAvailable = JSON.parse(
            JSON.stringify(this.collections)
          );
          // console.log("response.data", response.data)
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
    },
    onChangeList: function ({ source, destination }) {
      this.collectionsAvailable = source;
      this.collectionsToBackup = destination;
    },
    async backupCollections() {
      this.backingup = true;
      let ids = this.collectionsToBackup
        .map((collection) => collection.id)
        .join(",");
      // console.log(">>>>ids:", ids)

      await api
        .get("/utils/backup/collection/bulk", {
          params: {
            ids: ids,
            withCrawlSchedule: this.backupOptions.crawlSchedule,
            includeUsers: this.backupOptions.includeUsers,
          },
          responseType: "blob",
          headers: {
            "Content-Type": "application/json; application/octet-stream",
          },
        })
        .then((response) => {
          // console.log(">>>>>>>>>", response)
          let fileName =
            response.headers["content-disposition"].split("filename=")[1];
          let FILE = window.URL.createObjectURL(
            new Blob([response.data], { type: "application/zip" })
          );
          let docUrl = document.createElement("a");
          docUrl.href = FILE;
          docUrl.setAttribute("download", fileName);
          document.body.appendChild(docUrl);
          docUrl.click();

          this.notifyBackupErrors(response.headers["x-backup-errors"]);
        })
        .catch(async (errors) => {
          // console.log(errors);
          this.error = errors;
          const reason = await this.readBlobError(errors);
          EventBus.dispatch("toast", {
            type: "danger",
            msg: `Bulk backup failed: ${reason}`,
          });
        });

      await this.getAvailableBackups();
      this.backingup = false;
    },
    /**
     * Requests that use responseType: "blob" (to download a file on success) also receive
     * error bodies as a Blob, so the server's JSON error message has to be read out of it
     * manually before it can be shown to the user.
     */
    async readBlobError(errors) {
      const data = errors.response && errors.response.data;
      if (data instanceof Blob) {
        try {
          const parsed = JSON.parse(await data.text());
          return parsed.message || parsed.error || errors.message;
        } catch (e) {
          return errors.message;
        }
      }
      return (data && (data.message || data.error)) || errors.message;
    },
    notifyBackupErrors(errorHeader) {
      if (!errorHeader) {
        EventBus.dispatch("toast", {
          type: "success",
          msg: "All collection backups completed successfully.",
        });
        return;
      }

      // The header is a capped summary ({ count, failed: [{collectionId, collectionName}], truncated }),
      // not the full error detail - that's written to an _ERROR.json entry per collection in the zip,
      // so a large bulk backup (many failures) can't blow past the response header size limit.
      let summary;
      try {
        summary = JSON.parse(decodeURIComponent(errorHeader));
      } catch (e) {
        EventBus.dispatch("toast", {
          type: "danger",
          msg: "Some collection backups failed. See the _ERROR.json files in the downloaded zip.",
        });
        return;
      }

      const failed = summary.failed || [];
      this.backupErrors = failed;

      failed.forEach((f) => {
        EventBus.dispatch("toast", {
          type: "danger",
          msg: `Backup failed for "${f.collectionName}" (id ${f.collectionId}). See the _ERROR.json file in the downloaded zip for details.`,
        });
      });

      const shown = summary.truncated
        ? ` (showing first ${failed.length})`
        : "";
      EventBus.dispatch("toast", {
        type: "warning",
        msg: `${summary.count} of ${this.collectionsToBackup.length} collection backup(s) failed${shown}. An _ERROR.json file was added to the zip for each.`,
      });
    },
    async getAvailableBackups() {
      this.availableBackupsError = null;
      this.treeDisplayData[0].children = [];


      await api
        .get("/utils/backup/collection/bulk", {
          params: { listAvailableBackups: "true" },
        })
        .then((response) => {
          // console.log(">>>>> response:", response);
          let data = response.data;
          this.availableBackups = data;

          console.log("data", JSON.stringify(data, 2, null));

          data.forEach((row) => {
            // console.log("row:", row)
            let collectionNode = {
              // id: data.indexOf(row),
              name: Object.keys(row)[0],
              type: "folder",
              // checkable: false,
              children: [],
            };

            row[Object.keys(row)[0]].forEach((value) => {
              let fileNode = {
                // id: data.indexOf(row)+"_"+row[Object.keys(row)[0]].indexOf(value),
                name: value.file,
                date: value.date,
                // checkable: false,
                type: ".json",
              };
              collectionNode.children.push(fileNode);
            });

            this.treeDisplayData[0].children.push(collectionNode);
          });

          this.treeDisplayData[0].children.sort((a, b) =>
            a.name.toLowerCase() > b.name.toLowerCase() ? 1 : -1
          );
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
          this.availableBackupsError =
            errors.response?.data?.message ||
            errors.message ||
            "Failed to load available backups.";
        });
    },
    async restoreLocalBackup() {
      let files = this.serverFiles.map(
        (file) => file[Object.keys(file)[0]].name
      );

      let data = {
        files: files,
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
    print(value) {
      return JSON.stringify(value, null, 2);
    },
    normalizeCrawlSchedulerJobInfo,
    async getBackupFile(fileinfo) {
      let name = Object.keys(fileinfo)[0];
      let filename = fileinfo[Object.keys(fileinfo)[0]].name;
      let date = fileinfo[Object.keys(fileinfo)[0]].date;

      this.backupFileForView = { name: filename, date: date };

      await api
        .get("/utils/backup/collection/" + name + "/" + filename)
        .then((response) => {
          // console.log(">>>>> response:", response);
          let data = response.data;
          this.backupFileForView.data = data;
          // console.log("data", data)
        })
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
        formData.append("restoreCrawlSchedule", this.restoreOptions.crawlSchedule);
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
            this.message.push(response.data[0])
            this.localRestoreStatus = this.message
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
#demo {
  min-width: 50%;
}

/* Set a fixed scrollable wrapper */
.tableFixHead {
  height: 400px;
  overflow-y: auto;
  width: 100%;

  /* Set header to stick to the top of the container. */
  thead tr th {
    position: sticky;
    top: 0;
    z-index: 1;
  }

  table {
    table-layout: fixed;
  }

  td {
    vertical-align: middle;
    //white-space: nowrap;
    //text-overflow: ellipsis;
    //overflow: hidden;
    overflow-wrap: break-word;
  }
}

.drag-drop-zone {
  width: 100%;
  height: 250px;
  //border: 1px solid #e5e5e5;
  //border-radius: 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;

  &.dragover {
    background-color: #b6d1ec;
  }
  &.icon {
    width: 25px;
    height: 20px;
  }

  &.sub-text {
    font-size: 12px;
  }

  &.preview {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}
</style>
<script setup></script>