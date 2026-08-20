<template>
  <h2>ChangeHistory</h2>

  <div class="btn-toolbar justify-content-between mb-3 mt-3" role="toolbar" aria-label="Toolbar with button groups">
    <div class="btn-toolbar" role="toolbar" aria-label="Toolbar with button groups">
      <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#auditLogsModal">Audit Logs</button>
      <!--        <router-link class="btn btn-primary" type="button" role="toolbar" aria-label="Toolbar with button groups" :to="{name: 'crawlLogs', params: { jobName: this.jobData.jobName, jobGroup: this.jobData.jobGroup }}">Crawl Logs</router-link>-->
    </div>
  </div>

  <!-- Audit Logs Modal -->
  <Modal id="auditLogsModal">
    <template v-slot:header>
      <h5 class="modal-title text-black">Audit Log</h5>
    </template>
    <template v-slot:body>
      <!--        <p>Are you sure you want to delete collection <b>{{ collection.name }}</b>!</p>
              <p class="text-danger"><b>This can not be undone!</b></p>-->
      <h5>Audit Logs Coming Soon</h5>
    </template>
    <template v-slot:button-action>
      <button type="button" class="btn btn-primary" data-bs-dismiss="modal"
              @click.prevent="auditLogs()">Continue
      </button>
    </template>
  </Modal>

</template>

<script>
import Modal from "../../../components/Modal";
export default {
  name: "ChangeHistory",
  props: ['name', 'tabName'],
  components: {
    Modal
  },
}
</script>

<style scoped>

</style>