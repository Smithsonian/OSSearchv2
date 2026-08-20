<template>
  <div class="card mt-3 mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>Parse Checker</b>
    </div>
    <div class="card-body">
      <div class="row g-3 mb-3">
        <div class="col-md-12">
          <p>Parser checker, useful for testing crawler parsing.</p>
          <p>It also accurately reports possible fetching and parsing failures and presents protocol status signals to aid debugging.</p>
          <p>The tool enables us to retrieve the following data from any url:</p>
          <ol>
            <li><b>contentType</b>: The URL Content type.</li>
            <li><b>signature</b>: Digest is used to identify pages (like unique ID) and
              is used to remove duplicates during the dedup procedure. It is calculated
              using org.apache.nutch.crawl.MD5Signature or
              org.apache.nutch.crawl.TextProfileSignature.
            </li>
            <li><b>Version</b>: From ParseData.</li>
            <li><b>Status</b>: From ParseData.</li>
            <li><b>Title</b>: of the URL</li>
            <li><b>Outlinks</b>: associated with the URL.</li>
            <li><b>Content Metadata</b>: such as <i>X-AspNet-Version</i>, <i>Date</i>,
              <i>Content-length</i>, <i>servedBy</i>, <i>Content-Type</i>,
              <i>Cache-Control</i>, etc.
            </li>
            <li><b>Parse Metadata</b>: such as <i>CharEncodingForConversion</i>,
              <i>OriginalCharEncoding</i>, <i>language</i>, etc.
            </li>
            <li><b>RawText</b>: The page raw text before any parse filters are applied. Varies in length depending
              on <code>content.length</code> configuration.
            </li>
            <li><b>ParseText</b>: The page parse text after any parse filters are applied. Varies in length depending
              on <code>content.length</code> configuration.
            </li>
          </ol>

          <p><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>Outlinks found during the parsing step are added to the crawldb for fetching in following crawl rounds. Nutch by default takes links from: <code>a, area, form, frame, iframe, script, link, img, and source</code> tags. If you notice any links not showing up after running the parse checker it may be that the links tag is not in Nutch default tag list. If that is the case you can add the tag to <code>parser.html.outlinks.include_tags</code> crawl property</p>

          <p><i class="fas fa-lightbulb text-warning pe-2"></i><b class="pe-2">Quick Tip:</b>Parse Metadata returned by the parse checker may not all be indexed during crawling. For the list of metadata that gets indexed see <code>index.parse.md</code> and <code>index.metadata.multivalued.fields</code> crawl properties.</p>

        </div>
      </div>

      <div class="form-floating mb-3">
        <input type="url" class="form-control" :class="v$.parseOptions.url.$error ? (animated ? 'is-invalid shake' : 'is-invalid') : null" id="floatingInput" placeholder="http://example.com" v-model="parseOptions.url" @keyup.enter="runParseChecker">
        <label for="floatingInput">URL</label>
        <div class="invalid-feedback" v-for="error of v$.parseOptions.url.$errors" :key="error.$uid">
          {{ error.$message }}
        </div>
      </div>

      <div class="row">
        <legend class="col col-sm-auto col-form-label text-end pt-0">options:</legend>
        <div class="col col-sm-10">
          <div class="form-check form-switch">
            <input class="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckNormalize" v-model="parseOptions.normalize">
            <label class="form-check-label" for="flexSwitchCheckNormalize">normalize</label>
          </div>
          <div class="form-text mb-3">Normalize URL's</div>
          <div class="form-check form-switch">
            <input class="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckNormalize" v-model="parseOptions.checkRobotsTxt">
            <label class="form-check-label" for="flexSwitchCheckNormalize">checkRobotsTxt</label>
          </div>
          <div class="form-text mb-3">Fail if the robots.txt disallows fetching</div>
          <div class="form-check form-switch">
            <input class="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckNormalize" v-model="parseOptions.dumpText">
            <label class="form-check-label" for="flexSwitchCheckNormalize">dumpText</label>
          </div>
          <div class="form-text mb-3">Also show the plain-text extracted by parsers</div>
          <div class="form-check form-switch">
            <input class="form-check-input" type="checkbox" role="switch" id="flexSwitchCheckNormalize" v-model="parseOptions.followRedirects">
            <label class="form-check-label" for="flexSwitchCheckNormalize">followRedirects</label>
          </div>
          <div class="form-text mb-3">Follow redirects when fetching URL</div>
        </div>
      </div>

      <div class="btn-group-sm float-start">
        <button type="button" class="btn btn-sm btn-primary" @click="runParseChecker()">Submit</button>
      </div>
    </div>
  </div>

  <div style="position: relative">
    <div ref="result" style="height: 50px; position: absolute; top: -50px; pointer-events: none;"></div>
  </div>

  <div class="card mb-4" v-if="data">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>Parse Checker Result</b>
    </div>
    <div class="card-body">
      <div class="row g-3 mb-3">
        <div class="col">

          <ul class="list-group">

            <li class="list-group-item d-flex justify-content-start align-items-start">
              <div class="me-2 fw-bold text-nowrap">Output:</div>
              <pre>{{ data.output }}</pre>
            </li>

            <template v-if="data.result">
              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold">URL:</div>
                <a :href="data.result.url" target="_blank" class="text-wrap" style="word-break: break-all">{{ data.result.url}}</a>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Status:</div>
                <span class="text-wrap" style="word-break: break-all">{{ data.result.Status }}</span>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">ContentType:</div>
                <span class="text-wrap" style="word-break: break-all">{{ data.result.contentType }}</span>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Signature:</div>
                <span  class="text-wrap" style="word-break: break-all">{{ data.result.signature }}</span>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Title:</div>
                <span class="text-wrap" style="word-break: break-all">{{ data.result.Title}}</span>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Parse Metadata:</div>
                <ul class="list-unstyled">
                  <template v-for="(value, key, i) in data.result['Parse\ Metadata']" :key="i">
                    <li class="d-flex justify-content-start align-items-start">
                      <div class="me-2 fw-bold text-nowrap">{{key}}:</div>
                      <template v-for="item in value" :key="item">
                        <div class="text-wrap" style="word-break: break-all">{{item}}</div>
                      </template>
                    </li>
                  </template>
                </ul>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Content Metadata:</div>
                <ul class="list-unstyled">
                  <template v-for="(value, key, i) in data.result['Content\ Metadata']" :key="i">
                    <li class="d-flex justify-content-start align-items-start">
                      <div class="me-2 fw-bold text-nowrap">{{key}}:</div>
                      <template v-for="item in value" :key="item">
                        <div class="text-wrap" style="word-break: break-all">{{item}}</div>
                      </template>
                    </li>
                  </template>
                </ul>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Outlinks:</div>
                <span class="badge bg-primary rounded-pill me-2">{{data.result.Outlinks.count}}</span>
                <ul class="list-group list-group-numbered w-100">
                  <template v-for="(value, i) in data.result.Outlinks.outlinks" :key="i">
                    <li class="list-group-item d-flex justify-content-start align-items-start">
                      <ul class="list-unstyled">
                        <li class="d-flex justify-content-start align-items-start">
                          <div class="ms-2 me-2 fw-bold">toUrl:</div>
                          <a :href="value.toUrl" target="_blank" class="text-wrap" style="word-break: break-all">{{value.toUrl}}</a>
                        </li>
                        <li class="d-flex justify-content-start align-items-start">
                          <div class="ms-2 me-2 fw-bold">anchor:</div>
                          {{value.anchor}}
                        </li>
                      </ul>
                    </li>
                  </template>
                </ul>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Raw Text:</div>
                <div class="text-wrap">{{ data.result.raw_text}}</div>
              </li>

              <li class="list-group-item d-flex justify-content-start align-items-start">
                <div class="me-2 fw-bold text-nowrap">Parse Text:</div>
                <div class="text-wrap">{{ data.result.parse_text}}</div>
              </li>
            </template>
          </ul>

        </div>
      </div>
    </div>
  </div>

