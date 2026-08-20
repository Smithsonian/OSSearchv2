<template>

<!--  <div class="alert alert-warning d-flex justify-content-center align-items-center fw-bold" role="alert">
    <i class="bi bi-cone-striped text-warning"></i>
    <div>Preview</div>
    <i class="bi bi-cone-striped text-warning"></i>
  </div>-->

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
      <i class="fas fa-random me-1"></i>
      <b>EDAN Field Mapping Configuration</b>
    </div>

    <div class="card-body">
      <div class="row g-3">
        <div class="col-md-12">
          <p>Map metatags from webpage data to existing EDAN facets</p>
          <p>Allows the EDAN API or EDAN Drupal Module to create return results from both
            EDAN content (collection records, EADs, transcriptions) and webpage records (OSS data)</p>
        </div>
      </div>
    </div>
  </div>

  <div v-if="!loading" class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-address-card me-1"></i>
      <b>Core Webpage Fields</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditCore = JSON.parse(JSON.stringify(data.content)); isEditCore = !isEditCore" v-if="!isEditCore">Edit</button>
        <button v-if="isEditCore" class="btn btn-sm btn-success me-md-2" type="button" @click="save('isEditCore')">Save</button>
        <button v-if="isEditCore" class="btn btn-sm btn-danger float-end" type="button" @click="data.content = beforeEditCore; isEditCore = false">Cancel</button>
      </div>
    </div>

    <div class="card-body">
      <div v-if="loading" class="d-flex justify-content-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <template v-else>
        <fieldset :disabled="!isEditCore">

          <div class="row mb-3" v-for="(fieldOptions, fieldName) in content" :key="fieldName">
            <label :for="fieldName" class="col-sm-2 col-form-label fw-bold">
              {{fieldName}}
              <span v-if="!fieldOptions.optional" class="text-danger"> (default)</span>
            </label>
            <div class="col-sm-10">
              <input v-if="!fieldOptions.optional && typeof data.content[fieldName] !== 'object' && data.content[fieldName] !== null" type="text" class="form-control" :id="fieldName" v-model="data.content[fieldName]" :disabled="!fieldOptions.optional" >
              <input v-else-if="!fieldOptions.optional && typeof data.content[fieldName] === 'object' && data.content[fieldName] !== null" type="text" class="form-control" :id="fieldName" :value="JSON.stringify(data.content[fieldName])" :disabled="!fieldOptions.optional" >
              <Multiselect v-else id="metaFieldMultiselect"
                           v-model="data.content[fieldName]"
                           :options="indexedFieldsList"
                           :multiple="fieldOptions.multivalued"
                           :custom-label="displayName"
                           :searchable="true"
                           placeholder="Select Meta Fields..."
                           :allowEmpty="true"
                           :hideSelected="true"
                           :loading="loading"
              />
            </div>
          </div>

        </fieldset>
      </template>
    </div>
  </div>

  <div v-if="!loading" class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-file-alt me-1"></i>
      <b>Freetext Mapping</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditFreetext = JSON.parse(JSON.stringify(data.content.freetext)); isEditFreetext = !isEditFreetext" v-if="!isEditFreetext">Edit</button>
        <button v-if="isEditFreetext" class="btn btn-sm btn-success me-md-2" type="button" @click="save('isEditFreetext')">Save</button>
        <button v-if="isEditFreetext" class="btn btn-sm btn-danger float-end" type="button" @click="data.content.freetext = beforeEditFreetext; isEditFreetext = false">Cancel</button>
      </div>
    </div>

    <div class="card-body">
      <div v-if="loading" class="d-flex justify-content-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <template v-else>
        <fieldset :disabled="!isEditFreetext">

          <div class="card card-body mb-3" v-for="(v, k, i) in data.content.freetext" :key="i">
            <div class="row g-3">

              <div class="col-sm-2">
                <div class="form-floating">
                  <select :value="Object.keys(data.content.freetext)[i]" class="form-control-sm form-select"
                          :id="'freetext_term' + i" :ref="'freetext_term' + i" placeholder="Choose Freetext Term"
                          @change="updateFreetext($event.target.value, k)">
                    <option v-for="(option, i) in freetextSelect" :key="i" :value="option">{{ option }}
                      <template v-if="facet_fields.includes(option)">&#128142;</template>
                    </option>
                    <option v-if="Object.keys(data.content.freetext)[i] !== ''"
                            :value="Object.keys(data.content.freetext)[i]" selected hidden>
                      {{ Object.keys(data.content.freetext)[i] }}
                      <template v-if="facet_fields.includes(Object.keys(data.content.freetext)[i])" class="text">&#128142;</template>
                    </option>
                  </select>
                  <label :for="'freetext_term_' + i">Freetext Term</label>
                </div>
              </div>

              <div class="col-sm-10">

                <div class="row g-3 align-items-center mb-3" v-for="(entry, j) in v" :key="entry">

                  <div class="col-sm-3">
                    <div class="form-floating">
                      <input type="text" class="form-control" :id="'freetext_label_row_' + i +'col_' + j" :ref="'freetext_label_row_' + i +'col_' + j" placeholder="Label" v-model="data.content.freetext[k][j].label">
                      <label :for="'freetext_label_row_' + i +'col_' + j">Label <b class="text-danger">*</b></label>
                    </div>
                  </div>

                  <div class="col-sm-3">
                    <div class="form-floating">
                      <select class="form-control-sm form-select" :id="'freetext_category' + i" :ref="'freetext_category' + i" placeholder="Choose Category" v-model="data.content.freetext[k][j].category">
                        <option v-for="(option, i) in freetextCategory" :key="i" :value="option">{{ option }}
                        </option>
                      </select>
                      <label :for="'freetext_category_' + i">Category <b class="text-danger">*</b></label>
                    </div>
                  </div>

                  <div class="col-sm-3">
                    <div class="form-floating">
                      <select class="form-control-sm form-select" :id="'freetext_content_row_' + i +'col_' + j" :ref="'freetext_content_row_' + i +'col_' + j" placeholder="Choose Freetext" v-model="data.content.freetext[k][j].content">
                        <option v-for="(option, i) in indexedFieldsList" :key="i" :value="option">{{ option }}
                        </option>
                      </select>
                      <label :for="'freetext_content_row_' + i +'col_' + j">Content mapped to Meta Field Value <b class="text-danger">*</b></label>
                    </div>
                  </div>

                  <div class="col-sm-1">
                    <button v-if="isEditFreetext" class="btn btn-sm btn-danger" type="button" @click="data.content.freetext[k].splice(j, 1); data.content.freetext[k].length === 0 ? delete data.content.freetext[k] : ''"><i class="fas fa-trash"></i></button>
                  </div>
                </div>

                <button v-if="isEditFreetext" class="btn btn-sm btn-primary bi-plus-lg me-md-2" type="button" @click="data.content.freetext[k].push({label:'', content:''})">Meta Field Mapping</button>

              </div>

            </div>
          </div>

          <button class="btn btn-sm btn-primary bi-plus-lg me-md-2" type="button" @click="addFreetextRow()">Freetext Term</button>

        </fieldset>
      </template>

    </div>
  </div>

