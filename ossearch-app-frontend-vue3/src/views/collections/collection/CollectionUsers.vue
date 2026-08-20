<template>
  <div class="row mt-4 mb-4">
    <div class="col-xl-6">
      <div class="card">
        <div class="card-header">
          <i class="fas fa-user me-1"></i>
          <b>Owner</b>
          <div v-if="isAdmin" class="float-end">
            <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditOwner = JSON.parse(JSON.stringify(collection)); isEditOwner = !isEditOwner" v-if="!isEditOwner">Edit</button>
            <button v-if="isEditOwner" class="btn btn-sm btn-success me-md-2" type="button" @click="updateOwner()">Save</button>
            <button v-if="isEditOwner" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditOwner; isEditOwner = false">Cancel</button>
          </div>
        </div>
        <div class="card-body">
          <div v-if="loading" class="d-flex justify-content-center">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          <template v-else>
            <fieldset :disabled="!isEditOwner">
              <Multiselect v-if="!loading" id="OwnerSelect"
                           v-model="collection.owner"
                           :options="users"
                           :multiple="false"
                           trackBy="username"
                           label="username"
                           :custom-label="displayName"
                           :searchable="false"
                           :placeholder="'Select Owner'"
                           :allowEmpty="true"
                           :loading="loading"
              />
            </fieldset>
          </template>
        </div>
      </div>
    </div>
    <div class="col-xl-6">
      <div class="card">
        <div class="card-header">
          <i class="fas fa-users me-1"></i>
          <b>Users</b>
          <div v-if="isAdmin" class="float-end">
            <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditUsers = JSON.parse(JSON.stringify(collection)); isEditUsers = !isEditUsers" v-if="!isEditUsers">Edit</button>
            <button v-if="isEditUsers" class="btn btn-sm btn-success me-md-2" type="button" @click="updateUsers()">Save</button>
            <button v-if="isEditUsers" class="btn btn-sm btn-danger float-end" type="button" @click="collection = beforeEditUsers; isEditUsers = false">Cancel</button>
          </div>
        </div>
        <div class="card-body">
          <div v-if="loading" class="d-flex justify-content-center">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          <template v-else>
            <fieldset :disabled="!isEditUsers">
              <Multiselect v-if="!loading" id="PartOfCollectionsMultiselect"
                           v-model="collection.users"
                           :options="users"
                           :multiple="true"
                           trackBy="username"
                           label="username"
                           :custom-label="displayName"
                           :searchable="false"
                           :placeholder="'Select Users'"
                           :allowEmpty="true"
                           :loading="loading"
              />
            </fieldset>
          </template>
        </div>
      </div>
    </div>
  </div>

</template>

<script>
import EventBus from "../../../common/EventBus";
import UserService from "../../../services/user.service";
import CollectionService from "../../../services/collection.service";
import Multiselect from "vue-multiselect";

export default {
  name: "CollectionUsers",
  components: {
    Multiselect
  },
  data() {
    return {
      loading: false,
      isEditOwner: false,
      isEditUsers: false,
      beforeEditOwner: null,
      beforeEditUsers: null,
      collection: {},
      users: [],
    }
  },
  async mounted() {
    this.loading = true
    await this.getCollection();
    await this.getUsers();
    this.loading = false
  },
  computed: {
    currentUser() {
      return this.$store.state.auth.user;
    },
    isAdmin() {
      if (this.currentUser && this.currentUser['roles']) {
        return this.currentUser['roles'].includes('ROLE_ADMIN') || this.currentUser['roles'].includes('ROLE_MANAGER');
      }
      return false;
    }
  },
  methods: {
    async getCollection() {
      await CollectionService.getCollections('/collection/search/getCollectionByName', {
        name: this.$route.params.name,
        projection: 'collectionUsers'
      })
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
    async getUsers() {
      await UserService.getUsers("/users", {size: 1000, projection: 'userIdNameEmailRoles', sort: 'lastName'})
          .then(res => {
            this.users = res.data._embedded.users
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },
    displayName (user) {
      return user.lastName + ', ' + user.firstName + ' ( '+ user.email +' )'
    },
    async updateOwner() {
      this.loading = true
      await CollectionService.changeOwner("/collection/"+this.collection.id+"/owner", this.collection.owner._links.self.href.replace('{?projection}',''))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("changeOwner data", data)
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'Collection Owner Updated!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
      await this.getCollection()
      this.beforeEditOwner = null;
      this.loading = false
      this.isEditOwner = false
    },
    async updateUsers() {
      let body = []

      this.collection.users.forEach(user => {
        body.push(user._links.self.href.replace('{?projection}',''))
      })

      await CollectionService.updateManagers('/collection/'+this.collection.id+'/users', body.join("\n"))
          .then(response => {
            let data = response.data;
            // this.collection = data;
            console.log("saveManagers data", data)
            EventBus.dispatch('toast', {
              type: 'success',
              msg: 'Collection Users Updated!'
            })
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      await this.getCollection()
      this.beforeEditUsers = null;
      this.loading = false
      this.isEditUsers = false
    }
  },
}
</script>
