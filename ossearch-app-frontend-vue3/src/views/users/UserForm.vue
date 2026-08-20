<template>
  <div v-if="loading" class="container-fluid px-4 loading">
    Loading...
  </div>

  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div v-if="user" class="container-fluid px-4">
    <h1 class="mt-4">Create New User</h1>
    <Breadcrumb/>

    <div class="btn-toolbar justify-content-between mb-3" role="toolbar" aria-label="Toolbar with button groups">
      <div class="btn-toolbar float-end" role="toolbar" aria-label="Toolbar with button groups">
        <button type="button" class="btn btn-danger" @click="addUser">Create</button>
      </div>
    </div>


    <div class="card mb-4">

      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Basic Information</b>
      </div>

      <div class="card-body">

        <div class="row g-3 mb-3">
          <div class="col-md-6">
            <div class="form-floating mb-3">
            <input v-model="user.firstName" type="text" class="form-control" id="validationDefault01" required placeholder="firstName">
              <label for="validationDefault01" class="form-label">First name</label>
            </div>
          </div>
          <div class="col-md-6">
            <div class="form-floating mb-3">
            <input v-model="user.lastName" type="text" class="form-control" id="validationDefault02" required placeholder="lastName">
              <label for="validationDefault02" class="form-label">Last name</label>
            </div>
          </div>
        </div>

        <div class="row g-3 mb-3">
          <div class="col-md-6">
            <div class="form-floating mb-3">
              <input v-model="user.username" type="text" class="form-control" id="validationDefaultUsername"
                     aria-describedby="inputGroupPrepend2"
                     required placeholder="username">
              <label for="validationDefaultUsername" class="form-label">Username</label>
            </div>
          </div>
          <div class="col-md-6">
            <div class="form-floating mb-3">
            <input v-model="user.email" type="text" class="form-control" id="validationDefault03" required
                   placeholder="email">
              <label for="validationDefault03" class="form-label">Email</label>
            </div>
          </div>
        </div>

        <div class="row g-3">
          <div class="col-md-6">
            <div id="accountEnabled" class="form-check form-switch">
              <input class="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckCheckedUserEnabled" v-model="user.enabled">
              <label class="form-check-label" for="flexSwitchCheckCheckedUserEnabled">User Account is Enabled</label>
            </div>
          </div>
        </div>

      </div>

    </div>

    <div class="card mb-4">

      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Password Information</b>
      </div>

      <div class="card-body">
        <div class="row g-3">
          <div class="col-md-6">
            <div id="passwordInputForm" class="form-floating">
              <input v-model="user.password" @blur="v$.user.password.$touch()"
                     type="text"
                     class="form-control"
                     :class="(!v$.user.password.$invalid && user.password !== '') ? ' is-valid': user.password === '' ? '' : 'is-invalid'"
                     id="passwordInput" ref="passwordInput"
                     aria-describedby="inputGroupPrepend2"
                     required
                     placeholder="password"
                     autocomplete="off"
              >
              <label for="passwordInput" class="form-label">Password</label>
              <div class="invalid-feedback">
                Your password must be 8-20 characters long, contain letters, numbers, and special characters, and must not contain spaces or emoji.
              </div>
              <div :class="(v$.user.password.$invalid && user.password !== '') ? 'show' : 'opacity-1'"
                   class="popover fade bs-popover-end"
                   role="tooltip" id="passwordValidationPopover" ref="passwordValidationPopover">
                <div class="popover-arrow" data-popper-arrow></div>
                <h3 class="popover-header">Your password must contain:</h3>
                <div class="popover-body">
                  <div class="form-text">
                    <span v-if="v$.user.password.containsUppercase.$invalid"><i class="fas fa-times text-danger"></i></span>
                    <span v-else><i class="fas fa-check text-success"></i></span>
                    at least <strong>one uppercase letter</strong>
                  </div>
                  <div class="form-text">
                    <span v-if="v$.user.password.containsLowercase.$invalid"><i class="fas fa-times text-danger"></i></span>
                    <span v-else><i class="fas fa-check text-success"></i></span>
                    at least <strong>one lowercase letter</strong>
                  </div>
                  <div class="form-text">
                    <span v-if="v$.user.password.containsNumber.$invalid"><i class="fas fa-times text-danger"></i></span>
                    <span v-else><i class="fas fa-check text-success"></i></span>
                    at least <strong>one number</strong>
                  </div>
                  <div class="form-text">
                    <span v-if="v$.user.password.containsSpecial.$invalid"><i class="fas fa-times text-danger"></i></span>
                    <span v-else><i class="fas fa-check text-success"></i></span>
                    at least <strong>one special character</strong>
                  </div>
                  <div class="form-text">
                    <span v-if="v$.user.password.containsSpace.$invalid"><i class="fas fa-times text-danger"></i></span>
                    <span v-else><i class="fas fa-check text-success"></i></span>
                    not contains spaces
                  </div>
                  <div class="form-text">
                    <span v-if="v$.user.password.minLength.$invalid"><i class="fas fa-times text-danger"></i></span>
                    <span v-else><i class="fas fa-check text-success"></i></span>
                    at least 8 characters
                  </div>
                  <div class="form-text">
                    <span v-if="v$.user.password.maxLength.$invalid"><i class="fas fa-times text-danger"></i></span>
                    <span v-else><i class="fas fa-check text-success"></i></span>
                    no more than 20 characters
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="col-md-6">
            <div id="passwordConfirmInput" class="form-floating">
              <input v-model="confirmPassword" type="text" class="form-control" :class="(!v$.confirmPassword.$invalid && confirmPassword !== '') ? ' is-valid': confirmPassword === '' ? '' : 'is-invalid'" id="validationVerifyPassword" ref="validationVerifyPassword"
                     aria-describedby="inputGroupPrepend2"
                     required placeholder="password" autocomplete="off">
              <label for="validationVerifyPassword" class="form-label">Verify Password</label>
              <div class="invalid-feedback">
                Password does not match!
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>

    <div class="card mb-4">

      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Roles and Permissions</b>
      </div>

      <div class="card-body">
        <div class="row g-3">
          <div class="col-md-6">
            <div id="rolesSelect" class="form-floating">
              <!--          <select :value="roles" class="form-select" multiple @input="$emit('update:roles', $event.target.value)"
                                aria-label="select multiple roles" id="validationDefault04">
                          <option v-for="(role, i) in rolesList" :key="i" :value="role"
                                  :selected="roles.some(r => r.name === role.name)">
                            {{ role.name }}
                          </option>
                        </select>-->

              <!-- For Multiselect 2-way binding is not very intuitive this works tho  -->
              <Multiselect id="roleMultiselect"
                           v-model="user.roles"
                           :modelValue="roles"
                           :options="rolesList"
                           :multiple="true"
                           trackBy="name"
                           label="name"
                           :searchable="false"
                           :placeholder="'Select Role(s)'"
                           :allowEmpty="false"
              />
              <!--            <label for="roleMultiselect" class="form-label">Role(s)</label>-->
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-search me-1"></i>
        <b>Collection Access</b>
      </div>
      <div class="card-body">
        <ul class="list-group">
          <li v-for="collection in collections" :key="collection.id" class="list-group-item">
            <input v-model="user.collections" :value="collection" class="form-check-input me-1" type="checkbox"
                   aria-label="...">
            {{ collection.name }}
          </li>
        </ul>
      </div>
    </div>