<!--  <div v-if="!loading" class="card mt-4 mb-4">
    <div class="card-header">
      <i class="fas fa-file-code me-1"></i>
      <b>IndexedStructured Mapping</b>
      <div class="float-end">
        <button class="btn btn-sm btn-primary float-end" type="button" @click="beforeEditIndexedStructured = JSON.parse(JSON.stringify(data.content.indexedStructured)); isEditIndexedStructured = !isEditIndexedStructured" v-if="!isEditIndexedStructured">Edit</button>
        <button v-if="isEditIndexedStructured" class="btn btn-sm btn-success me-md-2" type="button" @click="save('isEditIndexedStructured')">Save</button>
        <button v-if="isEditIndexedStructured" class="btn btn-sm btn-danger float-end" type="button" @click="data.content.indexedStructured = beforeEditIndexedStructured; isEditIndexedStructured = false">Cancel</button>
      </div>
    </div>

    <div class="card-body">
      <div v-if="loading" class="d-flex justify-content-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>

      <template v-else>
        <fieldset :disabled="!isEditIndexedStructured">

          <div class="card card-body mb-3" v-for="(v, k, i) in data.content.indexedStructured" :key="i">

            <div class="row g-3">

              <div class="col-sm-2">
                <div class="form-floating">
                  <select :value="Object.keys(data.content.indexedStructured)[i]" class="form-control-sm form-select"
                          :id="'indexedStructured_term' + i" :ref="'indexedStructured_term' + i"
                          placeholder="Choose IndexedStructured"
                          @change="updateIndexedStructured($event.target.value, k)">
                    <option v-for="(option, i) in indexedstructuredSelect" :key="i" :value="option">{{ option }}
                      <template v-if="facet_fields.includes(option)">&#128142;</template>
                    </option>
                    <option v-if="Object.keys(data.content.indexedStructured)[i] !== ''"
                            :value="Object.keys(data.content.indexedStructured)[i]" selected hidden>
                      {{ Object.keys(data.content.indexedStructured)[i] }}
                      <template v-if="facet_fields.includes(Object.keys(data.content.indexedStructured)[i])">&#128142;</template>
                    </option>
                  </select>
                  <label :for="'indexedStructured_term_' + i">IndexedStructured Term</label>
                </div>
              </div>

              <div v-if="indexedstructuredAttributeType.includes(k)" class="col">
