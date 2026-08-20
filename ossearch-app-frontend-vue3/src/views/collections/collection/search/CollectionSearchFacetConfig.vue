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
      <b>Search Dynamic Navigation Configuration</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditDynamicNav = JSON.parse(JSON.stringify(collection)); isEditDynamicNav = !isEditDynamicNav" v-if="!isEditDynamicNav">Edit</button>
        <button v-if="isEditDynamicNav" class="btn btn-sm btn-success me-md-2" type="button" @click="saveDynamicNav()">Save</button>
        <button v-if="isEditDynamicNav" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditDynamicNav; isEditDynamicNav = false">Cancel</button>
      </div>
    </div>

    <div class="card-body">
      <DynamicNavigation
          :isEditing="isEditDynamicNav"
          :collectionId="collection.id"
          v-model:useFacets="collection.useFacets"
          v-model:dynamicNavigations="collection.dynamicNavigations"
      />
    </div>
  </div>

</template>

<script>
import CollectionService from "../../../../services/collection.service";
import DynamicNavigation from "../../../../components/collections/DynamicNavigation";
import EventBus from "../../../../common/EventBus";

export default {
  name: "CollectionSearchFacetConfig",
  props: ['name', 'tabName'],
  components: {
    DynamicNavigation
  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: false,
      collection: null,
      isEditDynamicNav: false,
      beforeEditDynamicNav: null,
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
    async saveDynamicNav() {

      let origDynamicNavigations = []

      await CollectionService.getCollections('/collection/'+this.collection.id+"/dynamicNavigations")
          .then(response => {
            let data = response.data;
            data._embedded.dynamicNavigation.forEach(dynamicNavigation => {
              origDynamicNavigations.push(dynamicNavigation._links.self.href)
            });
            //console.log("origDynamicNavigations", origDynamicNavigations, "currentDynamicNavigations", this.collection.dynamicNavigations)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      let dynamicNavigations = await this.updateDynamicNav()

      //console.log("(2) updated dynamicNavigations", dynamicNavigations)

      let deleteDynamicNavigations = origDynamicNavigations.filter((dynamicNavigation) => !dynamicNavigations.includes(dynamicNavigation))

      //console.log(">>> for delete dynamicNavigations", deleteDynamicNavigations)

      await this.deleteDynamicNav(deleteDynamicNavigations)

      // update useFacet
      //let url = '/collection/'+this.collection.id
      let body = {
        useFacets: this.collection.useFacets
      }
      await CollectionService.updateCollection('/collection/'+this.collection.id, JSON.stringify(body))
          .then(response => {
            let data = response.data;
            this.collection = data;
            //console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      EventBus.dispatch('toast', {
        type: 'success',
        msg: 'Dynamic Navigation Config Updated!'
      })
      await this.getCollection()
      this.isEditDynamicNav = false
    },
    async updateDynamicNav() {
      let dynamicNavigations = []
      let promises = []

      this.collection.dynamicNavigations.forEach(dynamicNavigation => {
        //console.log("dynamicNavigation:", JSON.stringify(dynamicNavigation))
        //let url = "/dynamic-navigation"
        let body = dynamicNavigation

        if (dynamicNavigation._links && dynamicNavigation.id) {
          //url = dynamicNavigation._links.self.href
          promises.push(CollectionService.updateCollection(dynamicNavigation._links.self.href, JSON.stringify(body)))
        } else {
          body.collection = this.collection._links.self.href
          promises.push(CollectionService.addCollection('/dynamic-navigation', JSON.stringify(body)))
        }
      })

      await Promise.all(promises)
          .then(results => {
            results.forEach(response => {
              let data = response.data;
              let dynamicNavigation_link = data._links.self.href
              //console.log("dynamicNavigation_link", dynamicNavigation_link)
              dynamicNavigations.push(dynamicNavigation_link)
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
      //console.log("(1) return dynamicNavigations", dynamicNavigations)
      return dynamicNavigations;
    },
    async deleteDynamicNav(deleteDynamicNavigations) {
      let promises = []

      deleteDynamicNavigations.forEach(dynamicNavigation => {
        //console.log(">>> DELETE dynamicNavigation:", JSON.stringify(dynamicNavigation))
        promises.push(CollectionService.deleteCollection(dynamicNavigation))
      })

      await Promise.all(promises)
          .then(results => {
            results.forEach(response => {
              let data = response.data;
              console.log("delete resp", data)
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },
  }
}
</script>

<style scoped>

</style>