<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-search me-1"></i>
      <b>Search Keymatch Configuration</b>
      <div class="float-end">
          <button v-if="!isEditing" class="btn btn-sm btn-primary" @click="startEditing">Edit</button>
          <template v-else>
            <button class="btn btn-sm btn-success me-2" @click="saveChanges">Save</button>
            <button class="btn btn-sm btn-danger" @click="cancelEditing">Cancel</button>
          </template>
      </div>
    </div>
    <div class="card-body">
      <div class="row g-3 mb-3">
        <div class="col-md-12">
          <legend class="col-form-label">Keymatches&nbsp;
            <i class="fas fa-info-circle text-primary"></i>
          </legend>
          <div class="card card-body">
            <div class="form-text">
              <!-- Keymatch description text -->
              <p>Keymatches enable you to force certain documents to the top of search results. When users search with a
                term that you specify, the search appliance always presents the KeyMatch first. Users can navigate
                immediately to the featured document and spend less time searching and viewing less relevant
                documents. You also might consider using KeyMatches to promote documents that are too new to be in the search index or might not appear among the highest search results.</p>

              <p>A KeyMatch associates a specific term with a particular result, enabling you to direct users to a result that is especially relevant but that might not appear at the top of the results page. For example, if a department is releasing a new Operations web page that should be returned for certain types of queries, you can direct users to that new web page by associating specific search terms, such as operations, with the new web page. A link for that page is returned at the top of search results for queries containing the term operations.</p>

              <p>This feature is especially useful for directing users to web pages that are not yet part of the production index or that have very few links to them, causing them to appear further down in the results list than you would like.</p>
              <h6><b>Setting Up KeyMatches</b></h6>
              <p>You set up a KeyMatch by matching a search term to a specific URL and specifying a title for the
                match</p>
              <p>To create a KeyMatch, you must specify the word, phrase, or exact match criteria for which a specific result will be returned. The rules for creating a KeyMatch are described in the following table.</p>
              <div><b>Title for Match:</b> A description for Title for Match.</div>
              <div><b>Search Term:</b> A description for Search Term.</div>
              <div><b>Keymatch Type:</b> A description for Keymatch Type.</div>
              <ul>
                <li><b>KeywordMatch:</b> A word that must appear anywhere in query.</li>
                <li><b>PhraseMatch:</b> A phrase that appears anywhere in query. For the phrase to match, all of the words must be present, the order of the words must be the same with no intervening words, and any hyphens in the query must be matched.</li>
                <li><b>ExactMatch:</b> Phrase must exactly match the query.</li>
              </ul>
              <div><b>URL for Match:</b> A URL for Match.</div>
              <div><b>Image URL for Match:</b> An image url for Match.</div>
            </div>
          </div>
        </div>
      </div>
      <div class="row g-3">
        <div class="col-md-12">
          <Keymatch
          ref="keymatchComponent"
          :isEditing="isEditing"
          :collectionName="name"
          @update:keymatches="updateKeymatches"
          />
        </div>
      </div>
    </div>
  </div>

</template>

<script>
import Keymatch from "@/components/collections/Keymatch";

export default {
  name: 'CollectionSearchKeymatchConfig',
  components: {
    Keymatch
  },
  props: {
    name: {
      type: String,
      required: true
    }
  },
  data() {
    return {
      isEditing: false,
      originalKeymatches: []
    };
  },
  methods: {
    startEditing() {
      this.isEditing = true;
      this.originalKeymatches = JSON.parse(JSON.stringify(this.$refs.keymatchComponent.keymatches));
    },
    async saveChanges() {
      try {
        await this.$refs.keymatchComponent.saveKeymatches();
        this.isEditing = false;
      } catch (error) {
        console.error('Error saving keymatches:', error);
        // Handle error (e.g., show error message to user)
      }
    },
    cancelEditing() {
      this.isEditing = false;
      this.$refs.keymatchComponent.resetKeymatches(this.originalKeymatches);
    },
    updateKeymatches(newKeymatches) {
      // Handle any necessary updates in the parent component
      console.log('Keymatches updated:', newKeymatches);
    },
    handleError(error) {
      // Failed API requests are surfaced globally as toasts by the axios
      // interceptor; only track local state here.
      this.error = error;
    }
  }
}
</script>

<style scoped>

</style>