&lt;!&ndash;                <div class="card card-body mb-3" >&ndash;&gt;
                  <div class="row g-3 align-items-center mb-3" v-for="(attr_row, attr_i) in data.content.indexedStructured[k]" :key="attr_i">

                    <div class="col">
                      <div class="form-floating">
                        <input type="text" class="form-control" :id="'indexedstructured_label_'+attr_i"
                               :ref="'indexedstructured_label_'+attr_i" placeholder="Label"
                               v-model="data.content.indexedStructured[k][attr_i].label">
                        <label :for="'indexedstructured_label_'+attr_i" class="text-capitalize">Label</label>
                      </div>
                    </div>

                    <div class="col">
                      <div class="form-floating">
                        <select class="form-control-sm form-select" :id="'indexedstructuredAttr_'+attr_i + '_'+content"
                                :ref="'indexedstructuredAttr_'+attr_i + '_'+content" placeholder="Choose Meta Field"
                                v-model="data.content.indexedStructured[k][attr_i].content">
                          <option v-for="(option, i) in indexedFieldsList" :key="i" :value="option">{{ option }}
                          </option>
                        </select>
                        <label :for="'indexedstructuredAttr_'+attr_i + '_'+content">Content mapped to Meta Field Value
                          <b class="text-danger">*</b></label>
                      </div>
                    </div>

                    <div class="col-sm-1">
                      <button v-if="isEditIndexedStructured" class="btn btn-sm btn-danger" type="button"
                              @click="data.content.indexedStructured[k].splice(attr_i,1);"><i class="fas fa-trash"></i>
                      </button>
                    </div>

                  </div>
&lt;!&ndash;                </div>&ndash;&gt;

                <button v-if="isEditIndexedStructured" class="btn btn-sm btn-primary bi-plus-lg me-md-2" type="button" @click="data.content.indexedStructured[k].push({ label: '', content: '' })">Meta Field Mapping</button>

              </div>

              <div v-else class="col-sm-3">
