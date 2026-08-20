<template>
<!--  <div v-if="loading" class="container-fluid loading">
    Loading...
    <h2>Name: {{ $route.params.name }}</h2>
    <h2>Id: {{ $route.params.id }}</h2>
  </div>-->

  <div v-if="error" class="container-fluid error">
    {{ error }}
  </div>

  <div class="container-fluid px-4" id="tutorial">
    <Breadcrumb/>

    <router-view></router-view>

  </div>
</template>

<script>
import Breadcrumb from '../../components/Breadcrumb'

export default {
  name: "Tutorial",
  props: [],
  components: {
    Breadcrumb
  },
  data() {
    return {
      loading: false,
      error: null
    }
  },
  computed: {
    currentUser() {
      return this.$store.state.auth.user;
    },
    isAdmin() {
      if (this.currentUser && this.currentUser['roles']) {
        return this.currentUser['roles'].includes('ROLE_ADMIN');
      }
      return false;
    }
  },
  methods: {

  }
}
</script>

<style scoped>

</style>
