<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="container-fluid px-4">
    <Breadcrumb />

    <div class="card mb-3">
      <div class="card-header">
        <i class="fas fa-cloud-download-alt me-1"></i>
        <b>Reindex Collections</b>
      </div>
      <div class="card-body">
        <DualListBox
          :source="collectionsAvailable"
          :destination="collectionsToReindex"
          label="jobName"
          @onChangeList="onChangeList"
        >
          <template v-slot:source>
            <h5>Collections Available for Reindex</h5>
          </template>
          <template v-slot:destination>
            <h5>Selected For Reindex</h5>
          </template>
        </DualListBox>

        <fieldset :disabled="!collectionsToReindex.length">
          <div class="row justify-content-end align-items-end mt-3">
            <div class="col-2">
              <button
                class="btn btn-primary float-end"
                type="button"
                @click.prevent="reindexCollections()"
              >
                <template v-if="reindexing">
                  <span
                    class="spinner-border spinner-border-sm"
                    role="status"
                    aria-hidden="true"
                  ></span>
                  Reindexing Collections...
                </template>
                <template v-else> Reindex </template>
              </button>
            </div>
          </div>
        </fieldset>
      </div>
    </div>
  </div>

  <div ref="modal" class="modal fade" :class="{ show: toggleModal, 'd-block': toggleModal }" id="reindexResponse" tabindex="-1" aria-labelledby="reindexResponse" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title"><b>Reindex Response</b></h5>
          <button type="button" class="btn-close" aria-label="Close" @click="toggleModal = !toggleModal"></button>
        </div>
        <div class="modal-body">
          <table class="table table-hover">
            <thead>
              <tr>
                <th>Collection</th>
                <th>Reindex Status</th>
                <th>View History</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in message" :key="job">
                <td>{{job.jobName}}</td>
                <td>{{job.status}}</td>
                <td><router-link class="btn-link link-primary" :to="{name: 'crawlLogs', params: { jobName: job.jobName, jobGroup: job.groupName }}" target="_blank"><i class="fas fa-eye"></i></router-link></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-primary" @click="toggleModal = !toggleModal">Close</button>
        </div>
      </div>
    </div>
  </div>


</template>

<script>
import Breadcrumb from "../components/Breadcrumb.vue";
import DualListBox from "../components/DualListBox.vue";
import api from "../services/api";

export default {
  name: "Reindex",
  components: {
    DualListBox,
    Breadcrumb
  },
  data() {
    return {
      loading: false,
      reindexing: false,
      error: null,
      collectionsAvailable: [],
      collectionsToReindex: [],
      reindexOptions: {
        includeUsers: false,
        crawlSchedule: true,
      },
      progressInfos: {},
      message: [],
      toggleModal: false,
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
    await this.getCollectionsWithCrawSchedule();
    this.loading = false;
  },
  watch: {
    collectionsAvailable: {
      handler: function () {
        this.collectionsAvailable.sort((a, b) => a.jobName.localeCompare(b.jobName));
      }
    },
    collectionsToReindex: {
      handler: function () {
        this.collectionsToReindex.sort((a, b) => a.jobName.localeCompare(b.jobName));
      }
    }
  },
  methods: {
    async getCollectionsWithCrawSchedule() {
      await api.get("/scheduler/getAllJobs2")
        .then((response) => {
          this.collectionsAvailable = response.data;
          this.collectionsAvailable.sort((a, b) => a.jobName.localeCompare(b.jobName));
          this.collectionsAvailable = JSON.parse(
            JSON.stringify(this.collectionsAvailable)
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
      this.collectionsToReindex = destination;
    },
    async reindexCollections() {
      this.reindexing = true;

      await api
        .post("/utils/reindex", this.collectionsToReindex)
        .then((response) => {
          console.log(">>>>>>>>>", response)
          this.message = response.data
        })
        .catch((errors) => {
          // console.log(errors);
          this.error = errors;
        });
      this.reindexing = false;
      this.toggleModal = !this.toggleModal
    },
    print(value) {
      return JSON.stringify(value, null, 2);
    }
  },
};
</script>

<style lang="scss" scoped>
</style>