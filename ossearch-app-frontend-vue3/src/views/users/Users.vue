<template>

  <div class="container-fluid px-4">
<!--    <h1 class="mt-4">Users</h1>-->
    <Breadcrumb/>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-users me-1"></i>
        <b>Users</b>
        <div class="float-end">
          <router-link type="button" class="btn btn-sm btn-primary bi-plus-lg float-end" to="/users/create">
            User
          </router-link>
        </div>
      </div>

      <div class="card-body">
        <Datatable :loading="loading"
          :tableData="tableData"
          :tableOptions="tableOptions"
                   id="usersTable"
        >
        <template v-slot:table-body>
          <tr v-for="user in tableData" :key="user.id">
            <td>{{user.id}}</td>
            <td>
              <div class="form-check form-switch">
                <input class="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckChecked" :checked="user.enabled" disabled>
              </div>
              <!--          <span v-if="!user.enabled" class="badge rounded-pill bg-success">Active</span>
                        <span v-else class="badge rounded-pill bg-danger">Inactive</span>-->
            </td>
            <td>{{user.firstName}}</td>
            <td>{{user.lastName}}</td>
            <td>
              <router-link :to="{ name: 'userDetails', params: { name: user.username, id: user.id }}">{{ user.username }}</router-link>
<!--              {{user.username}}-->
            </td>
            <td>{{user.email}}</td>
            <td>
              <div v-for="(role, i) in user.roleList" :key="i">
                <span v-if="role === 'ROLE_ADMIN'" class="badge rounded-pill bg-danger bg-opacity-25 text-danger">{{ role }}</span>
                <span v-else-if="role === 'ROLE_MANAGER'" class="badge rounded-pill bg-warning bg-opacity-25 text-warning">{{ role }}</span>
                <span v-else class="badge rounded-pill bg-primary bg-opacity-25 text-primary">{{ role }}</span>
              </div>
            </td>
            <td>{{user.dateCreated}}</td>
            <td class="justify-content-evenly text-center">
              <div class="btn-group btn-group-sm align-items-center">
                <router-link class="btn link-primary p-0 m-1" :to="{ name: 'userDetails', params: { name: user.username, id: user.id }}"><i class="fa fa-edit"></i></router-link>
                <a href="#" class="btn link-danger p-0" data-bs-toggle="modal" data-bs-target="#deleteUserModal" title="Delete" @click="selectUser(user)">
                  <i class="fa fa-times-circle"></i>
                </a>
              </div>
            </td>
          </tr>
        </template>
        </Datatable>
      </div>
    </div>

    <Modal v-if="selectedUser" id="deleteUserModal">
      <template v-slot:header>
        <h5 class="modal-title text-black">Delete User</h5>
      </template>
      <template v-slot:body>
        <p>Are you sure you want to delete user <b>{{ selectedUser.firstName }} {{ selectedUser.lastName }}</b>! This
          can not be undone!</p>
      </template>
      <template v-slot:button-action>
        <button type="button" class="btn btn-danger" data-bs-dismiss="modal"
                @click.prevent="deleteUser(selectedUser._links.self.href)">Delete
        </button>
      </template>
    </Modal>
  </div>

</template>

<script>
// import EventBus from "../common/EventBus";
import UserService from "../../services/user.service";
import Breadcrumb from "../../components/Breadcrumb";
import Datatable from "../../components/table/Datatable";
import Modal from "../../components/Modal";


export default {
  name: "Users",
  components: {
    Breadcrumb,
    Datatable,
    Modal,
  },
  async mounted() {
    this.loading = true
    await this.getUsers();
    this.loading = false
  },
  // watch: {
  //   '$route': 'getUsers'
  // },
  data() {
    return {
      loading: false,
      tableOptions: {
        columns: [
          {label: 'ID', name: 'ID'},
          {label: 'Active', name: 'enabled'},
          {label: 'First Name', name: 'firstName'},
          {label: 'Last Name', name: 'lastName'},
          {label: 'Username', name: 'username'},
          {label: 'Email', name: 'email'},
          {label: 'Roles', name: 'roleList'},
          {label: 'Created', name: 'Created'},
          {label: 'Actions', name: 'Created', class: 'text-center'},
        ],
      },
      tableData: [],
      selectedUser: {},
    }
  },
  methods: {
    async getUsers() {
      await UserService.getUsers("/users", {size: 100, projection: 'userInfo'})
          .then(res => {
            this.tableData = res.data._embedded.users
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },
    deleteUser(url) {
      UserService.deleteUser(url)
          .then(res => {
            console.log("delete res data:", res.data)
            // for (let i = 0; i < this.tableData.length; i++) {
            //   if (this.tableData[i].id == res.data) {
            //     this.tableData.splice(i, 1);
            //   }
            // }
            this.getUsers();
            this.selectedUser = null
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },
    selectUser(user) {
      this.selectedUser = user
    }
  },
}
</script>

<style scoped lang="scss">
.users-wrapper {
  background: #fff;
  //padding: 20px 25px;
  margin: 30px auto;
  border-radius: 3px;
  box-shadow: 0 1px 1px rgba(0, 0, 0, .05);
}

.users-wrapper .btn {
  float: right;
  //color: #333;
  //background-color: #fff;
  border-radius: 3px;
  border: none;
  outline: none !important;
  margin-left: 10px;
}

.users-wrapper .btn:hover {
  color: #333;
  background: #f2f2f2;
}

.users-wrapper .btn.btn-primary {
  color: #fff;
  background: #03A9F4;
}

.users-wrapper .btn.btn-primary:hover {
  background: #03a3e7;
}

.user-title .btn {
  font-size: 13px;
  border: none;
}

.user-title .btn i {
  float: left;
  font-size: 21px;
  margin-right: 5px;
}

.user-title .btn span {
  float: left;
  margin-top: 2px;
}

.user-title {
  color: #fff;
  background: #4b5366;
  padding: 16px 25px;
  margin: -20px -25px 10px;
  border-radius: 3px 3px 0 0;
}

.user-title h2 {
  margin: 5px 0 0;
  font-size: 24px;
}

.pagination {
  float: right;
  margin: 0 0 5px;
}

.pagination li a {
  border: none;
  font-size: 13px;
  min-width: 30px;
  min-height: 30px;
  color: #999;
  margin: 0 2px;
  line-height: 30px;
  border-radius: 2px !important;
  text-align: center;
  padding: 0 6px;
}

.pagination li a:hover {
  color: #666;
}

.pagination li.active a {
  background: #03A9F4;
}

.pagination li.active a:hover {
  background: #0397d6;
}

.pagination li.disabled i {
  color: #ccc;
}

.pagination li i {
  font-size: 16px;
  padding-top: 6px
}

.hint-text {
  float: left;
  margin-top: 10px;
  font-size: 13px;
}
</style>