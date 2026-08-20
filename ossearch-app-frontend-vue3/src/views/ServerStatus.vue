<template>
  <div class="container-fluid px-4">
    <div class="container">
      <div class="row">
        <div class="col-md-12">
          <h1>Backend Status</h1>
          <p v-if="node" class="text-muted small">Reported by application server: <b>{{ node }}</b> (requests are load-balanced; another server may answer next time)</p>
        </div>
      </div>
      <div class="row clearfix">
        <div class="col-md-12 column">
  <!--        <div class="panel alert-warning">
            <div class="panel-heading">
              <h3 class="panel-title">
                Not All Systems Operational
                <small class="float-sm-end">Refreshed 39 minutes ago</small>
              </h3>
            </div>
          </div>-->

          <ServiceStatus serviceName="Database"
                         v-bind:serviceStatus="db_status"/>
          <ServiceStatus serviceName="LDAP"
                         v-bind:serviceStatus="ldap"/>
          <ServiceStatus serviceName="Solr Master"
                         v-bind:serviceStatus="solr_master"/>
          <ServiceStatus serviceName="Solr Slave"
                         v-bind:serviceStatus="solr_slave"/>

          <div :class="scheduler_status.started ? 'alert-success' : 'alert-danger'"  class="alert d-flex align-items-center" role="alert">
            <i :class="scheduler_status.started ? 'bi-check-circle-fill' : 'bi-x-circle-fill'"  style="font-size: x-large" class="bi bi-check-circle-fill flex-shrink-0 me-2" aria-label="Success:"></i>
            <div>
              Quartz Scheduler running since {{ scheduler_status.runningSince }}
            </div>
          </div>

        </div>
      </div>
    </div>
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-users me-1"></i>
        <b>Users Logged In</b>
      </div>

      <div class="card-body">
        <Datatable :loading="loading"
                   :tableData="tableData"
                   :tableOptions="tableOptions"
                   id="usersLoggedInTable"
        >
          <template v-slot:table-body>
            <tr v-for="user in tableData" :key="user.id">
              <!--<td>{{user.id}}</td>-->
              <td>
                <router-link :to="{ name: 'userDetails', params: { name: user.username, id: user.id }}">{{ user.username }}</router-link>
              </td>
              <td>{{user.email}}</td>
              <td>{{user.firstName}}</td>
              <td>{{user.lastName}}</td>
              <td>
                <div v-for="(role, i) in user.roleList" :key="i">
                  <span v-if="role === 'ROLE_ADMIN'" class="badge rounded-pill bg-danger bg-opacity-25 text-danger">{{ role }}</span>
                  <span v-else-if="role === 'ROLE_MANAGER'" class="badge rounded-pill bg-warning bg-opacity-25 text-warning">{{ role }}</span>
                  <span v-else class="badge rounded-pill bg-primary bg-opacity-25 text-primary">{{ role }}</span>
                </div>
              </td>
              <td>{{getLocalDateTime(user.expiryDate)}}</td>
            </tr>
          </template>
        </Datatable>
      </div>
    </div>
  </div>
</template>

<script>
import ServerStatusService from "../services/server-status.service"
import ServiceStatus from "../components/ServiceStatus";
import Datatable from "../components/table/Datatable.vue";
import UserService from "../services/user.service";

export default {
  name: "ServerStatus",
  data() {
    return {
      json: '',
      node: '',
      backend_status: '',
      db_status: '',
      schedulerStatus: '',
      ldap: '',
      solr_master: '',
      solr_slave: '',
      scheduler_status: '',
      loading: false,
      tableOptions: {
        columns: [
          // {label: 'ID', name: 'ID'},
          {label: 'Username', name: 'username'},
          {label: 'Email', name: 'email'},
          {label: 'First Name', name: 'firstName'},
          {label: 'Last Name', name: 'lastName'},
          {label: 'Roles', name: 'roleList'},
          {label: 'Token Expire Date', name: 'expiryDate'},
        ],
      },
      tableData: [],
    }
  },
  components: {
    Datatable,
    ServiceStatus
  },
  async mounted() {
    this.loading = true
    this.getServerStatus()
    this.getSchedulerStatus()
    await this.getUsers();
    this.loading = false
  },
  watch: {
    error: {
      deep: true,
      handler: function () {
        // A 403 here must not log the user out: session expiry is handled
        // globally by the axios interceptor (401 -> refresh -> logout), and
        // the WAF can 403 individual requests for a still-valid session.
        let content = (this.error.response && this.error.response.data && this.error.response.data.message) || this.error.message || this.error.toString();
        alert("ERROR: " + content)
      }
    }
  },
  methods: {
    async getServerStatus() {
      await ServerStatusService.getServerStatus().then(
          response => {
            this.json = response.data
            this.node = this.json.node
            this.db_status = this.json.components?.db?.status
            this.ldap = this.json.components?.ldap?.status
            this.solr_master = this.json.components?.solr?.details['master.status']
            this.solr_slave = this.json.components?.solr?.details['slave.status']
            console.log(this.json)
          }
      )
    },
    async getSchedulerStatus() {
      await ServerStatusService.getSchedulerStatus().then(
          response => {
            this.scheduler_status = response.data
            console.log(this.scheduler_status)

          }
      )
    },
    async getUsers() {
      await UserService.getUsers("/auth/loggedinusers", null)
          .then(res => {
            console.log(JSON.stringify(res.data,null,2));
            this.tableData = res.data
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },
    getLocalDateTime(utc) {
      let u = utc
      return new Date(u).toLocaleString()
    },
  }
}
</script>

<style scoped>

</style>