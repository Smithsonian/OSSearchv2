import api from './api';

class BackupService {

  getScheduledBackupStatus() {
    return api.get("/utils/backup/scheduled/status");
  }

}

export default new BackupService();
