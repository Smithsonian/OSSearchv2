<template>
  <div class="card mb-3">
    <div class="card-header">
      <i class="fas fa-clock me-1"></i>
      <b>Scheduled Backups</b>
    </div>
    <div class="card-body">
      <div v-if="loading" class="d-flex justify-content-center">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">Loading...</span>
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
            <!--
              Mutually exclusive with the "Not enabled on this node" branch above, which
              already names both hosts: without this guard the disabled-plus-remote-last-run
              case renders the same hostname message twice.
            -->
            <div
              v-if="status.node !== status.lastRun.hostname && status.enabledOnThisNode !== false"
              class="text-muted small mb-2"
            >
              Answered by <code>{{ status.node }}</code>; last run executed on <code>{{ status.lastRun.hostname }}</code>.
            </div>

            <dl class="row mb-0">
              <dt class="col-sm-3">Last backup</dt>
              <dd class="col-sm-9">{{ formatDateTime(status.lastRun.finishedAt || status.lastRun.startedAt) }}</dd>

              <dt class="col-sm-3">Status</dt>
              <dd class="col-sm-9">
                <span :class="statusBadgeClass(status.lastRun.status)">{{ statusLabel(status.lastRun.status) }}</span>
              </dd>

              <dt class="col-sm-3">Collections</dt>
              <dd class="col-sm-9">
                {{ status.lastRun.collectionsSucceeded }} / {{ status.lastRun.collectionsTotal }} succeeded
                <span v-if="status.lastRun.collectionsFailed > 0" class="text-danger">
                  ({{ status.lastRun.collectionsFailed }} failed)
                </span>
              </dd>

              <!--
                Retention is reported separately from the run status on purpose: a run can
                finish with status=SUCCESS while retention_status=FAILED, and showing only
                the green SUCCESS pill is exactly the indistinguishability the backend's
                retention Outcome enum exists to eliminate.
              -->
              <template v-if="status.lastRun.retentionStatus">
                <dt class="col-sm-3">Retention</dt>
                <dd class="col-sm-9">
                  <span :class="retentionBadgeClass(status.lastRun.retentionStatus)">
                    {{ status.lastRun.retentionStatus }}
                  </span>
                </dd>
              </template>

              <!--
                A SKIPPED outcome carries its reason in the same retentionError field, so this
                row is always rendered - only its label and colour change, so the reason is
                shown rather than swallowed.
              -->
              <template v-if="status.lastRun.retentionError">
                <dt class="col-sm-3">
                  {{ retentionDetailIsError() ? "Retention error" : "Retention skipped" }}
                </dt>
                <dd :class="retentionDetailIsError() ? 'col-sm-9 text-danger' : 'col-sm-9 text-muted'">
                  {{ status.lastRun.retentionError }}
                </dd>
              </template>

              <template v-if="status.lastRun.errorMessage">
                <dt class="col-sm-3">Error</dt>
                <dd class="col-sm-9 text-danger">{{ status.lastRun.errorMessage }}</dd>
              </template>
            </dl>
          </div>

          <dl class="row mb-2 mt-3">
            <dt class="col-sm-3">Next backup</dt>
            <dd class="col-sm-9 mb-0">
              <!--
                nextRun and nextRunNote are NOT alternatives: the backend sets both together
                when the cron expression has a next fire time but scheduled backups are not
                enabled on this node, which is precisely when the note matters. Render the
                note alongside the time rather than instead of it.
              -->
              <template v-if="status.nextRun">
                {{ formatDateTime(status.nextRun) }}
              </template>
              <span v-else-if="!status.nextRunNote" class="text-muted">-</span>
              <div v-if="status.nextRunNote" class="text-muted small">
                {{ status.nextRunNote }}
              </div>
            </dd>
          </dl>

          <div v-if="status.retention" class="mt-3 pt-3 border-top text-muted small">
            <div v-if="status.retention.days">
              Backups older than {{ status.retention.days }} days are deleted.
            </div>
            <div v-if="status.retention.error" class="text-warning">
              Retention preview unavailable: {{ status.retention.error }}
            </div>
            <div v-else-if="retentionCandidateCount !== null">
              {{ retentionCandidateCount }}
              {{ retentionCandidateCount === 1 ? "backup" : "backups" }}
              will be pruned next run.<span v-if="status.retention.candidateTruncated">
                (candidate list truncated)</span>
            </div>
          </div>
        </template>

        <div class="mt-3">
          <button
            class="btn btn-primary btn-sm"
            type="button"
            :disabled="loading"
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
  computed: {
    // null means "no preview in this response" (the caller did not ask for one, or the
    // backend omitted it), which is distinct from a preview that found nothing to prune.
    retentionCandidateCount() {
      const count = this.status && this.status.retention
        ? this.status.retention.candidateCount
        : null;
      return typeof count === "number" ? count : null;
    },
  },
  mounted() {
    this.fetchStatus();
  },
  methods: {
    fetchStatus() {
      this.loading = true;
      this.failedToLoad = false;
      // The retention preview walks the whole crawlDir on the backend, so it is opt-in
      // per request. This component renders the result, so it asks for it.
      BackupService.getScheduledBackupStatus(true)
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
    // Run status and retention outcome are two separate backend vocabularies that happen to
    // sit in the same <dl>: the run reports SUCCESS/PARTIAL_FAILURE/ABORTED_*/RUNNING/STALE,
    // retention reports COMPLETED/SKIPPED/FAILED. They only share the token FAILED. They were
    // briefly served by one helper; keeping them separate means neither has to carry a case
    // that can never occur in its own column.
    statusBadgeClass(status) {
      switch (status) {
        case "SUCCESS":
          return "badge rounded-pill bg-success text-success bg-opacity-25";
        case "FAILED":
          return "badge rounded-pill bg-danger text-danger bg-opacity-25";
        case "PARTIAL_FAILURE":
        case "ABORTED_LOW_DISK":
          return "badge rounded-pill bg-warning text-warning bg-opacity-25";
        // Deliberately NOT the same treatment as ABORTED_LOW_DISK: "the crawlDir mount is
        // gone" is an infrastructure outage, not a capacity warning, and must not read like
        // one at a glance.
        case "ABORTED_CRAWL_DIR_UNAVAILABLE":
          return "badge rounded-pill bg-dark text-dark bg-opacity-25";
        case "RUNNING":
          return "badge rounded-pill bg-primary text-primary bg-opacity-25";
        // A run the status service reconciled because it started longer ago than the lease
        // duration and never finished (typically a JVM kill mid-run). It means "we do not
        // know how this ended", so it must not wear RUNNING's active blue nor FAILED's red -
        // muted grey is the honest reading.
        case "STALE":
          return "badge rounded-pill bg-secondary text-secondary bg-opacity-25";
        default:
          return "badge rounded-pill bg-warning text-warning bg-opacity-25";
      }
    },
    // The raw enum names are readable enough except for the multi-word ones.
    statusLabel(status) {
      switch (status) {
        case "ABORTED_LOW_DISK":
          return "ABORTED - LOW DISK";
        case "ABORTED_CRAWL_DIR_UNAVAILABLE":
          return "ABORTED - CRAWL DIR UNAVAILABLE";
        case "STALE":
          return "STALE - OUTCOME UNKNOWN";
        default:
          return status;
      }
    },
    retentionBadgeClass(outcome) {
      switch (outcome) {
        case "COMPLETED":
          return "badge rounded-pill bg-success text-success bg-opacity-25";
        case "FAILED":
          return "badge rounded-pill bg-danger text-danger bg-opacity-25";
        // Retention was deliberately not run this cycle (every collection failed, or the
        // lease was lost mid-run). Neither a success nor a failure, so neither green nor
        // red: informational blue, with the reason spelled out in the row below.
        case "SKIPPED":
          return "badge rounded-pill bg-info text-info bg-opacity-25";
        default:
          return "badge rounded-pill bg-warning text-warning bg-opacity-25";
      }
    },
    // SKIPPED reuses the retention_error column to carry its reason, which is not an error.
    // Label and colour that row for what it actually is, so a deliberate skip does not read
    // as a fault.
    retentionDetailIsError() {
      return !this.status
        || !this.status.lastRun
        || this.status.lastRun.retentionStatus !== "SKIPPED";
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