&lt;!&ndash;                <div class="row g-2 mb-3" v-for="(entry, j) in v" :key="entry">
                  <div class="col-sm-12">
                    <div class="form-floating">
                      <select class="form-control-sm form-select" :id="'indexedStructured_term' + i" :ref="'indexedStructured_term' + i" placeholder="Choose IndexedStructured" v-model="data.content.indexedStructured[k][j]">
                        <option v-for="(option, i) in indexedFieldsList" :key="i" :value="option">{{ option }}
                        </option>
                      </select>
                      <label :for="'indexedStructured_term_' + i">Mapped to Meta Field Value</label>
                    </div>
                  </div>
                </div>
                <button v-if="isEditIndexedStructured" class="btn btn-sm btn-primary bi-plus-lg me-md-2 float-end" type="button" @click="data.content.indexedStructured[k].push('')">Meta Field Mapping</button>&ndash;&gt;
                <Multiselect :id="'indexedStructured_term' + i"  style="min-height: fit-content"
                             v-model="data.content.indexedStructured[k]"
                             :options="indexedFieldsList"
                             :multiple="true"
                             :searchable="true"
                             placeholder="Select Content mapped to Meta Field Value"
                             :allowEmpty="false"
                             :hideSelected="true"
                             :loading="loading"
                />
              </div>

              <div class="col-sm-1">
                <button v-if="isEditIndexedStructured" class="btn btn-sm btn-danger float-end" type="button" @click="delete data.content.indexedStructured[k];"><i class="fas fa-trash"></i></button>
              </div>

            </div>
          </div>

          <button class="btn btn-sm btn-primary bi-plus-lg me-md-2" type="button" @click="addIndexedStructuredRow()">IndexedStructured Term</button>

        </fieldset>
      </template>

    </div>
  </div>-->



  <!-- JSON Review -->
  <div class="card mb-4">
    <div class="card-header">
      <i class="bi bi-braces me-1"></i>
      <b>EDAN Content Preview</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_crawlStepJson'" disabled v-model="showJson">
      </div>
    </div>
    <div class="card-body" v-if="showJson">
      <pre>{{ preview() }}</pre>
    </div>
  </div>



  <!-- JSON Review -->
  <div class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>OSS Indexed Meta Fields Available</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_crawlStepJson'" v-model="showIndexedMetaJson">
      </div>
    </div>
    <div class="card-body" v-if="showIndexedMetaJson">
      <pre>{{ print(indexedFields) }}</pre>
    </div>
  </div>

  <!-- JSON Review -->
  <div class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>EDAN Facets</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_crawlStepJson'" v-model="showFacetsJson">
      </div>
    </div>
    <div class="card-body" v-if="showFacetsJson">
      <pre>{{ print(facet_fields) }}</pre>

      <p>Omitted Complex Types or ???</p>
      <ul>
        <li>geolocation</li>
        <li>exhibition</li>
        <li>Related Record ???</li>
        <li>nmaiculture_* ???</li>
      </ul>
    </div>
  </div>

  <!-- JSON Review -->
  <div class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>EDAN Freetext Terms</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_crawlStepJson'" v-model="showFreetextJson">
      </div>
    </div>
    <div class="card-body" v-if="showFreetextJson">
      <pre>{{ print(freetext) }}</pre>
    </div>
  </div>

  <!-- JSON Review -->
<!--  <div class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>EDAN IndexedStructured Terms</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_crawlStepJson'" v-model="showIndexedStructuredJson">
      </div>
    </div>
    <div class="card-body" v-if="showIndexedStructuredJson">
      <pre>{{ print(indexedstructured) }}</pre>

      <p>Omitted Complex Types or ???</p>
      <ul>
        <li>geolocation</li>
        <li>exhibition</li>
        <li>Related Record ???</li>
        <li>nmaiculture_* ???</li>
      </ul>
    </div>
  </div>-->

  <!-- JSON Review -->
