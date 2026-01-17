import api from './api';

class SD603ReportService {
  /**
   * Generate SD603 report for specified collections and search terms
   * @param {number[]} collectionIds - Array of collection IDs to search
   * @param {string[]} searchTerms - Array of search terms/phrases
   * @returns {Promise} API response with report data
   */
  generateReport(collectionIds, searchTerms) {
    return api.post('/reports/sd603', {
      collectionIds: collectionIds,
      searchTerms: searchTerms
    });
  }

  /**
   * Export SD603 report as CSV file
   * @param {number[]} collectionIds - Array of collection IDs to search
   * @param {string[]} searchTerms - Array of search terms/phrases
   * @returns {Promise} API response with CSV blob
   */
  exportCsv(collectionIds, searchTerms) {
    return api.post('/reports/sd603/export', {
      collectionIds: collectionIds,
      searchTerms: searchTerms
    }, {
      responseType: 'blob'
    });
  }
}

export default new SD603ReportService();
