<template>
  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div v-if="loading" class="d-flex justify-content-center">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  </div>

  <div v-if="!loading" class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-search me-1"></i>
      <b>Search Response Configuration</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditSearchConfig = JSON.parse(JSON.stringify(collection)); isEditSearchConfig = !isEditSearchConfig" v-if="!isEditSearchConfig">Edit</button>
        <button v-if="isEditSearchConfig" class="btn btn-sm btn-success me-md-2" type="button" @click="saveSearchConfig()">Save</button>
        <button v-if="isEditSearchConfig" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditSearchConfig; isEditSearchConfig = false">Cancel</button>
      </div>
    </div>
    <div class="card-body">
      <SearchConfigurationForm
          :isEditing="isEditSearchConfig"
          v-model:responseType="collection.responseType"
          v-model:resultsPerPage="collection.resultsPerPage"
          v-model:resultLimit="collection.resultLimit"
          v-model:includeFields="collection.includeFields"
      />
    </div>
  </div>

</template>

<script>
import CollectionService from "../../../../services/collection.service";
import SearchConfigurationForm from "../../../../components/collections/SearchConfigurationForm";
import EventBus from "../../../../common/EventBus";

export default {
  name: "CollectionSearchConfig",
  props: ['name', 'tabName'],
  components: {
    SearchConfigurationForm
  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: false,
      collection: null,
      isEditSearchConfig: false,
      beforeEditSearchConfig: null,
    }
  },
  created () {
    // watch the params of the route to fetch the data again
    this.$watch(
        () => this.$route.params.name,
        async () => {
          await this.fetchData()
        },
        // fetch the data when the view is created and the data is
        // already being observed
        { immediate: true }
    )
    // return this.v$.$touch;
  },
  methods: {
    async fetchData() {
      this.loading = true
      if (Object.keys(this.$route.params).length !== 0) {
        //console.log(">>>> params", this.$route.params, "query", this.$route.query)
        this.error = this.collection = null
        await this.getCollection()
      }
      this.loading = false
    },
    async getCollection() {
      // console.log("collection name", fetchedId)
      // let url = 'collection/search/getCollectionEntitiesByName'
      // let params = {projection: 'collectionFormData', name: name}

      //console.log("collectionId", JSON.stringify(this.$route.params.id))
      // let url = '/collection/search/getCollectionByName'
      // let params = {name: this.$route.params.name, projection: 'collectionFormData'}

      //console.log("[getCollections] url: " + url + ", prams: " + JSON.stringify(params), null, 2)

      await CollectionService.getCollections('/collection/search/getCollectionByName', {name: this.$route.params.name, projection: 'collectionFormData'})
          .then(response => {
            let data = response.data;
            this.collection = data;
            console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async saveSearchConfig() {
      // let url = '/collection/'+this.collection.id
      let body = {
        responseType: this.collection.responseType,
        resultsPerPage: this.collection.resultsPerPage,
        resultLimit: this.collection.resultLimit,
        includeFields: this.collection.includeFields
      }
      await CollectionService.updateCollection('/collection/'+this.collection.id, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("data", data)
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'Response Config Updated!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      await this.getCollection()
      this.isEditSearchConfig = false
    },
  }
}
</script>

<style scoped>

</style>