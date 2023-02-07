<template>
<!--  <div v-if="loading" class="container-fluid px-4 loading">
    <div class="d-flex justify-content-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>
  </div>-->

  <div v-if="error" class="container-fluid px-4 error">
    {{ error }}
  </div>

  <div class="container-fluid px-4">
<!--    <h1 class="mt-4">{{ jobData.jobName }}</h1>-->
    <h1 class="mt-4">Schedule New Collection Crawl</h1>
    <Breadcrumb/>

<!--    <div class="btn-toolbar justify-content-between mb-3" role="toolbar" aria-label="Toolbar with button groups">
      <div class="btn-toolbar float-end" role="toolbar" aria-label="Toolbar with button groups">
        <button type="button" class="btn btn-danger" data-bs-dismiss="modal" @click="createCrawlSchedule()">Create</button>
      </div>
    </div>-->

    <div class="d-grid gap-2 d-md-flex justify-content-md-end mb-3">
      <button type="button" class="btn btn-success" data-bs-dismiss="modal" @click="createCrawlSchedule()">Create</button>
      <router-link class="btn btn-danger" to="/scheduler" replace>Cancel</router-link>
    </div>

    <!-- Crawl Scheduler Details -->
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>Crawl Scheduler Details</b>
        <!--            <div class="float-end">
                      <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEdit = {...jobData}; isEditBasicInfo = !isEditBasicInfo" v-if="!isEditBasicInfo">Edit</button>
                      <button v-if="isEditBasicInfo" class="btn btn-sm btn-success me-md-2" type="button" @click="saveCrawlJobSchedule()">Save</button>
                      <button v-if="isEditBasicInfo" class="btn btn-sm btn-danger float-end" type="button" @click="jobData = beforeEdit; isEditBasicInfo = false">Cancel</button>
                    </div>-->
      </div>
      <div class="card-body">
        <div v-if="loading" class="d-flex justify-content-center">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <template v-else>
          <div class="row g-3 mb-3">
            <div class="col-md-6">
              <div class="form-floating">
                <input v-model="jobData.jobName" type="text" class="form-control" id="validationDefault01" required
                       placeholder="job name" :disabled="disableChangeCollection">
                <label for="validationDefault01" class="form-label">Job Name</label>
              </div>
            </div>
            <div class="form-floating col-md-6">
              <!--            <input class="form-control" list="datalistOptions" id="exampleDataList" :value="jobData.collectionName" @input="selectCollection($event.target.value)" placeholder="Type to search...">
                          <label for="exampleDataList" class="form-label">Collection</label>
                          <datalist id="datalistOptions">
                            <option v-for="(collection, i) in collections" :key="i" :value="collection.name">{{collection.name}}</option>
                          </datalist>-->
              <div class="form-floating">
                <select class="form-select" id="floatingCollectionSelect" aria-label="select collection"
                        :value="jobData.collectionName" @change="selectCollection($event.target.value)"
                        :disabled="disableChangeCollection">
                  <option v-for="(collection, i) in collections" :key="i" :value="collection.name">
                    {{ collection.name }}
                  </option>
                </select>
                <label for="floatingCollectionSelect">Collection</label>
              </div>
            </div>
          </div>
          <div class="row g-3">
            <div class="col-md-6">
              <div class="form-floating">
                <input v-model="jobData.description" type="text" class="form-control" id="validationDefault02" required
                       placeholder="description">
                <label for="validationDefault01" class="form-label">Description</label>
              </div>
            </div>
            <div class="col-md-6">
              <div class="form-floating">
                <input v-model="jobData.numberOfRounds" type="text" class="form-control" id="validationDefault03"
                       required placeholder="50">
                <label for="validationDefault02" class="form-label">Number of Crawl Rounds</label>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Crawl Schedule -->
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-clock me-1"></i>
        <b>Crawl Schedule</b>
<!--        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEdit = {...jobData}; isEditCrawlSchedule = !isEditCrawlSchedule" v-if="!isEditCrawlSchedule">Edit</button>
          <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-success me-md-2" type="button" @click="updateCrawlConfig()">Save</button>
          <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-danger float-end" type="button" @click="jobData = beforeEdit; isEditCrawlSchedule = false">Cancel</button>
        </div>-->
      </div>
      <div class="card-body">
        <div v-if="loading" class="d-flex justify-content-center">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <template v-else>
          <CollectionCrawlScheduleForm
              v-if="jobData?.jobName === jobData?.collectionName"
              :isEditing="jobData?.jobName === jobData?.collectionName"
              :collectionName="jobData.jobName"
              v-model:crawlScheduleCron="crawlConfig.crawlCronSchedule"
              :activeTab="getActiveTab()"
              :editorData="getEditorData()"
              @updateCronEditorData="updateCronEditorData"
          />
          <div v-else>
            <h4>Custom Crawl Job Starts Immediately</h4>
          </div>
        </template>
      </div>
    </div>

    <!-- Basic Crawl Options -->
    <div class="card mb-4" v-if="isAdmin">
      <div class="card-header">
        <i class="fas fa-cog me-1"></i>
        <b>Basic Crawl Options</b>
        <!--        <div class="float-end">
                  <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEdit = {...jobData}; isEditCrawlSchedule = !isEditCrawlSchedule" v-if="!isEditCrawlSchedule">Edit</button>
                  <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-success me-md-2" type="button" @click="updateCrawlConfig()">Save</button>
                  <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-danger float-end" type="button" @click="jobData = beforeEdit; isEditCrawlSchedule = false">Cancel</button>
                </div>-->
      </div>
      <div class="card-body">
        <div v-if="loading" class="d-flex justify-content-center">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <!-- Crawl Options -->
        <div v-else class="row">
          <JSONFormGenerator
              :formData="jobData.crawlOptions"
              @formData="updateCrawlOptions"
              :formOptions="crawlOptions"
          />
        </div>
      </div>
    </div>

    <!-- Advanced Crawling -->
    <div class="card mb-4" v-if="isAdmin">
      <div class="card-header">
        <i class="fas fa-cogs me-1"></i>
        <b>Advanced Crawling</b> <b class="text-danger">(Advanced)</b>