</template>

<script>
import CrawlUtilsService from "../../../services/crawlUtils.service";
import { useVuelidate } from '@vuelidate/core'
import { required, url } from '@vuelidate/validators'

export default {
  name: "ParseChecker",
  props: ['name', 'tabName'],
  setup () {
    return {
      v$: useVuelidate()
    }
  },
  data() {
    return {
      error: null,
      showJson: true,
      data: null,
      animated: false,
      parseOptions: {
        collectionName: this.name,
        url: '',
        normalize: false,
        checkRobotsTxt: false,
        dumpText: false,
        followRedirects: false
      }
    }
  },
  validations() {
    return {
      parseOptions: {
        url: {
          url,
          required,
          // $autoDirty: true
        },
        // $autoDirty: true
      }
    }
  },
  methods: {
    async runParseChecker() {

      const isValid = await this.v$.$validate()
      this.animated = true
      setTimeout(() => {
        this.animated = false
      }, 1500)
      if (!isValid) return

      let params = this.parseOptions

      await CrawlUtilsService.get('/crawl/utils/urls/parsechecker', params)
          .then(response => {
            this.data = response.data;
            console.log("data", this.data)
          })
          .catch(errors => {
            console.log(errors);
            console.log(errors.message);
            console.log(errors.response);
            console.log(errors.response.data);
            console.log(errors.response.status);
            console.log(errors.response.headers);
            this.error = errors
          });

      this.setFocus()
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    setFocus() {
      setTimeout(() => { this.$refs.result.scrollIntoView({ behavior: "smooth" }) }, 200)
    }
  }
}
</script>

<style scoped lang="scss">
.shake {
  animation: shake 0.82s cubic-bezier(.36,.07,.19,.97) both;
  transform: translate3d(0, 0, 0);
}
@keyframes shake {
  10%, 90% {
    transform: translate3d(-1px, 0, 0);
  }
  20%, 80% {
    transform: translate3d(2px, 0, 0);
  }
  30%, 50%, 70% {
    transform: translate3d(-4px, 0, 0);
  }
  40%, 60% {
    transform: translate3d(4px, 0, 0);
  }
}
</style>
