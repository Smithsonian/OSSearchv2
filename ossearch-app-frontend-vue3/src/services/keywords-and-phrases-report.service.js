import api from './api';

class KeywordsAndPhrasesReportService {
  /**
   * Generate Keywords and Phrases report for specified collections and search terms
   * @param {number[]} collectionIds - Array of collection IDs to search
   * @param {string[]} searchTerms - Array of search terms/phrases
   * @returns {Promise} API response with report data
   */
  generateReport(collectionIds, searchTerms) {
    return api.post('/reports/keywords-and-phrases', {
      collectionIds: collectionIds,
      searchTerms: searchTerms
    });
  }

  /**
   * Export Keywords and Phrases report as CSV file
   * @param {number[]} collectionIds - Array of collection IDs to search
   * @param {string[]} searchTerms - Array of search terms/phrases
   * @returns {Promise} API response with CSV blob
   */
  exportCsv(collectionIds, searchTerms) {
    return api.post('/reports/keywords-and-phrases/export', {
      collectionIds: collectionIds,
      searchTerms: searchTerms
    }, {
      responseType: 'blob'
    });
  }
}

export default new KeywordsAndPhrasesReportService();