<!--  <div class="card mb-4">
    <div class="card-header">
      <i class="fas fa-info-circle me-1"></i>
      <b>Notes</b>
      <div class="form-check form-switch float-end">
        <input class="form-check-input" type="checkbox" role="switch" :id="'flexSwitchCheck_crawlStepJson'" v-model="showDesc">
      </div>
    </div>
    <div class="card-body" v-if="showDesc">
      <p>Filed Mapping Notes</p>
      <pre>
                content: {
                  page_title: title
                  page_content: content
                  page_url: url-from-nutch
                  page_meta: {key:value pairs of all meta tags}
                  image: ex. og:image || twitter:image
                  description: ex. og:description || twitter:description || ???
                }
              </pre>

      <p>I envision something like that, but we’d need to account for a few other things to make it easier for consumers:</p>
      <pre>
                content: {
                  page_title: title
                  page_content: content
                  page_meta: {key:value pairs of all meta tags}
                  page_url: url-from-nutch
                  image: ex. og:image || twitter:image
                  description: ex. og:description || twitter:description || ???
                  freetext: {
                    ...facet mapping result where meta is mapped down...
                    name: [{
                      label: dcauthor
                      content: value of meta.dcauthor
                    },{
                      label: author
                      content: value of meta.author
                    }]
                  }
                }
              </pre>

      <p>I think the freetext pattern is pretty useful/easy to consume:</p>
      <pre>
                name: [{
                        label: dcauthor
                        content: value of meta.dcauthor
                        indexValue: cross-walked/normalized meta.dcauthor
                      },{
                        label: author
                        content: value of meta.author
                        indexValue: cross-walked/normalized meta.author
                      }]
              </pre>
    </div>
  </div>-->

</template>

<script>
import CollectionService from "../../../../services/collection.service";
import EventBus from "../../../../common/EventBus";
import api from "../../../../services/api";
import Multiselect from "vue-multiselect";
// import _ from 'lodash';