<!--        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEdit = {...jobData}; isEditCrawlSchedule = !isEditCrawlSchedule" v-if="!isEditCrawlSchedule">Edit</button>
          <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-success me-md-2" type="button" @click="updateCrawlConfig()">Save</button>
          <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-danger float-end" type="button" @click="jobData = beforeEdit; isEditCrawlSchedule = false">Cancel</button>
        </div>-->
        <div class="form-check form-switch float-end">
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_advancedCrawling'" v-model="advancedCrawling">
        </div>
      </div>
      <div class="card-body" v-if="advancedCrawling">
        <div v-if="loading" class="d-flex justify-content-center">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <template v-else>
          <div class="row mb-3">

            <div class="col col-6 text-center">
              <figure class="figure">
                <a href="../../assets/images/nutch_crawler_workflow.jpg" data-bs-toggle="modal" data-bs-target="#nutchCrawlWorkflowImage">
                  <img class="figure-img img-fluid img-thumbnail" src="../../assets/images/nutch_crawler_workflow.jpg" style="width: 400px; height: 264px;"></a>
                <figcaption class="figure-caption">Click to Enlarge</figcaption>
              </figure>
              <div class="modal fade" id="nutchCrawlWorkflowImage" tabindex="-1" aria-labelledby="nutchCrawlWorkflowImage" aria-hidden="true">
                <div class="modal-dialog modal-lg modal-dialog-centered">
                  <div class="modal-content">
                    <div class="modal-body">
                      <img class="img-fluid rounded mx-auto d-block" src="../../assets/images/nutch_crawler_workflow.jpg">
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="col col-6 text-center">
              <figure class="figure">
                <a href="../../assets/images/nutch_storage_and_data_flow.jpg" data-bs-toggle="modal" data-bs-target="#nutchCrawlStorageAndDataFlowImage">
                  <img class="figure-img img-fluid img-thumbnail" src="../../assets/images/nutch_storage_and_data_flow.jpg" style="width: 400px; height: 264px;"></a>
                <figcaption class="figure-caption">Click to Enlarge</figcaption>
              </figure>
              <div class="modal fade" id="nutchCrawlStorageAndDataFlowImage" tabindex="-1" aria-labelledby="nutchCrawlStorageAndDataFlowImage" aria-hidden="true">
                <div class="modal-dialog modal-lg modal-dialog-centered">
                  <div class="modal-content">
                    <div class="modal-body">
                      <img class="img-fluid rounded mx-auto d-block" src="../../assets/images/nutch_storage_and_data_flow.jpg">
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <template v-for="(step, i) in nutchSteps" :key="i">

            <div class="row mb-3">
              <legend class="col col-sm-auto col-form-label text-capitalize pt-0">{{ i }} Step:</legend>
              <!--                <div class="col col-sm-2">
                                <div class="form-check form-switch">
                                  <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_'+i"
                                         :value="jobData.nutchSteps[i]" :checked="jobData.nutchSteps.includes(i)" @change="updateStep(i, $event.target.checked)">
                                  <label class="form-check-label text-capitalize" :for="'flexSwitchCheck_'+i">{{ i }}</label>
                                </div>
                              </div>-->
              <div class="col col-sm-10">
                <div class="row mb-3">
                  <div class="form-text" style="white-space: pre-line;" v-html="step.desc"></div>
                </div>
                <div class="row">
                  <legend class="col col-sm-auto col-form-label text-end pt-0">args:</legend>
                  <div class="col col-sm-10">
                    <!-- Loop Nutch Step Args -->
                    <JSONFormGenerator
                        :formData="jobData.nutchStepArgs[i]"
                        @formData="updateNutchStepArg(i, $event)"
                        :formOptions="step.args"
                    />
                  </div>
                </div>
              </div>
            </div>

          </template>
        </template>
      </div>
    </div>

    <!-- Nutch Crawler Properties -->
    <div class="card mb-4" v-if="isAdmin">
      <div class="card-header">
        <i class="fas fa-tools me-1"></i>
        <b>Nutch Crawler Properties</b> <b class="text-danger">(Advanced)</b>
