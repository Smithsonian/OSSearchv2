import api from './api';

class BackupService {

  /**
   * @param {boolean} includeRetentionPreview ask the backend to also compute which backup
   *   files the next retention sweep would prune. That preview walks the whole crawlDir over
   *   NFS, so it is off by default and only requested by callers that actually render it.
   */
  getScheduledBackupStatus(includeRetentionPreview = false) {
    return api.get("/utils/backup/scheduled/status", {
      params: {includeRetentionPreview: includeRetentionPreview}
    });
  }

}

export default new BackupService();
