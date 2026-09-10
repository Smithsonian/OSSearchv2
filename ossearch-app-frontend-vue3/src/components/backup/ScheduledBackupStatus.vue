<template>
  <div class="card mb-3">
    <div class="card-header">
      <i class="fas fa-clock me-1"></i>
      <b>Scheduled Backups</b>
    </div>
    <div class="card-body">
      <div v-if="loading" class="d-flex justify-content-center">
        <div class="spinner-border text-primary" role="status">
          <span class="sr-only">Loading...</span>
        </div>
      </div>

      <template v-else>
        <div v-if="failedToLoad" class="text-muted">
          Unable to load scheduled backup status.
        </div>

        <template v-else-if="status">
          <div v-if="status.error" class="alert alert-warning">
            Could not read scheduled backup status: {{ status.error }}
          </div>

          <div v-if="status.enabledOnThisNode === false && !status.lastRun" class="alert alert-secondary">
            Scheduled backups are currently disabled.
          </div>
          <div
            v-else-if="status.enabledOnThisNode === false && status.lastRun && status.lastRun.hostname !== status.node"
            class="text-muted"
          >
            Not enabled on this node ({{ status.node }}); last run executed on {{ status.lastRun.hostname }}.
          </div>


          <div v-if="!status.lastRun" class="text-muted">
            No scheduled backup has run yet.
          </div>

          <div v-else>
            <div v-if="status.node !== status.lastRun.hostname" class="text-muted small mb-2">
              Answered by <code>{{ status.node }}</code>; last run executed on <code>{{ status.lastRun.hostname }}</code>.
            </div>

            <dl class="row mb-0">
              <dt class="col-sm-3">Last backup</dt>
              <dd class="col-sm-9">{{ formatDateTime(status.lastRun.finishedAt || status.lastRun.startedAt) }}</dd>

              <dt class="col-sm-3">Status</dt>
              <dd class="col-sm-9">
                <span :class="statusBadgeClass(status.lastRun.status)">{{ status.lastRun.status }}</span>
              </dd>

              <dt class="col-sm-3">Collections</dt>
              <dd class="col-sm-9">
                {{ status.lastRun.collectionsSucceeded }} / {{ status.lastRun.collectionsTotal }} succeeded
                <span v-if="status.lastRun.collectionsFailed > 0" class="text-danger">
                  ({{ status.lastRun.collectionsFailed }} failed)
                </span>
              </dd>

              <template v-if="status.lastRun.errorMessage">
                <dt class="col-sm-3">Error</dt>
                <dd class="col-sm-9 text-danger">{{ status.lastRun.errorMessage }}</dd>
              </template>
            </dl>
          </div>

          <dl class="row mb-2 mt-3">
            <dt class="col-sm-3">Next backup</dt>
            <dd class="col-sm-9 mb-0">
              <template v-if="status.nextRun">
                {{ formatDateTime(status.nextRun) }}
              </template>
              <span v-else-if="status.nextRunNote" class="text-muted">
                {{ status.nextRunNote }}
              </span>
              <span v-else class="text-muted">-</span>
            </dd>
          </dl>

          <div class="mt-3 pt-3 border-top text-muted small">
            Automated backups will be deleted after 90 days.
          </div>
        </template>

        <div class="mt-3">
          <button
            class="btn btn-primary btn-sm"
            type="button"
            @click="fetchStatus()"
          >
            Refresh
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import BackupService from "../../services/backup.service";

export default {
  name: "ScheduledBackupStatus",
  data() {
    return {
      loading: false,
      status: null,
      failedToLoad: false,
    };
  },
  mounted() {
    this.fetchStatus();
  },
  methods: {
    fetchStatus() {
      this.loading = true;
      this.failedToLoad = false;
      BackupService.getScheduledBackupStatus()
        .then((response) => {
          this.status = response.data;
        })
        .catch(() => {
          this.failedToLoad = true;
        })
        .finally(() => {
          this.loading = false;
        });
    },
    statusBadgeClass(status) {
      switch (status) {
        case "SUCCESS":
          return "badge rounded-pill bg-success text-success bg-opacity-25";
        case "FAILED":
          return "badge rounded-pill bg-danger text-danger bg-opacity-25";
        case "PARTIAL_FAILURE":
        case "ABORTED_LOW_DISK":
          return "badge rounded-pill bg-warning text-warning bg-opacity-25";
        case "RUNNING":
          return "badge rounded-pill bg-primary text-primary bg-opacity-25";
        default:
          return "badge rounded-pill bg-warning text-warning bg-opacity-25";
      }
    },
    formatDateTime(value) {
      if (!value) {
        return "-";
      }
      return new Date(value).toLocaleString();
    },
  },
};
</script>