<!--        <div class="float-end">
          <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEdit = {...jobData}; isEditCrawlSchedule = !isEditCrawlSchedule" v-if="!isEditCrawlSchedule">Edit</button>
          <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-success me-md-2" type="button" @click="updateCrawlConfig()">Save</button>
          <button v-if="isEditCrawlSchedule" class="btn btn-sm btn-danger float-end" type="button" @click="jobData = beforeEdit; isEditCrawlSchedule = false">Cancel</button>
        </div>-->
        <div class="form-check form-switch float-end">
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_advancedCrawling'" v-model="advancedProperties">
        </div>
      </div>
      <div class="card-body" v-show="advancedProperties">
        <Datatable :loading="loading"
            :tableData="nutchProperties"
            :tableOptions="tableOptions.nutchProperties"
                   id="nutchPropsTable"
        >
          <template v-slot:table-body>
            <tr v-for="(item, i) in nutchProperties" :key="i">
              <template v-for="column in tableOptions.nutchProperties.columns" :key="column">
                <td v-if="['name', 'value', 'substituted.value'].includes(column.name)" class="text-break" style="width: 25%">
                  {{ item?.[column.name] }}
                </td>
                <td v-if="column.name === 'description'">
                  <a href="#" class="link-primary" data-bs-toggle="modal"
                     data-bs-target="#crawlPropDetails" @click="selectedProp = item">more info
                  </a>
                </td>
                <!--                <td v-else-if="column.name === 'description'" data-container="body" data-bs-toggle="tooltip" data-bs-placement="top" :title="item.description.replace(/\s{2,}/g, '\n')" class="text-wrap" style="width: 33%">
                                  {{ item.name }}
                                </td>-->
                <td v-else-if="column.name === 'setValue'" style="width: 25%">
                  <input v-model="jobData.nutchProperties[item.name]" type="text" class="form-control" :id="item.name">
                </td>
              </template>
            </tr>
          </template>
        </Datatable>
      </div>
    </div>

    <!-- Parser HTML Blacklist -->
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-code me-1"></i>
        <b>Parser HTML Blacklist</b>
        <div class="form-check form-switch float-end">
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_parseHtmlBlacklist'"
                 v-model="parseHtmlBlacklist">
        </div>
      </div>
      <div class="card-body" v-if="parseHtmlBlacklist">
        <div class="row g-3 mb-3">
          <div class="form-floating">
            <input v-model="jobData.nutchProperties['parser.html.blacklist']" type="text" class="form-control" id="parserhtmlblacklist" required placeholder="description">
            <label for="parserhtmlblacklist" class="form-label">{{ getProperty('parser.html.blacklist') }}</label>
          </div>
        </div>
        <div class="row g-3 mb-3">
            <div class="text mb-3" style="white-space: pre-wrap;">
              A comma-delimited list of css like tags to identify the elements which should NOT be parsed. Use this to tell the HTML parser to ignore the given elements, e.g. site navigation. It is allowed to only specify the element type (required), and optional its class name ('.') or ID ('#'). More complex expressions will not be parsed.
            </div>

            <div class="text mb-3" style="white-space: pre-wrap;">
              Valid examples: div.header,span,p#test,div#main,ul,div.footercol
            </div>
            <div class="text mb-3" style="white-space: pre-wrap;">
              Invalid expressions: div#head#part1,#footer,.inner#post Note that the elements and their children will be silently ignored by the parser, so verify the indexed content with Luke to confirm results. Use either 'parser.html.blacklist' or 'parser.html.whitelist', but not both of them at once. If so,only the whitelist is used.
            </div>
        </div>

      </div>
    </div>

    <!-- Parser HTML Whitelist -->
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-code me-1"></i>
        <b>Parser HTML Whitelist</b>
        <div class="form-check form-switch float-end">
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_parseHtmlWhitelist'"
                 v-model="parseHtmlWhitelist">
        </div>
      </div>
      <div class="card-body" v-if="parseHtmlWhitelist">
        <div class="row g-3 mb-3">
          <div class="form-floating">
            <input v-model="jobData.nutchProperties['parser.html.whitelist']" type="text" class="form-control" id="parserhtmlwhitelist" required placeholder="description">
            <label for="parserhtmlwhitelist" class="form-label">{{ getProperty('parser.html.whitelist') }}</label>
          </div>
        </div>
        <div class="row g-3 mb-3">
          <div class="text mb-3" style="white-space: pre-wrap;">A comma-delimited list of css like tags to identify the elements which should be parsed. Use this to tell the HTML parser to only use the given elements, e.g. content. It is allowed to only specify the element type (required), and optional its class name ('.') or ID ('#'). More complex expressions will not be parsed.</div>

          <div class="text mb-3" style="white-space: pre-wrap;">Valid examples: div.header,span,p#test</div>
          <div class="text mb-3" style="white-space: pre-wrap;">
            Invalid expressions: div#head#part1,#footer,.inner#post Note that the elements and their children will be silently ignored by the parser, so verify the indexed content with Luke to confirm results. Use either 'parser.html.blacklist' or 'parser.html.whitelist', but not both of them at once. If so, only the whitelist is used.
          </div>
        </div>
      </div>
    </div>

    <!-- Parser HTML Text Blacklist -->
    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-code me-1"></i>
        <b>Parser HTML Text Blacklist</b>
        <div class="form-check form-switch float-end">
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_parseHtmlTextBlacklist'" v-model="parseHtmlTextBlacklist">
        </div>
      </div>
      <div class="card-body" v-if="parseHtmlTextBlacklist">
        <div class="row g-3 mb-3">
          <div class="form-floating">
            <input v-model="jobData.nutchProperties['parser.html.text.blacklist']" type="text" class="form-control" id="parserhtmltextblacklist" required placeholder="description">
            <label for="parserhtmltextblacklist" class="form-label">{{ getProperty('parser.html.text.blacklist') }}</label>
          </div>
        </div>
        <div class="row g-3 mb-3">
          <div class="text mb-3" style="white-space: pre-wrap;">A piped-delimited list type,id,class prefix and comma-delimated list of values in parenthesis to identify the elements which should NOT be parsed. Use this to tell the HTML parser to ignore the given elements, e.g. site navigation.</div>

          <div class="text mb-3" style="white-space: pre-wrap;">Valid examples: type(div,span,p,ul)|id(test,main)|class(header,footercol) Note that the elements and their children will be silently ignored by the parser, so verify the indexed content with Luke to confirm results.</div>
          <div class="text mb-3" style="white-space: pre-wrap;">
            Use either 'parser.html.blacklist' or 'parser.html.whitelist', but not both of them at once. If so,
            only the whitelist is used.
          </div>
        </div>
      </div>
    </div>

    <div class="d-grid gap-2 d-md-flex justify-content-md-end mb-3">
      <button type="button" class="btn btn-success" data-bs-dismiss="modal" @click="createCrawlSchedule()">Create</button>
      <router-link class="btn btn-danger" to="/scheduler" replace>Cancel</router-link>
    </div>

    <!-- JSON Review -->
