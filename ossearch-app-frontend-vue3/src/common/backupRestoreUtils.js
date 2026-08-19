/**
 * crawlSchedulerJobInfo in a backup file may be an array (current export format) or a single
 * object (legacy backups already on disk) - normalize both into an array of jobs, mirroring the
 * instanceof JSONArray / JSONObject handling on the restore side in BackupRestoreServiceImpl.
 *
 * Used by both BackupRestore.vue and BackupRestoreCollection.vue's raw backup-file preview
 * modals, which read arbitrary historical backup files and so must handle either shape.
 */
export function normalizeCrawlSchedulerJobInfo(data) {
  if (Array.isArray(data)) {
    return data;
  }
  if (data && typeof data === "object" && Object.keys(data).length) {
    return [data];
  }
  return [];
}
