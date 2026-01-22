<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="container-fluid px-4">
    <Breadcrumb />

    <!-- Report Description -->
    <div class="card mb-3">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>About This Report</b>
      </div>
      <div class="card-body">
        <p>
          The <strong>Keywords and Phrases Report</strong> searches selected collections
          for exact phrase matches. Use this report to find all pages containing specific
          keywords or phrases across your collections.
        </p>
        <p class="mb-2"><strong>Fields Searched:</strong></p>
        <ul class="mb-2">
          <li><strong>Content</strong> - The main body text of each page</li>
          <li><strong>Title</strong> - The page title</li>
          <li><strong>Anchor Text</strong> - Text from links pointing to the page</li>
          <li><strong>URL &amp; Host</strong> - The page URL and domain</li>
          <li><strong>Meta Tags</strong> - HTML meta tags including:
            <ul>
              <li>Standard meta tags (description, keywords, author)</li>
              <li>Dublin Core metadata (dc.title, dc.creator, dc.subject, etc.)</li>
              <li>Open Graph metadata (og:title, og:description, etc.)</li>
              <li>Twitter Card metadata (twitter:title, twitter:description, etc.)</li>
              <li>Custom metadata fields specific to collections</li>
            </ul>
          </li>
        </ul>
        <p class="mb-0">
          <strong>Results Include:</strong> Page URL, title, and anchor text from links
          pointing to each matching page. Results are organized by collection and search term.
        </p>
      </div>
    </div>

    <!-- Collection Selection Card -->
    <div class="card mb-3">
      <div class="card-header">
        <i class="fas fa-sitemap me-1"></i>
        <b>Select Collections</b>
      </div>
      <div class="card-body">
        <div v-if="loading" class="d-flex justify-content-center">
          <div class="spinner-border text-primary" role="status">
            <span class="sr-only">Loading...</span>
          </div>
        </div>
        <Multiselect
          v-else
          v-model="collectionsSelected"
          :options="collections"
          :multiple="true"
          :searchable="true"
          :close-on-select="false"
          :clear-on-select="false"
          :preserve-search="true"
          trackBy="id"
          label="name"
          placeholder="Select collections..."
          :loading="loading"
        >
          <template #beforeList>
            <div class="multiselect-select-actions">
              <button
                type="button"
                class="btn btn-sm btn-outline-primary me-2"
                @click.stop="selectAllCollections"
              >
                Select All
              </button>
              <button
                type="button"
                class="btn btn-sm btn-outline-secondary"
                @click.stop="clearAllCollections"
              >
                Clear All
              </button>
            </div>
          </template>
          <template #noResult>
            No collections found matching your search.
          </template>
          <template #noOptions>
            No collections available.
          </template>
        </Multiselect>
        <small class="text-muted mt-1 d-block">
          {{ collectionsSelected.length }} collection(s) selected
        </small>
      </div>
    </div>

    <!-- Search Terms Card -->
    <div class="card mb-3">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Search Terms</b>
      </div>
      <div class="card-body">
        <div class="row">
          <div class="col-md-6">
            <label class="form-label">Upload CSV File</label>
            <input
              type="file"
              class="form-control"
              accept=".csv,.txt"
              ref="fileInput"
              @change="handleFileUpload"
            />
            <small class="text-muted">
              Upload a CSV or text file with words/phrases (one per line or comma-separated)
            </small>
          </div>
          <div class="col-md-6">
            <label class="form-label">Or Enter Manually</label>
            <textarea
              class="form-control"
              rows="4"
              v-model="manualTerms"
              placeholder="Enter words or phrases, one per line or comma-separated"
            ></textarea>
          </div>
        </div>
        <div class="mt-3">
          <span class="badge bg-primary">{{ searchTerms.length }} search terms loaded</span>
          <button
            class="btn btn-sm btn-outline-secondary ms-2"
            @click="clearTerms"
            v-if="searchTerms.length"
          >
            Clear All
          </button>
          <button
            class="btn btn-sm btn-outline-info ms-2"
            @click="showTermsPreview = !showTermsPreview"
            v-if="searchTerms.length"
          >
            {{ showTermsPreview ? 'Hide' : 'Show' }} Terms
          </button>
        </div>
        <div v-if="showTermsPreview && searchTerms.length" class="mt-2">
          <div class="border rounded p-2 bg-light" style="max-height: 150px; overflow-y: auto;">
            <span
              v-for="(term, index) in searchTerms"
              :key="index"
              class="badge bg-secondary me-1 mb-1"
            >
              {{ term }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Generate Button -->
    <div class="d-flex justify-content-end mb-3">
      <button
        class="btn btn-primary"
        @click="generateReport"
        :disabled="!canGenerate || generating"
      >
        <template v-if="generating">
          <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
          Generating...
        </template>
        <template v-else>
          <i class="fas fa-file-alt me-1"></i> Generate Report
        </template>
      </button>
    </div>

    <!-- Results Card (shown when report exists) -->
    <div class="card mb-3" v-if="reportData">
      <div class="card-header d-flex justify-content-between align-items-center">
        <div>
          <i class="fas fa-chart-bar me-1"></i>
          <b>Report Results</b>
        </div>
        <button class="btn btn-sm btn-success" @click="exportCsv" :disabled="exporting">
          <template v-if="exporting">
            <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
            Exporting...
          </template>
          <template v-else>
            <i class="fas fa-download me-1"></i> Export CSV
          </template>
        </button>
      </div>
      <div class="card-body">
        <!-- Summary -->
        <div class="alert alert-info">
          <strong>Total Matches:</strong> {{ reportData.totalMatches }} URLs found
          in {{ collectionsWithResults.length }} of {{ totalCollectionsSearched }} collection(s)
          <span class="ms-3">
            <strong>Generated:</strong> {{ formatDate(reportData.generatedAt) }}
          </span>
        </div>

        <!-- Results by Collection (only showing collections with results) -->
        <div class="accordion" id="resultsAccordion">
          <div
            class="accordion-item"
            v-for="(collection, collIdx) in collectionsWithResults"
            :key="collection.collectionId"
          >
            <h2 class="accordion-header" :id="'collectionHeading' + collIdx">
              <button
                class="accordion-button"
                :class="{ collapsed: collIdx > 0 }"
                type="button"
                data-bs-toggle="collapse"
                :data-bs-target="'#collectionCollapse' + collIdx"
                :aria-expanded="collIdx === 0"
                :aria-controls="'collectionCollapse' + collIdx"
              >
                {{ collection.collectionName }}
                <span class="badge bg-secondary ms-2">{{ getTotalUrls(collection) }} URLs</span>
              </button>
            </h2>
            <div
              :id="'collectionCollapse' + collIdx"
              class="accordion-collapse collapse"
              :class="{ show: collIdx === 0 }"
              :aria-labelledby="'collectionHeading' + collIdx"
              data-bs-parent="#resultsAccordion"
            >
              <div class="accordion-body p-2">
                <div v-if="collection.matches.length === 0" class="text-muted">
                  No matches found for this collection.
                </div>
                <!-- Nested Terms Accordion -->
                <div class="accordion" :id="'termsAccordion' + collIdx">
                  <div
                    class="accordion-item"
                    v-for="(match, termIdx) in collection.matches"
                    :key="match.searchTerm"
                  >
                    <h2 class="accordion-header" :id="'termHeading' + collIdx + '_' + termIdx">
                      <button
                        class="accordion-button collapsed term-accordion-button"
                        type="button"
                        data-bs-toggle="collapse"
                        :data-bs-target="'#termCollapse' + collIdx + '_' + termIdx"
                        aria-expanded="false"
                        :aria-controls="'termCollapse' + collIdx + '_' + termIdx"
                      >
                        <i class="fas fa-search me-2"></i>
                        "{{ match.searchTerm }}"
                        <span class="badge bg-primary ms-2">{{ match.count }} results</span>
                      </button>
                    </h2>
                    <div
                      :id="'termCollapse' + collIdx + '_' + termIdx"
                      class="accordion-collapse collapse"
                      :aria-labelledby="'termHeading' + collIdx + '_' + termIdx"
                      :data-bs-parent="'#termsAccordion' + collIdx"
                    >
                      <div class="accordion-body py-2">
                        <ul class="list-group list-group-flush">
                          <li
                            class="list-group-item"
                            v-for="url in match.urls"
                            :key="url.url"
                          >
                            <div>
                              <strong>{{ url.title || url.anchor || 'No title' }}</strong>
                            </div>
                            <a :href="url.url" target="_blank" rel="noopener noreferrer" class="text-break">
                              {{ url.url }}
                            </a>
                          </li>
                        </ul>
                        <div v-if="match.urls.length === 0" class="text-muted">
                          No URLs found for this term.
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty Results Message -->
        <div v-if="collectionsWithResults.length === 0" class="alert alert-warning">
          No results found for the selected collections and search terms.
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import Breadcrumb from "../../components/Breadcrumb.vue";
import Multiselect from "vue-multiselect";
import CollectionService from "../../services/collection.service";
import KeywordsAndPhrasesReportService from "../../services/keywords-and-phrases-report.service";

export default {
  name: "KeywordsAndPhrasesReport",
  components: {
    Breadcrumb,
    Multiselect,
  },
  data() {
    return {
      loading: false,
      generating: false,
      exporting: false,
      error: null,
      collections: [],
      collectionsSelected: [],
      manualTerms: "",
      fileTerms: [],
      reportData: null,
      showTermsPreview: false,
    };
  },
  computed: {
    searchTerms() {
      // Combine file terms and manual terms
      const manual = this.manualTerms
        .split(/[\n,]/)
        .map((t) => t.trim())
        .filter((t) => t.length > 0);
      // Deduplicate combined terms
      return [...new Set([...this.fileTerms, ...manual])];
    },
    canGenerate() {
      return this.collectionsSelected.length > 0 && this.searchTerms.length > 0;
    },
    collectionsWithResults() {
      if (!this.reportData || !this.reportData.byCollection) return [];
      return this.reportData.byCollection.filter(
        (collection) => this.getTotalUrls(collection) > 0
      );
    },
    totalCollectionsSearched() {
      if (!this.reportData || !this.reportData.byCollection) return 0;
      return this.reportData.byCollection.length;
    },
  },
  async mounted() {
    this.loading = true;
    await this.getCollections();
    this.loading = false;
  },
  methods: {
    async getCollections() {
      try {
        const response = await CollectionService.getCollections("/collection", {
          projection: "collectionIdNameInfo",
          size: 1000,
        });
        this.collections = response.data._embedded.collection;
        this.collections.sort((a, b) => a.name.localeCompare(b.name));
      } catch (err) {
        this.error = err.message || err.toString();
      }
    },
    selectAllCollections() {
      this.collectionsSelected = [...this.collections];
    },
    clearAllCollections() {
      this.collectionsSelected = [];
    },
    handleFileUpload(event) {
      const file = event.target.files[0];
      if (!file) return;

      const reader = new FileReader();
      reader.onload = (e) => {
        const text = e.target.result;
        this.fileTerms = text
          .split(/[\n,]/)
          .map((t) => t.trim())
          .filter((t) => t.length > 0);
      };
      reader.onerror = () => {
        this.error = "Failed to read the uploaded file.";
      };
      reader.readAsText(file);
    },
    clearTerms() {
      this.fileTerms = [];
      this.manualTerms = "";
      this.showTermsPreview = false;
      // Clear the file input
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = "";
      }
    },
    async generateReport() {
      this.generating = true;
      this.error = null;
      this.reportData = null;

      try {
        const collectionIds = this.collectionsSelected.map((c) => c.id);
        const response = await KeywordsAndPhrasesReportService.generateReport(
          collectionIds,
          this.searchTerms
        );
        this.reportData = response.data;
      } catch (err) {
        this.error =
          err.response?.data?.error || err.message || "Report generation failed";
      } finally {
        this.generating = false;
      }
    },
    async exportCsv() {
      this.exporting = true;
      try {
        const collectionIds = this.collectionsSelected.map((c) => c.id);
        const response = await KeywordsAndPhrasesReportService.exportCsv(
          collectionIds,
          this.searchTerms
        );

        // Create download link
        const blob = new Blob([response.data], { type: "text/csv" });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `Keywords_and_Phrases_Report_${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } catch (err) {
        this.error = "CSV export failed: " + (err.message || err.toString());
      } finally {
        this.exporting = false;
      }
    },
    getTotalUrls(collection) {
      return collection.matches.reduce((sum, m) => sum + m.count, 0);
    },
    formatDate(dateStr) {
      if (!dateStr) return "N/A";
      return new Date(dateStr).toLocaleString();
    },
  },
};
</script>

<style lang="scss" scoped>
.error {
  color: #dc3545;
  margin-bottom: 1rem;
}

.multiselect-select-actions {
  padding: 0.5rem;
  border-bottom: 1px solid #dee2e6;
  background-color: #f8f9fa;
}

.accordion-button:not(.collapsed) {
  background-color: #e7f1ff;
  color: #0c63e4;
}

// Nested terms accordion styling
.term-accordion-button {
  padding: 0.5rem 1rem;
  font-size: 0.95rem;
  background-color: #f8f9fa;

  &:not(.collapsed) {
    background-color: #d1e7dd;
    color: #0f5132;
  }
}

.list-group-item {
  border-left: none;
  border-right: none;
  padding: 0.5rem 1rem;
}

.list-group-item:first-child {
  border-top: none;
}

.list-group-item:last-child {
  border-bottom: none;
}

.text-break {
  word-break: break-all;
}
</style>