export default {
  name: "CollectionSearchEdanFieldMappingConfig",
  props: ['name', 'tabName'],
  components: {
    Multiselect
  },
  data() {
    return {
      loading: false,
      error: null,
      showDesc: false,
      showJson: true,
      showIndexedMetaJson: false,
      showFacetsJson: false,
      showFreetextJson: false,
      showIndexedStructuredJson: false,
      collection: null,
      indexedFields: [],
      isEditCore: false,
      beforeEditCore: null,
      isEditFreetext: false,
      beforeEditFreetext: null,
      isEditIndexedStructured: false,
      beforeEditIndexedStructured: null,
      data: {
        content: {
          collectionID: "OSS collectionID",
          collection_name: "OSS collection name",
          page_title: "page title",
          page_content: "page content",
          page_url: "page url",
          page_meta: {meta_key:"meta_value pairs of all meta tags"},
          image: null,
          description: null,
          freetext: {},
          // indexedStructured: {}
        },
      },
      content: {
        collectionID: {optional: false, default: "OSS collectionID", suggested: [], desc: ""},
        collection_name: {optional: false, default: "OSS collection name", suggested: [], desc: ""},
        page_title: {optional: false, default: "page title", suggested: [], desc: ""},
        page_content: {optional: false, default: "page content", suggested: [], desc: ""},
        page_url: {optional: false, default: "page url", suggested: [], desc: ""},
        page_meta: {optional: false, default: "key:value pairs of all page meta tags", suggested: [], desc: ""},
        image: {optional: true, default: "", suggested: ["og:image", "twitter:image"], multivalued: false, desc: ""},
        description: {optional: true, default: "", suggested: ["og:description", "twitter:description"], multivalued: false, desc: ""},
      },
      facet_fields: [
        "common_name",
        "culture",
        "data_source",
        "date",
        "exhibition_building",
        "geo_age-era",
        "geo_age-series",
        "geo_age-stage",
        "geo_age-system",
        "language",
        "media_usage",
        "metadata_usage",
        "name",
        "object_type",
        "occupation",
        "online_media_type",
        "online_visual_material",
        "onphysicalexhibit",
        "place",
        "scientific_name",
        "set_name",
        "strat_formation",
        "strat_group",
        "strat_member",
        "tax_class",
        "tax_division",
        "tax_family",
        "tax_kingdom",
        "tax_order",
        "tax_phylum",
        "tax_sub-family",
        "topic",
        "type",
        "unit_code",
      ],
      freetext: [
        "abstract",
        "accessrestrict",
        "accruals",
        "acqinfo",
        "altformavail",
        "appraisal",
        "arrangement",
        "bibliography",
        "bioghist",
        "container",
        "creator",
        "creditline",
        "culture",
        "custodhist",
        "datasource",
        "date",
        "fileplan",
        "function",
        "genre_format",
        "identifier",
        "index",
        "language",
        "materialspec",
        "name",
        "note",
        "notes",
        "objectrights",
        "objecttype",
        "odd",
        "originalsloc",
        "otherfindaid",
        "physdesc",
        "physicaldescription",
        "physloc",
        "phystech",
        "place",
        "prefercite",
        "processinfo",
        "publisher",
        "recordid",
        "relatedmaterial",
        "scopecontent",
        "separatedmaterial",
        "setname",
        "sponsor",
        "taxonomicname",
        "title",
        "topic",
        "unitdate",
        "userestrict",
      ],
      freetextCategory: [
        "citation", ////////
        "creditLine", ////////
        "culture", ////////
        "dataSource", ////////
        "date", ////////
        "galleryLabel", ////////
        "identifier", ////////
        "language", ////////
        "name", ////////
        "notes", ////////
        "objectRights", ////////
        "objectType", ////////
        "occupation",
        "physicalDescription", ////////
        "place", ////////
        "publisher", ////////
        "recordId",
        "related_record_identifier",
        "setName", ////////
        "taxonomicName", ////////
        "title",
        "topic", ////////
      ],
      indexedstructured: [
        "category",
        "common_name",
        "culture", //type="attributeNoEmptyValueType"
        "DAMS_address",
        "date",
        "gender",
        "geo_age-era",
        "geo_age-series",
        "geo_age-stage",
        "geo_age-system",
        "grade",
        "language", //type="attributeNoEmptyValueType"
        "name", //type="attributeNoEmptyValueType"
        "object_type",
        "occupation", //type="attributeNoEmptyValueType"
        "online_media_rights",
        "online_media_type",
        "onPhysicalExhibit",
        "place",
        "related_record_id",
        "scientific_name",
        "set_name",
        "sortdate",
        "status",
        "strat_formation",
        "strat_group",
        "strat_member",
        "tax_class",
        "tax_division",
        "tax_family",
        "tax_kingdom",
        "tax_order",
        "tax_phylum",
        "tax_sub-family",
        "thesaurus_id",
        "topic",
        "type",
        "usage_flag",
        "xref",  //type="attributeNoEmptyValueType"
      ],
      indexedstructuredAttributeType: [
        "culture",
        "language",
        "name",
        "occupation",
        "xref",
      ],
      attributeNoEmptyValueType_attributes: [
        "type",
        "source_id",
        "source",
      ],
      edanmdm: {
        descriptiveNonRepeating: {
          show: true,
          fields: {
            record_ID: {optional: false, default: "", suggested: [], desc: ""},
            unit_code: {optional: false, default: "", suggested: [], desc: ""},
            data_source: {optional: false, default: "", suggested: [], desc: ""},
            title_sort: {optional: false, default: "", suggested: [], desc: ""},
            title: {optional: false, default: "", suggested: [], desc: ""},
            record_link: {optional: true, default: "", suggested: [], desc: ""},
            record_type: {optional: true, default: "", suggested: [], desc: ""},
            online_media: {optional: true, default: "", suggested: [], desc: ""},
            metadata_usage: {optional: true, default: "", suggested: [], desc: ""},
            guid: {optional: true, default: "", suggested: [], desc: ""}
          }
        },
        descriptiveOptional: {
          show: false,
          fields: {
            freetext: {optional: true, default: "", attributes: {label: "", related_record_ID: "", category: ""}, suggested: [], desc: ""}
          }
        },
        indexedStructured: {
          show: false,
          fields: {
            object_type: {optional: true, default: "", suggested: [], desc: ""},
            topic: {optional: true, default: "", suggested: [], desc: ""},
            place: {optional: true, default: "", suggested: [], desc: ""},
            date: {optional: true, default: "", suggested: [], desc: ""},
            sortdate: {optional: true, default: "", suggested: [], desc: ""},
            name: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
            online_media_type: {optional: true, default: "", suggested: [], desc: ""},
            online_media_rights: {optional: true, default: "", suggested: [], desc: ""},
            usage_flag: {optional: true, default: "", suggested: [], desc: ""},
            set_name: {optional: true, default: "", suggested: [], desc: ""},
            related_record_id: {optional: true, default: "", suggested: [], desc: ""},
            thesaurus_id: {optional: true, default: "", suggested: [], desc: ""},
            "geo_age-era": {optional: true, default: "", suggested: [], desc: ""},
            "geo_age-system": {optional: true, default: "", suggested: [], desc: ""},
            "geo_age-series": {optional: true, default: "", suggested: [], desc: ""},
            "geo_age-stage": {optional: true, default: "", suggested: [], desc: ""},
            tax_kingdom: {optional: true, default: "", suggested: [], desc: ""},
            tax_phylum: {optional: true, default: "", suggested: [], desc: ""},
            tax_division: {optional: true, default: "", suggested: [], desc: ""},
            tax_class: {optional: true, default: "", suggested: [], desc: ""},
            tax_order: {optional: true, default: "", suggested: [], desc: ""},
            tax_family: {optional: true, default: "", suggested: [], desc: ""},
            "tax_sub-family": {optional: true, default: "", suggested: [], desc: ""},
            common_name: {optional: true, default: "", suggested: [], desc: ""},
            scientific_name: {optional: true, default: "", suggested: [], desc: ""},
            strat_group: {optional: true, default: "", suggested: [], desc: ""},
            strat_formation: {optional: true, default: "", suggested: [], desc: ""},
            strat_member: {optional: true, default: "", suggested: [], desc: ""},
            onPhysicalExhibit: {optional: true, default: "", suggested: [], desc: ""},
            gender: {optional: true, default: "", suggested: [], desc: ""},
            grade: {optional: true, default: "", suggested: [], desc: ""},
            DAMS_address: {optional: true, default: "", suggested: [], desc: ""},
            entityId: {optional: true, default: "", attributes: {thesaurus: "VIAF", matchType: "machine_heading"}, suggested: [], desc: ""},
            occupation: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
            xref: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
            culture: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
            language: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
          }
        },
        sort: {
          show: false,
          fields: {
            name: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
            title: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
            collection: {optional: true, default: "", attributes: {type: "", source_id: "", source_ID: "", source: ""}, suggested: [], desc: ""},
          }
        }
      }
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
  computed: {
    indexedFieldsList() {
      return this.indexedFields.map(entry => entry.fieldName)
    },
    freetextSelect() {
      let freetextTermsUsed = Object.keys(this.data.content.freetext)

      let answer = this.freetext.filter(el => !freetextTermsUsed.includes(el))

      return answer
    },
    indexedstructuredSelect() {
      let indexedstructuredTermsUsed = Object.keys(this.data.content.indexedStructured)

      let answer = this.indexedstructured.filter(el => !indexedstructuredTermsUsed.includes(el))

      return answer
    }
  },
  methods: {
    async fetchData() {
      this.loading = true
      if (Object.keys(this.$route.params).length !== 0) {
        //console.log(">>>> params", this.$route.params, "query", this.$route.query)
        this.error = this.collection = null
        await this.getCollection()
        await this.getIndexedFields()
        await this.getEdanFieldMapping()
      }
      this.loading = false
    },
    async getCollection() {

      await CollectionService.getCollections('/collection/search/getCollectionByName', {name: this.$route.params.name, projection: 'overlappingCollections'})
          .then(response => {
            let data = response.data;
            this.collection = data;
            this.data.content.collectionID = this.collection.id
            this.data.content.collection_name = this.collection.name
            // console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    async getEdanFieldMapping() {

      await CollectionService.getCollections('/edanFieldMapping/'+this.collection.id, null)
          .then(response => {
            let data = response.data;
            this.data = data.edanContentFields;
            // console.log("data", data)
          })
          .catch(errors => {
            if (errors.response.status)
            console.error("Error response:");
            console.error(errors.response.data);    // ***
            console.error(errors.response.status);  // ***
            console.error(errors.response.headers); // ***
            this.error = errors
          });
    },
    async getIndexedFields() {
      let url = "/edan/utils/indexedFields/"+this.collection.id
      let params = null
      api.get(url, {headers: {"Content-Type": "application/json"}, params: params})
          .then(response => {
            this.indexedFields = response.data;
            // console.log("indexedFields", this.indexedFields)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });
    },
    save(isEdit) {
      this.saving = true

      let url = '/edanFieldMapping/'+this.collection.id
      let body = {edanContentFields: this.data, collection: this.collection._links.self.href}

      CollectionService.addEdanFieldMappingCollections(url, body)
          .then(response => {
            let data = response.data;
            this.data = data.edanContentFields;
            // console.log("data", data)
          })
          .catch(errors => {
            //console.log(errors);
            this.error = errors
          });

      EventBus.dispatch('toast', {
        type: 'success',
        msg: 'EDAN Field Mapping Updated!'
      })
      this.saving = false
      this[isEdit] = false
    },
    preview() {
      let order = [
        "collectionID",
        "collection_name",
        "page_title",
        "page_content",
        "page_url",
        "page_meta",
        "image",
        "description",
        "freetext",
        "indexedStructured"
      ]
      let answer = this.setKeyOrder(this.data.content, order);
      return JSON.stringify({content: answer}, null, 2)
    },
    setKeyOrder(obj, order){
      let answer = {};
      for(var i = 0; i < order.length; i++) {
        if(Object.prototype.hasOwnProperty.call(obj, order[i])) {
          answer[order[i]] = obj[order[i]];
        }
      }
      return answer
    },
    print(value) {
      return JSON.stringify(value, null, 2)
    },
    setShow(name) {
      Object.keys(this.edanmdm).forEach(key => {
        if (key === name) {
          this.edanmdm[key].show = true
        } else {
          this.edanmdm[key].show = false
        }
      })
    },
    displayName(metaFieldName) {
      let indexedField = this.indexedFields.find(x => x.fieldName === metaFieldName)
      return metaFieldName.replace('meta_', '') + ' ('+indexedField.count+')'
    },
    addFreetextRow() {
      this.data.content.freetext = {...this.data.content.freetext, "": [{label: "", content: ""}]}

      // let refName = 'freetext_' + (this.data.content.freetext.length - 1)
      // this.$nextTick(() => this.$refs[refName][0].focus())
    },
    addIndexedStructuredRow() {
      this.data.content.indexedStructured = {...this.data.content.indexedStructured, "": []}

      // let refName = 'indexedStructured_' + (this.data.content.indexedStructured.length - 1)
      // this.$nextTick(() => this.$refs[refName][0].focus())
    },
    addFreetextRowValue(key, index) {
      console.log("key", key, "index", index)
      //this.data.content.freetext = {...this.data.content.freetext, "": [{label: "", content: ""}]}

      // let refName = 'freetext_' + (this.data.content.freetext.length - 1)
      // this.$nextTick(() => this.$refs[refName][0].focus())
    },
    updateFreetext(newKey, oldKey) {
      delete Object.assign(this.data.content.freetext, {[newKey]: this.data.content.freetext[oldKey] })[oldKey];
    },
    updateIndexedStructured(newKey, oldKey) {
      delete Object.assign(this.data.content.indexedStructured, {[newKey]: this.data.content.indexedStructured[oldKey] })[oldKey];
      this.data.content.indexedStructured[newKey] = []
    }
  }
}
</script>

<style lang="scss">
// Import Bootstrap variables for use within theme
@import "~bootstrap/scss/functions.scss";
@import "~bootstrap/scss/variables.scss";

$vue-multiselect-min-height: $form-floating-height !important;

.multiselect {
  min-height: $form-floating-height;
}
</style>