<!--    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>User JSON Data</b>
      </div>
      <div class="card-body">
        <pre>{{ print(user) }}</pre>
      </div>
    </div>-->

<!--    <div class="accordion" id="accordionExample">
      <div class="accordion-item">
        <h2 class="accordion-header" id="headingOne">
          <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="false" aria-controls="collapseOne">
            User JSON Data
          </button>
        </h2>
        <div id="collapseOne" class="accordion-collapse collapse" aria-labelledby="headingOne" data-bs-parent="#accordionExample">
          <div class="accordion-body">
            <pre>{{ print(user) }}</pre>
          </div>
        </div>
      </div>
    </div>-->
  </div>

</template>

<script>
import UserService from "../../services/user.service";
import CollectionService from "../../services/collection.service";
import RoleService from '../../services/role.service';
import Multiselect from 'vue-multiselect'
import useVuelidate from '@vuelidate/core'
import {required, minLength, maxLength, sameAs, helpers,} from "@vuelidate/validators";
import { createPopper } from '@popperjs/core';
import Breadcrumb from "../../components/Breadcrumb";
// import Tooltip from 'bootstrap'
// import Popover from 'bootstrap'

export default {
  name: "UserForm",
  setup: () => ({ v$: useVuelidate() }),
  components: {
    Breadcrumb,
    Multiselect,
    // Popper
  },
  data() {
    return {
      loading: false,
      error: null,
      collections: null,
      rolesList: [],
      selected: [],
      user: {
        firstName: '',
        lastName: '',
        username:'',
        password: '',
        email:'',
        roles: [],
        roleList: [],
        collections: [],
        enabled: true
      },
      confirmPassword: '',
      submitted: false,
    }
  },
  validations () {
    return {
      user: {
        password: {
          required,
          containsUppercase: helpers.regex(/(.*[A-Z].*)/),
          containsLowercase: helpers.regex(/(.*[a-z].*)/),
          containsNumber: helpers.regex(/(.*[0-9].*)/),
          containsSpecial: helpers.regex(/(.*[!"#$%&'()*+,-./:;<=>?@[\\\]^_`{|}~].*)/),
          containsSpace: helpers.regex(/(.*^(?!.*\s).*)/),
          minLength: minLength(8),
          maxLength: maxLength(20),
          $autoDirty: true
        },
      },
      confirmPassword: {
        required,
        sameAsPassword: sameAs(this.user.password),
        $autoDirty: true
      },
      $autoDirty: true
    }
  },
  // created() {
  //   // this.submitted = true;
  //   return this.v$.$touch;
  // },
  mounted() {
    this.$nextTick(() => {
      const input = this.$refs["passwordInput"]
      const tooltip = this.$refs["passwordValidationPopover"]
      createPopper(input, tooltip, {
        placement: "right",
        // strategy: "absolute",
        // modifiers: [
        //   // applyStyle: {enabled: false},
        //   // {
        //   //   name: 'offset',
        //   //   options: {
        //   //     offset: [-150, 0],
        //   //   },
        //   // }
        // ]
      });


      // new Popover(input, {
      //   placement: "right",
      //   animation: false,
      //   content: tooltip.innerHTML,
      //   html: true
      // })

    })

    RoleService.getRoles().then(
        response => {
          // this.content = response.data._embeded;
          // console.log(JSON.stringify(response.data._embedded.roles, null, 2))
          this.rolesList = response.data._embedded.roles
          this.user.roles.push(this.rolesList.find(role => role.name === 'ROLE_USER'));
          // this.rolesList = response.data._embedded.roles.map(role => ({label: role.name, value: role}))
        },
        error => {
          this.content =
              (error.response && error.response.data && error.response.data.message) ||
              error.message ||
              error.toString();

          //console.log(error)
        }
    )

    CollectionService.getCollections("/collection", {size: 100, projection: 'collectionIdNameInfo'}).then(response => {
      this.collections = response.data._embedded.collection;
      this.collections.sort((a, b) => a.name.localeCompare(b.name));
    })
        .catch(errors => {
          //console.log(errors);
          this.error = errors
        });
  },
  methods: {
    // setPassword($event) {
    //   // do some silly transformation
    //   this.user.password = $event.target.value
    //   this.v$.user.password.$touch()
    // },
    submitForm() {
      this.submitted = true;
      // this.v$.$touch();
      // if (this.v$.$invalid) {
      //   return false; // stop here if form is invalid
      // } else {
      //   alert("Form Valid. Move to next screen");
      // }

      this.v$.$validate() // checks all inputs
      if (!this.v$.$error) { // if ANY fail validation
        alert('Form successfully submitted.')
      } else {
        alert('Form failed validation')
      }
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    async addUser() {
      let userRef = '';

      let body = JSON.parse(JSON.stringify(this.user))
      body.roles.length = 0
      body.collections.length = 0

      // add the user role relationship
      this.user.roles.forEach(role => {
        body.roles.push(role._links.self.href)
      })

      //console.log(JSON.stringify(body, null, 2))

      //add the user
      await UserService.addUser("/users", body)
          .then(response => {
            //   //console.log("add user response: " + response);
            userRef = response.data._links.self.href

            // if (response.status == 201) {
            //   this.$router.push({path: '/users'})
            // }

          })
          .catch(errors => {
            //console.log("add user error: " + errors);
            alert("error creating user " + errors)
          });

      // add the user collection relationship
      // this.user.collections.forEach(collection => {
      //   body.collections.push(collection._links.self.href)
      // })

      if (userRef) {
        // add the user collection relationship
        await this.user.collections.forEach(collection => {
          // let url = collection._links.users.href.replace('{?projection}', '')
          console.log("adding user to collection", collection.name)
          UserService.updateCollections(collection._links.users.href.replace('{?projection}', ''), userRef)
              // .then(response => {
              //   let data = response.data;
              //   console.log("data", data)
              // })
              .catch(errors => {
                console.log(errors);
                this.error = errors
              });
        })
      }

      this.$router.push({path: '/users'})
    },
  }
}
</script>

<style scoped lang="scss">

.list-group {
  max-height: 300px;
  margin-bottom: 10px;
  overflow-y: scroll;
  -webkit-overflow-scrolling: touch;
}

/* Hide the popper when the reference is hidden */
//.popover[data-popper-reference-hidden] {
//  visibility: hidden;
//  pointer-events: none;
//}

.popover {
  // display: none; <---- don't do this
  opacity: 1; // do this

  background-color: white;
  border: 1px solid #c6c6c6;
  box-shadow: 1px 1px 8px 1px rgba(0, 0, 0, 0.2);
  line-height: 1.5;
  padding: 4px 8px;
  border-radius: 4px;
  z-index: -1; // this keeps it from blocking other elements when it's hidden

  &.show {
    visibility: visible;
    pointer-events: auto;
    opacity: 1; // oh yeah
    z-index: 1;
  }
}

</style>
