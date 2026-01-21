import { createRouter, createWebHistory } from 'vue-router';
import Login from '../views/Login';
import store from '@/store';
import Collections from '../views/collections/Collections';
import Collection from "../views/collections/Collection";
import Dashboard from "../views/Dashboard";
import UserForm from "../views/users/UserForm";
import UserDetails from "../views/users/UserDetails";
import Reports from "../views/reports/Reports";
import CrawlScheduleDetails from "../views/scheduler/CrawlScheduleDetails";
import CrawlScheduleForm from "../views/scheduler/CrawlScheduleForm";
import CrawlLogs from "../views/scheduler/CrawlLogs";
import CrawlSteps from "../views/scheduler/CrawlSteps";
import AddRemoveUrls from "../views/collections/collection/AddRemoveUrls";
import KeywordReport from "../views/collections/collection/reports/KeywordReport";
import KeywordCountReport from "../views/collections/collection/reports/KeywordCountReport";
import IndexedUrlsReport from "../views/collections/collection/reports/IndexedUrlsReport";
import UrlReport from "../views/collections/collection/reports/UrlReport";
import CrawldbReport from "../views/collections/collection/reports/CrawldbReport";
import ChangeHistory from "../views/collections/collection/ChangeHistory";
import BackupRestoreCollection from "../views/collections/collection/BackupRestoreCollection.vue";
import CollectionOverview from "../views/collections/collection/CollectionOverview";
import CollectionSearchConfig from "../views/collections/collection/search/CollectionSearchConfig";
import CollectionSearchFacetConfig from "../views/collections/collection/search/CollectionSearchFacetConfig";
import CollectionSearchKeymatchConfig from "../views/collections/collection/search/CollectionSearchKeymatchConfig";
import CollectionOverlappingSearchConfig from "../views/collections/collection/search/CollectionOverlappingSearchConfig";
import CollectionCrawlingConfig from "../views/collections/collection/crawling/CollectionCrawlingConfig";
import CollectionCrawlSchedule from "../views/collections/collection/crawling/CollectionCrawlSchedule";
import CollectionUsers from "../views/collections/collection/CollectionUsers";
import CollectionCrawlingIncludeExcludeSiteUrls from "../views/collections/collection/crawling/CollectionCrawlingIncludeExcludeSiteUrls";
import CollectionCrawlingUrlExclusionPatterns from "../views/collections/collection/crawling/CollectionCrawlingUrlExclusionPatterns";
import CollectionCrawlingSitemaps from "../views/collections/collection/crawling/CollectionCrawlingSitemaps";
import CollectionCreate from "../views/collections/CollectionCreate";
import CollectionSearchPageResults from "../views/collections/collection/search/CollectionSearchPageResults";
import SearchReport from "../views/collections/collection/reports/SearchReport";
import ParseChecker from "../views/collections/collection/ParseChecker.vue";
import CollectionCrawlingUrlNormalizerPatterns from "../views/collections/collection/crawling/CollectionCrawlingUrlNormalizerPatterns.vue";
import CollectionSearchEdanFieldMappingConfig from "../views/collections/collection/search/CollectionSearchEdanFieldMappingConfig.vue";
import BackupRestore from "../views/BackupRestore.vue";
import Reindex from "../views/Reindex.vue";
import KeywordsAndPhrasesReport from "../views/reports/KeywordsAndPhrasesReport.vue";

import Tutorial from '../views/tutorial/Tutorial';
import TutorialCreateCollection from '../views/tutorial/collection/TutorialCreateCollection.vue';