<!--    <div class="card mb-4">
      <div class="card-header">
        <i class="fas fa-info-circle me-1"></i>
        <b>JSON Review</b>
        <div class="form-check form-switch float-end">
          <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_crawlScheduleFormJson'" v-model="showJson">
        </div>
      </div>
      <div class="card-body" v-if="showJson">
        <pre>{{ print(jobData) }}</pre>
      </div>
    </div>-->

    <div class="modal fade" id="crawlPropDetails" tabindex="-1" aria-labelledby="crawlPropDetails" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ selectedProp?.name }}</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <p class="text-wrap">{{ selectedProp?.description.replace(/\s{2,}/g, '\n') }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>

  </div>


</template>

<script>
import SchedulerService from "../../services/scheduler.service";
import CollectionCrawlScheduleForm from "../../components/collections/CollectionCrawlScheduleForm";
import CollectionService from "../../services/collection.service";
import Breadcrumb from "../../components/Breadcrumb";
import JSONFormGenerator from "../../components/forms/JSONFormGenerator";
import Datatable from "../../components/table/Datatable";
import EventBus from "../../common/EventBus";

export default {
  name: "CrawlScheduleForm",
  // props: {collectionId: {type: String, default: null}, collectionName: {type: String, default: null}},
  components: {
    Breadcrumb,
    CollectionCrawlScheduleForm,
    JSONFormGenerator,
    Datatable
  },
  data() {
    return {
      loading: false,
      error: null,
      showJson: false,
      selectedProp: null,
      jobData: {
        jobName: this.collectionName,
        collectionId: this.collectionId,
        collectionName: null,
        numberOfRounds: 50,
        crawlOptions: {
          index: true,
          sitemaps_from_hostdb: 'once',
        },
        nutchStepArgs: {
          index: {
            deleteGone: true
          }
        },
        nutchProperties: {},
      },
      crawlConfig: {
        crawlCronSchedule: '0 0 0 ? * FRI *',
        cronEditorData: "{\"name\":\"Weekly\",\"editorData\":{\"dateTime\":\"00:00\",\"daysOfWeek\":[6]}}"
      },
      tableOptions: {
        nutchProperties: {
          // enableImport: true,
          // enableAddRow: true,
          // enableActions: true,
          columns: [
            {label: 'Key', name: 'name'},
            {label: 'Description', name: 'description'},
            {label: 'Default Value', name: 'value'},
            {label: 'Substituted Value', name: 'substituted.value'},
            {label: 'Set Value', name: 'setValue'}
          ]
        }
      },
      crawlOptions: [
        {name: "index", type: "boolean", desc: "Indexes crawl results into a configured indexer"},
        {name: "wait", type: "text", desc: "&lt;NUMBER[SUFFIX]&gt; Time to wait before generating a new segment when no URLs are scheduled for fetching.\nSuffix can be: s for second, m for minute, h for hour and d for day.\nIf no suffix isspecified second is used by default. [default: -1]"},
        // {name: "s", type: "text", desc: "&lt;seed_dir&gt; Path to seeds file(s)"},
        // {name: "sm", type: "text", desc: "&lt;sitemap_dir&gt; Path to sitemap URL file(s)"},
        {name: "hostdbupdate", type: "checkbox", desc: "Boolean flag showing if we either update or not update hostdb for each round"},
        {name: "hostdbgenerate", type: "checkbox", desc: "Boolean flag showing if we use hostdb in generate or not"},
        // {name: "num_fetchers", type: "number", desc: "&lt;num_fetchers&gt; Number of tasks used for fetching (fetcher map tasks) [default: 1]\nNote: This can only be set when running in distributed mode and \nshould correspond to the number of worker nodes in the cluster. "},
        {name: "num_tasks", type: "number", desc: "&lt;num_tasks&gt; Number of reducer tasks [default: 2]"},
        {name: "size_fetchlist", type: "number", desc: "&lt;size_fetchlist&gt; Number of URLs to fetch in one iteration [default: 50000]"},
        {name: "time_limit_fetch", type: "number", desc: "&lt;time_limit_fetch&gt; Number of minutes allocated to the fetching [default: 180]"},
        {name: "num_threads", type: "number", desc: "&lt;num_threads&gt; Number of threads for fetching / sitemap processing [default: 50]"},
        {name: "sitemaps_from_hostdb", type: "select", desc: "&lt;frequency&gt; Whether and how often to process sitemaps based on HostDB.\nSupported values are:\n- never\n- always (processing takes place in every iteration)\n- once [default] (processing only takes place in the first iteration)\n", options: [{label: "never", value: "never"}, {label: "always", value: "always"}, {label: "once", value: "once"}], default: "never"},
        {name: "dedup_group", type: "select", desc: "&lgt;none|host|domain&gt; Deduplication group method [default: none]", options: [{label: "none", value: "none"}, {label: "host", value: "host"}, {label: "domain", value: "domain"}], default: "none"}
      ],
      nutchSteps: {
        inject: {
          desc: "Injector takes a flat text file of URLs (or a folder containing text files) and merges (\"injects\") these URLs into the CrawlDb. Useful for bootstrapping a Nutch crawl. The URL files contain one URL per line",
          args: [
            {name: "overwrite", type: "checkbox", desc: "Overwite existing crawldb records by the injected records. Has precedence over 'update'", default: false},
            {name: "update", type: "checkbox", desc: "Update existing crawldb records with the injected records. Old metadata is preserved", default: false},
            {name: "nonormalize", type: "checkbox", desc: "Do not normalize URLs before injecting", default: false},
            {name: "nofilter", type: "checkbox", desc: "Do not apply URL filters to injected URLs", default: false},
            {name: "filterNormalizeAll", type: "checkbox", desc: "Normalize and filter all URLs including the URLs of existing CrawlDb records", default: false}
          ]
        },
        sitemap: {
          desc: "Performs Sitemap processing by fetching sitemap links, parsing the content and merging the urls from Sitemap (with the metadata) with the existing crawldb.\nThere are two use cases supported in Nutch's Sitemap processing:\n1. Sitemaps are considered as \"remote seed lists\". Crawl administrators can prepare a list of sitemap links and get only those sitemap pages. This suits well for targeted crawl of specific hosts.\n2. For open web crawl, it is not possible to track each host and get the sitemap links manually. Nutch would automatically get the sitemaps for all the hosts seen in the crawls and inject the urls from sitemap to the crawldb.\nFor more details see: https://cwiki.apache.org/confluence/display/NUTCH/SitemapFeature",
          args: [
            // {name: "sitemapUrls",desc:"path to directory with sitemap urls or hostnames"},
            {name: "threads", desc:"Number of threads created per mapper to fetch sitemap urls (default: 8)", default: 8},
            // {name: "force", type: "checkbox", desc:"force update even if CrawlDb appears to be locked <b class='text-danger'>(CAUTION advised)</b>"},
            {name: "noStrict", type: "checkbox", desc:"By default Sitemap parser rejects invalid urls. '-noStrict' disables that.", default: false},
            {name: "noFilter", type: "checkbox", desc:"turn off URLFilters on urls (optional)", default: false},
            {name: "noNormalize", type: "checkbox", desc:"turn off URLNormalizer on urls (optional)", default: false},
          ]
        },
        generate: {
          desc: "Generate new segments to fetch from crawldb.\nGenerates a subset of a crawl db to fetch. This allows us to generate fetchlists for several segments in one go. The IP resolution is done ONLY on the entries which have been selected for fetching. The URLs are partitioned by IP, domain or host within a segment. We can chose separately how to count the URLS i.e. by domain or host to limit the entries.",
          args: [
            // {name: "force", type: "checkbox", desc: "This argument will force an update even if there appears to be a lock. <b class='text-danger'>(CAUTION: advised)</b>", default: false},
            {name: "topN", desc: "Where N is the number of top URLs to be selected. Normally, the \"generate\" command prepares a fetchlist out of all unfetched pages, or the ones where fetch interval already expired. But if you use -topN, then instead of all unfetched urls you only get N urls with the highest score - potentially the most interesting ones, which should be prioritized in fetching."},
            // {name: "numFetchers", desc: "The number of fetch partitions. Default: Configuration key -> mapred.map.tasks -> 1 (in local mode), possibly multiple in deploy/distributed mode."},
            {name: "expr", desc: ""},
            {name: "adddays", desc: "Adds 'days' to the current time to facilitate crawling urls already fetched sooner then db.default.fetch.interval. Default: 0"},
            {name: "noFilter", type: "checkbox", desc: "Whether to filter URLs or not is read from the crawl.generate.filter property in nutch-site.xml/nutch-default.xml configuration files. If the property is not found, the URLs are filtered. Same for the normalisation", default: false},
            {name: "noNorm", type: "checkbox", desc: "The exact same applies for normalisation parameter as does for the filtering option above", default: false},
            {name: "maxNumSegments", desc: "The (maximum) number of segments to be generated. Default: 1 -- Note: if multiple segments are generated, the limit -topN applies to the total number of URLs for all segments taken together, while generate.max.count is applied to every generated segment individually.", default: 1},
          ]
        },
        fetch: {
          desc: "Fetch a segment's pages.\nThis fetcher uses a well-known model of one producer (a QueueFeeder) and many consumers (FetcherThread-s).\nQueueFeeder reads input fetchlists and populates a set of FetchItemQueue-s, which hold FetchItem-s that describe the items to be fetched. There are as many queues as there are unique hosts, but at any given time the total number of fetch items in all queues is less than a fixed number (currently set to a multiple of the number of threads).\nAs items are consumed from the queues, the QueueFeeder continues to add new input items, so that their total count stays fixed (FetcherThread-s may also add new items to the queues e.g. as a results of redirection) - until all input items are exhausted, at which point the number of items in the queues begins to decrease. When this number reaches 0 fetcher will finish.\nThis fetcher implementation handles per-host blocking itself, instead of delegating this work to protocol-specific plugins. Each per-host queue handles its own \"politeness\" settings, such as the maximum number of concurrent requests and crawl delay between consecutive requests - and also a list of requests in progress, and the time the last request was finished. As FetcherThread-s ask for new items to be fetched, queues may return eligible items or null if for \"politeness\" reasons this host's queue is not yet ready.\nIf there are still unfetched items in the queues, but none of the items are ready, FetcherThread-s will spin-wait until either some items become available, or a timeout is reached (at which point the Fetcher will abort, assuming the task is hung).",
          args: [
            {name: "threads", desc: "This argument invokes the number of threads we wish to work concurrently on fetching URLs in the desired segment e.g. the number of fetcher threads the fetcher should use. This is also determines the maximum number of requests that are made at once (each fetcher thread handles one connection).", default: 10}
          ]
        },
        parse: {
          desc: "Parse a segment's pages.\nThe class parses contents in one segment. It assumes, under the given segment, the existence of ./fetcher_output/, which is typically generated after a non-parsing fetcher run (i.e., fetcher is started with option -noParsing or as default 'false' boolean value as specified in nutch-default.xml).\nContents in one segment are parsed and saved in these steps:\n1. ./fetcher_output/ and ./content/ are looped together (possibly by multiple ParserThreads), and content is parsed for each entry. The entry number and resultant ParserOutput are saved in ./parser.unsorted.\n2. ./parser.unsorted is sorted by entry number, result saved as ./parser.sorted.\n3. ./parser.sorted and ./fetcher_output/ are looped together. At each entry, ParserOutput is split into ParseDate and ParseText, which are saved in ./parse_data/ and ./parse_text/ respectively. Also updated is FetcherOutput with parsing status, which is saved in ./fetcher/.\nIn the end, ./fetcher/ should be identical to a directory produced as a result from the fetcher being run WITHOUT option -noParsing e.g. fetching and parsing in the same command. N.B. This is not suggested in a production environment.\nBy default, intermediates ./parser.unsorted and ./parser.sorted are removed at the end, unless option -noClean is used. However ./fetcher_output/ is kept intact.",
            args: [
            {name: "noFilter", type: "checkbox", desc: "optional flag to NOT filtering URLs", default: false},
            {name: "noNormalize", type: "checkbox", desc: "optional flag for NOT normalizing URLs", default: false}
          ]
        },
        updatedb: {
          desc: "Takes the output of the fetcher and updates the crawldb accordingly",
          args: [
            {name: "force", type: "checkbox", desc: "This arguement will force an update even if the crawldb appears to be locked. <b class='text-danger'>(CAUTION: advised)</b>", default: false},
            {name: "normalize", type: "checkbox", desc: "This arguement uses any current URLNormalizer's on urls in crawldb and segment (usually not needed).", default: false},
            {name: "filter", type: "checkbox", desc: "Pass this arguement to use any current URLFilters on urls in the crawldb and segment. This can provide better quality results in certain applications.", default: false},
            {name: "noAdditions", type: "checkbox", desc: "If pass this parameter the updatedb command will only update already existing URLs, and will not add any newly discovered URLs during a fetch.", default: false}
          ]
        },
        updatehostdb: {
          desc: "Update hostdb after fetching",
          args: [
            {name: "checkAll", type: "checkbox", desc: "check all hosts", default: false},
            {name: "checkFailed", type: "checkbox", desc: "check failed hosts", default: false},
            {name: "checkNew", type: "checkbox", desc: "check new hosts", default: false},
            {name: "checkKnown", type: "checkbox", desc: "check known hosts", default: false},
            // {name: "force", type: "checkbox", desc: "force check", default: false},
            {name: "filter", type: "checkbox", desc: "filtering enabled", default: false},
            {name: "normalize", type: "checkbox", desc: "normalizing enabled", default: false}
          ]
        },
        invertlinks: {
          desc: "Create a linkdb from parsed segments\nMaintains an inverted link map, listing incoming links for each url.",
          args: [
            // {name: "force", type: "checkbox", desc: "This arguement forces an update even if linkdb appears to be locked  <b class='text-danger'>(CAUTION advised)</b", default: false},
            {name: "noNormalize", type: "checkbox", desc: "We pass this if we don't normalize link URLs. This obtains us a true representation of incoming links within the linkdb.", default: false},
            {name: "noFilter", type: "checkbox", desc: "This parameter avoids and doesn't apply any of our current URLFilters to link URLs.", default: false}
          ]
        },
        dedup: {
          desc: "Deduplicate entries in the crawldb and give them a special status\nGeneric deduplicator which groups fetched URLs with the same digest and marks all of them as duplicate except the one with the highest score (based on the score in the crawldb, which is not necessarily the same as the score indexed). If two (or more) documents have the same score, then the document with the latest timestamp is kept. If the documents have the same timestamp then the one with the shortest URL is kept. The documents marked as duplicate wirh status changed to STATUS_DB_DUPLICATE, can then be deleted with the command CleaningJob.",
          args: [
            {name: "group", desc: "", type: "select", options: [{label: "none", value: "none"}, {label: "host", value: "host"}, {label: "domain", value: "domain"}]},
            {name: "compareOrder", desc: "compare using order provided", type: "sortable", options: ["score", "fetchTime", "httpsOverHttp", "urlLength"]}
          ]
        },
        index: {
          desc: "Run the plugin-based indexer on parsed segments and linkdb\nTakes the content from one or multiple segments and passes it to all enabled IndexWriter plugins which send the documents to Solr, Elasticsearch, and various other index back-ends.",
          args: [
            // {name: "linkdb", desc:"use LinkDb to index anchor texts of incoming links"},
            {name: "params", desc: "parameters passed to indexer plugins (via property indexer.additional.params)"},
            {name: "noCommit", type: "checkbox", desc:"do not call the commit method of indexer plugins", default: false},
            {name: "deleteGone", type: "checkbox", desc:"send deletion requests for 404s, redirects, duplicates", default: false},
            {name: "filter", type: "checkbox", desc:"skip documents with URL rejected by configured URL filters", default: false},
            {name: "normalize", type: "checkbox", desc:"normalize URLs before indexing"},
            {name: "addBinaryContent", type: "checkbox", desc:"index raw/binary content in field `binaryContent`", default: false},
            {name: "base64", type: "checkbox", desc:"use Base64 encoding for binary content", default: false},
          ]
        },
        webgraph: {
          desc: "Creates three databases, one for inlinks, one for outlinks, and a node database that holds the number of in and outlinks to a url and the current score for the url. The score is set by an analysis program such as LinkRank. The WebGraph is an update-able database. Outlinks are stored by their fetch time or by the current system time if no fetch time is available. Only the most recent version of outlinks for a given url is stored. As more crawls are executed and the WebGraph updated, newer Outlinks will replace older Outlinks. This allows the WebGraph to adapt to changes in the link structure of the web. The Inlink database is created from the Outlink database and is regenerated when the WebGraph is updated. The Node database is created from both the Inlink and Outlink databases. Because the Node database is overwritten when the WebGraph is updated and because the Node database holds current scores for urls it is recommended that a crawl-cycle (one or more full crawls) fully complete before the WebGraph is updated and some type of analysis, such as LinkRank, is run to update scores in the Node database in a stable fashion.",
          args: [
            {name: "filter", type: "checkbox", desc:"skip documents with URL rejected by configured URL filters", default: false},
            {name: "normalize", type: "checkbox", desc:"normalize URLs before indexing"},
          ]
        }
      },
      isChecked: {},
      isEditBasicInfo: true,
      isEditCrawlSchedule: true,
      beforeEdit: null,
      advancedCrawling: false,
      advancedProperties: false,
      collections: null,
      selectedCollection: null,
      nutchProperties: null,
      parseHtmlBlacklist: false,
      parseHtmlWhitelist: false,
      parseHtmlTextBlacklist: false,
      disableChangeCollection: false
    }
  },
  async mounted() {
    await this.fetchData()
  },
  watch: {
    error: {
      deep: true,
      handler: function () {
        let content = (this.error.response && this.error.response.data && this.error.response.data.message) || this.error.message || this.error.toString();
        if (this.error.response && this.error.response.status === 403) {
          EventBus.dispatch("logout");
        } else {
          alert("ERROR: " + content)
        }
      }
    },
    jobData: {
      deep: true,
      handler: function () {
        if (this.jobData?.jobName !== this.jobData?.collectionName) {
          delete this.jobData.cronExpression
          this.jobData.jobGroup = "custom_crawl"
        }
      }
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
    async fetchData() {
      this.loading = true
      this.error = null

      let promises = []
      promises.push(CollectionService.getCollections('/collection', {projection: 'collectionIdNameInfo', size: 1000}))
      promises.push(CollectionService.getCollections('/scheduler/utils/nutch/properties'))

      await Promise.all(promises)
          .then(([getColections, getNutchProperties]) => {
            // handle collections
            if (this.collectionId) {
              this.collections = getColections.data._embedded.collection.filter(collection => collection.id !== this.collectionId );
            } else {
              this.collections = getColections.data._embedded.collection;
            }

            this.collections.sort((a, b) => a.name.localeCompare(b.name));

            this.nutchProperties = getNutchProperties.data

            this.nutchProperties.forEach(prop => {
              if (prop['substituted.value']) {
                this.jobData.nutchProperties[prop.name] = prop['substituted.value']
              }
            })

          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })

      if (Object.keys(this.$route.query).length !== 0) {
        await this.getCrawlConfig(this.$route.query.collectionId, this.$route.query.collectionName)
        this.disableChangeCollection = true
      }

      this.loading = false
    },
    async getCrawlConfig(id, name){
      // await CollectionService.getCollections('/collection/' + id + '/crawlConfig')
      await CollectionService.getCollections('/crawl-config/search/getCrawlConfigByCollectionName', {name: name, projection: 'collectionIdNameInfo'})
          .then(res => {
            this.crawlConfig = res.data
            //console.log("schedule collectionCrawlConfigConfig:", this.crawlConfig)
            this.jobData = {
              jobName: name,
              collectionId: id,
              collectionName: name,
              numberOfRounds: 50,
              crawlOptions: {
                index: true,
                sitemaps_from_hostdb: 'once',
                // num_threads: "150"
              },
              nutchStepArgs: {},
              nutchProperties: {},
            }

            // if (this.crawlConfig.sitemapUrls.length > 0 && this.crawlConfig.useSitemap) {
            //   this.jobData.crawlOptions['sm'] = "./sitemap_seeds"
            //   // this.jobData.nutchSteps.push("sitemap")
            //   this.jobData.nutchStepArgs['sitemap'] = {...this.jobData.nutchStepArgs['sitemap'], sitemapUrls: "./sitemap_seeds"}
            // }

            this.jobData.cronExpression = this.crawlConfig.crawlCronSchedule
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          })
    },
    getActiveTab() {
      let cronEditorData = JSON.parse(this.crawlConfig.cronEditorData)
      //console.log('getActiveTab', cronEditorData.name)
      return cronEditorData.name
    },
    getEditorData() {
      let cronEditorData = JSON.parse(this.crawlConfig.cronEditorData)
      //console.log('getEditorData', cronEditorData.editorData)
      return cronEditorData.editorData
    },
    updateCronEditorData(event) {
      //console.log('updateCronEditorData', event)
      this.crawlConfig.cronEditorData = event;
      this.jobData.cronExpression = this.crawlConfig.crawlCronSchedule
    },
    updateNutchProperties(properties) {
      //console.log("update updateNutchProperties: ", properties)
      this.jobData.nutchProperties = properties
    },
    updateNutchStepArg(step, event) {
    Object.keys(event).length === 0 ? delete this.jobData.nutchStepArgs[step] : this.jobData.nutchStepArgs[step] = event
    },
    selectCollection(event) {
      //console.log("selected collection", event)

      this.selectedCollection = this.collections.find(collection => {
        return collection.name === event
      })

      this.getCrawlConfig(this.selectedCollection.id, this.selectedCollection.name)
    },
    updateCrawlOptions(event) {
      //console.log("updateCrawlOptions event", event)
      this.jobData.crawlOptions = event

      if ("num_tasks" in event) {
        this.jobData.nutchProperties['mapreduce.job.reduces'] = event.num_tasks
      } else {
        if (this.jobData.nutchProperties?.['mapreduce.job.reduces']) {
          delete this.jobData.nutchProperties['mapreduce.job.reduces']
        }
      }

      //TODO: Fix when checked and values are empty then unchecked, the fields are removed correctly
      //console.log("updateCrawlOptions num_threads add:", ("num_threads" in event && Object.keys(event?.num_threads).length !== 0))
      if ("num_threads" in event && Object.keys(event?.num_threads).length !== 0) {
        this.jobData.nutchStepArgs['fetch'] = {...this.jobData.nutchStepArgs['fetch'], threads: event.num_threads}
        this.jobData.nutchStepArgs['sitemap'] = {...this.jobData.nutchStepArgs['sitemap'], threads: event.num_threads}
      } else {
        if (this.jobData.nutchStepArgs?.['fetch']?.['threads']) {
          //console.log("remove fetch.num_threads")
          delete this.jobData.nutchStepArgs['fetch']['threads']
        }
        if (this.jobData.nutchStepArgs?.['fetch'] && Object.keys(this.jobData.nutchStepArgs['fetch']).length === 0) {
          //console.log("remove fetch")
          delete this.jobData.nutchStepArgs['fetch']
        }

        if (this.jobData.nutchStepArgs?.['sitemap']?.['threads']) {
          //console.log("remove sitemap.num_threads")
          delete this.jobData.nutchStepArgs['sitemap']['threads']
        }
        if (this.jobData.nutchStepArgs?.['sitemap'] && Object.keys(this.jobData.nutchStepArgs['sitemap']).length === 0) {
          //console.log("remove sitemap")
          delete this.jobData.nutchStepArgs['sitemap']
        }
      }

      if ("size_fetchlist" in event) {
        this.jobData.nutchStepArgs['generate'] = {...this.jobData.nutchStepArgs['generate'], topN: event.size_fetchlist}
      } else {
        if (this.jobData.nutchStepArgs?.['generate']?.['topN']) {
          delete this.jobData.nutchStepArgs['generate']['topN']
        }
        if (this.jobData.nutchStepArgs?.['generate'] && Object.keys(this.jobData.nutchStepArgs['generate']).length === 0) {
          delete this.jobData.nutchStepArgs['generate']
        }
      }

      if ("time_limit_fetch" in event) {
        this.jobData.nutchProperties['fetcher.timelimit.mins'] = event.time_limit_fetch
      } else {
        this.nutchProperties['fetcher.timelimit.mins'] = ''
      }

      if ("dedup_group" in event) {
        this.jobData.nutchStepArgs['dedup'] = {...this.jobData.nutchStepArgs['dedup'], group: event.dedup_group}
      } else {

        if (this.jobData.nutchStepArgs?.['dedup']?.['group']) {
          delete this.jobData.nutchStepArgs['dedup']['group']
        }

        if (this.jobData.nutchStepArgs?.['dedup'] && Object.keys(this.jobData.nutchStepArgs['dedup']).length === 0) {
          delete this.jobData.nutchStepArgs['dedup']
        }
      }
    },
    getProperty(name) {
      let prop = this.nutchProperties.find(property => property.name === name)
      //console.log("]]]]]]]]]]]]]]]]]]]]]]]]]] name", name, prop)
      return prop.value
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    async createCrawlSchedule() {
      await this.updateCrawlConfig()
      await this.saveCrawlJobSchedule()
    },
    async updateCrawlConfig() {
      if (this.jobData?.jobName === this.jobData?.collectionName) {
        // let url = this.crawlConfig._links.self.href
        let body = {
          crawlCronSchedule: this.crawlConfig.crawlCronSchedule,
          cronEditorData: this.crawlConfig.cronEditorData
        }
        await CollectionService.updateCollection(this.crawlConfig._links.self.href, JSON.stringify(body))
            .then(response => {
              let data = response.data;
              this.crawlConfig = data;

              if (this.crawlConfig.sitemapUrls.length > 0 && this.crawlConfig.useSitemap) {
                this.jobData.crawlOptions['sm'] = "./sitemap_seeds"
                // this.jobData.nutchSteps.push("sitemap")
                this.jobData['sitemap'] = {...this.jobData['sitemap'], sitemapUrls: "./sitemap_seeds"}
              }

              this.jobData.cronExpression = this.crawlConfig.crawlCronSchedule
            })
            .catch(errors => {
              //console.log(errors);
              this.error = errors
            });
      }
    },
    async saveCrawlJobSchedule() {

      if (this.jobData?.nutchStepArgs?.dedup?.compareOrder) {
        this.jobData.nutchStepArgs.dedup.compareOrder = this.jobData.nutchStepArgs.dedup.compareOrder.join(",")
      }

      //console.log("save saveCrawlJobSchedule", JSON.stringify(this.jobData, null, 2))
      await SchedulerService.createCrawlJob("/scheduler/schedule", JSON.stringify(this.jobData))
          .then(response => {
            let data = response.data;
            console.log("createCrawlJobSchedule", JSON.stringify(data, null, 2))
            this.$router.push({path: '/scheduler'});
          })
          .catch(errors => {
            console.log(errors);
            this.error = errors
          });
    },
    async updateCrawlJobSchedule() {

      if (this.jobData?.nutchStepArgs?.dedup?.compareOrder) {
        this.jobData.nutchStepArgs.dedup.compareOrder = this.jobData.nutchStepArgs.dedup.compareOrder.join(",")
      }

      //console.log("save updateCrawlJobSchedule", JSON.stringify(this.jobData, null, 2))
      await SchedulerService.updateCrawlJob("/scheduler/update", JSON.stringify(this.jobData))
          .then(response => {
            let data = response.data;
            data.data.forEach(job => {
              if (job.jobName === this.jobData.jobName) {
                console.log("create CrawlJobSchedule", JSON.stringify(job, null, 2))
              }
            })

          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
  }
}
</script>

<style scoped>

</style>