// lazy-loaded
const CrawlScheduler = () => import('../views/scheduler/CrawlScheduler');
import SearchCounts from '../views/reports/search/SearchCounts';
import SearchUrls from '../views/reports/search/SearchUrls';
const Users = () => import('../views/users/Users');
const ServerStatus = () => import('../views/ServerStatus');

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    linkActiveClass: "active",
    component: Dashboard
  },
  {
    path: '/dashboard',
    redirect: {
      name: 'Dashboard'
    }
  },
  {
    path: '/login',
    name: 'login',
    component: Login
  },
  {
    path: '/collections',
    name: 'collections',
    // lazy-loaded
    component: Collections,
  },
  {
    path: '/collections/:name/reports',
    props: true,
    redirect: {
      name: 'configuration'
    }
  },
  {
    path: '/collections/:name/search',
    props: true,
    redirect: {
      name: 'collectionSearch'
    }
  },
  {
    path: '/collections/:name/crawling',
    props: true,
    redirect: {
      name: 'collectionCrawlingConfig'
    }
  },
  {
    path: '/collections/:name',
    name: 'collection',
    component: Collection,
    props: true,
    redirect: {
      name: 'collectionOverview'
    },
    children: [
      {
        path: 'overview',
        name: 'collectionOverview',
        component: CollectionOverview,
        props: true
      },
      {
        path: 'search/config',
        name: 'collectionSearch',
        component: CollectionSearchConfig,
        props: true
      },
      {
        path: 'search/edan_field_mapping',
        name: 'collectionSearchEdanFieldMappingConfig',
        component: CollectionSearchEdanFieldMappingConfig,
        props: true
      },
      {
        path: 'search/faceting',
        name: 'collectionSearchFaceting',
        component: CollectionSearchFacetConfig,
        props: true
      },
      {
        path: 'search/keymatch',
        name: 'collectionSearchKeymatch',
        component: CollectionSearchKeymatchConfig,
        props: true
      },
      {
        path: 'search/overlapping-searches',
        name: 'collectionOverlappingSearches',
        component: CollectionOverlappingSearchConfig,
        props: true
      },
      {
        path: 'search/page-results',
        name: 'collectionSearchPageResults',
        component: CollectionSearchPageResults,
        props: true
      },
      {
        path: 'crawling',
        name: 'collectionCrawlingConfig',
        component: CollectionCrawlingConfig,
        props: true
      },
      {
        path: 'crawling/include-exclude-urls',
        name: 'collectionCrawlingIncludeExcludeSiteUrls',
        component: CollectionCrawlingIncludeExcludeSiteUrls,
        props: true
      },
      {
        path: 'crawling/url-exclusion-patterns',
        name: 'collectionCrawlingUrlExclusionPatterns',
        component: CollectionCrawlingUrlExclusionPatterns,
        props: true
      },
      {
        path: 'crawling/url-normalizer-patterns',
        name: 'collectionCrawlingUrlNormalizerPatterns',
        component: CollectionCrawlingUrlNormalizerPatterns,
        props: true
      },
      {
        path: 'crawling/sitemaps',
        name: 'collectionCrawlingSitemaps',
        component: CollectionCrawlingSitemaps,
        props: true
      },
      {
        path: 'crawling/schedule',
        name: 'collectionCrawlSchedule',
        component: CollectionCrawlSchedule,
        props: true
      },
      {
        path: 'addRemoveUrls',
        name: 'addRemoveUrls',
        component: AddRemoveUrls,
        props: true
      },
      {
        path: 'parseChecker',
        name: 'parseChecker',
        component: ParseChecker,
        props: true
      },
      {
        path: 'reports/search',
        name: 'searchReport',
        component: SearchReport,
        props: true
      },
      {
        path: 'reports/keywords',
        name: 'keywordReport',
        component: KeywordReport,
        props: true
      },
      {
        path: 'reports/keywordsCount',
        name: 'keywordCountReport',
        component: KeywordCountReport,
        props: true
      },
      {
        path: 'reports/indexedUrls',
        name: 'indexedUrlsReport',
        component: IndexedUrlsReport,
        props: true
      },
      {
        path: 'reports/urlDetails',
        name: 'urlReport',
        component: UrlReport,
        props: true
      },
      {
        path: 'reports/crawldb',
        name: 'crawldbReport',
        component: CrawldbReport,
        props: true
      },
      {
        path: 'changeHistory',
        name: 'changeHistory',
        component: ChangeHistory,
        props: true
      },
      {
        path: 'backupRestore',
        name: 'backupRestoreCollection',
        component: BackupRestoreCollection,
        props: true
      },
      {
        path: 'users',
        name: 'collectionUsers',
        component: CollectionUsers,
        props: true
      },
    ]
  },
  {
    path: '/collections/create',
    name: 'collectionCreate',
    component: CollectionCreate,
    beforeEnter: isAdmin
  },
  {
    path: '/scheduler',
    name: 'crawlScheduler',
    // lazy-loaded
    component: CrawlScheduler,
  },
  {
    path: '/scheduler/create',
    name: 'crawlScheduleForm',
    component: CrawlScheduleForm,
    beforeEnter: isAdmin,
    props: true
  },
  {
    path: '/scheduler/scheduled_crawl',
    redirect: {
      name: 'crawlScheduler'
    }
  },
  {
    path: '/scheduler/:groupName/:jobName',
    name: 'crawlScheduleDetails',
    component: CrawlScheduleDetails,
    props: true
  },
  {
    path: '/scheduler/history/scheduled_crawl',
    redirect: {
      name: 'crawlScheduler'
    }
  },
  {
    path: '/scheduler/history',
    redirect: {
      name: 'crawlScheduler'
    }
  },
  {
    path: '/scheduler/history/:jobName',
    name: 'crawlLogs',
    component: CrawlLogs,
    children: [
      {
        // CrawlSteps will be rendered inside CrawlLogs's <router-view>
        // when /scheduler/history/:jobGroup.:jobName/:jobId is matched
        path: ':jobId',
        name: 'crawlSteps',
        component: CrawlSteps,
      }
    ]
  },
  {
    path: '/reports',
    name: Reports,
    // lazy-loaded
    component: Reports
  },
  {
    path: '/search-stats',
    name: 'search-stats',
    children: [
      {
        path: 'counts',
        name: 'searchCounts',
        component: SearchCounts,
      },
      {
        path: 'urls',
        name: 'searchUrls',
        component: SearchUrls,
      }
    ]
  },
  {
    path: '/users',
    name: Users,
    // lazy-loaded
    component: Users
  },
  {
    path: '/users/:name',
    name: 'userDetails',
    component: UserDetails
  },
  {
    path: '/users/create',
    name: 'userForm',
    component: UserForm,
    beforeEnter: isAdmin
  },
  {
    path: '/backend',
    name: 'backend',
    // lazy-loaded
    component: ServerStatus
  },
  {
    path: '/backupRestore',
    name: 'backupRestore',
    component: BackupRestore,
    props: true
  },
  {
    path: '/reindex',
    name: 'reindex',
    component: Reindex,
    props: true
  },
  {
    path: '/reports/keywords-and-phrases',
    name: 'keywordsAndPhrasesReport',
    component: KeywordsAndPhrasesReport,
    beforeEnter: isAdmin
  },
  {
    path: '/about',
    name: 'About',
    // route level code-splitting
    // this generates a separate chunk (about.[hash].js) for this route
    // which is lazy-loaded when the route is visited.
    component: () => import(/* webpackChunkName: 'about' */ '../views/About.vue')
  },
  {
    path: '/tutorial',
    name: 'tutorial',
    component: Tutorial,
    props: true,
    children: [
      {
        path: 'collection/create',
        name: 'tutorialCreateCollection',
        component: TutorialCreateCollection,
        props: true
      }
    ]
  },
  {
    path: '/FAQ',
    name: 'FAQ',
    // route level code-splitting
    // this generates a separate chunk (about.[hash].js) for this route
    // which is lazy-loaded when the route is visited.
    component: () => import(/* webpackChunkName: 'about' */ '../views/FAQ.vue')
  }
];

const router = createRouter({
  linkActiveClass: "active",
  history: createWebHistory(process.env.BASE_URL),
  routes
});

// see the following for auth and navgard info
// https://github.com/vuejs/vue-router/tree/dev/examples/auth-flow
// https://router.vuejs.org/guide/advanced/navigation-guards.html
// https://router.vuejs.org/guide/advanced/meta.html
router.beforeEach((to, from, next) => {
  // const publicPages = ['/login', '/register', '/home'];
  const publicPages = ['/login', '/register'];
  const authRequired = !publicPages.includes(to.path);
  const loggedIn = localStorage.getItem('user');

  // trying to access a restricted page + not logged in
  // redirect to login page
  if (authRequired && !loggedIn) {
    sessionStorage.setItem('redirectPath', to.path);
    next('/login');
  } else {
    next();
  }
});

function isAdmin (to, from, next) {
  let user = store.state.auth.user;
  if (user && user.roles && user.roles.includes('ROLE_ADMIN')) {
    next();
  } else {
    next('/');
  }
}

export default router;
