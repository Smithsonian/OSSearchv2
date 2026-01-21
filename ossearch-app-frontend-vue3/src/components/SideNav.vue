<template>
  <nav class="sb-sidenav accordion sb-sidenav-dark" id="sidenavAccordion">
    <div class="sb-sidenav-menu">
      <div class="nav">
        <router-link class="nav-link" to="/">
          <div class="sb-nav-link-icon">
            <i class="fas fa-tachometer-alt"></i>
          </div>
          Dashboard
        </router-link>

        <li class="nav-item">
          <a href="#" class="nav-link collapsed" data-bs-toggle="collapse" data-bs-target="#collapseSearch" aria-expanded="false" aria-controls="collapseSearch">
            <i class="fas fa-search me-2"></i>
            Search
            <i class="fas fa-angle-down ms-auto float-end sb-sidenav-collapse-arrow"></i>
          </a>
        </li>
        <div class="collapse" id="collapseSearch">
          <ul class="nav flex-column mb-auto ms-4">
            <router-link class="nav-link" to="/search-stats/counts">
              <i class="fas fa-chart-line me-2"></i>
              Counts
            </router-link>
<!--            <router-link class="nav-link" to="/search-stats/urls">
              <i class="fas fa-link me-2"></i>
              Available URL's
            </router-link>-->
          </ul>
        </div>

        <router-link class="nav-link" to="/collections">
          <i class="fas fa-sitemap me-2"></i>
          Collections
<!--          <router-link type="button" class="link-light float-end" to="/collections/create">
            <i class="fas fa-plus-circle text-light"></i>
          </router-link>-->
        </router-link>
        <router-link class="nav-link" to="/scheduler">
          <i class="fas fa-spider me-2"></i>
          Crawl Scheduler
        </router-link>

        <li class="nav-item">
          <a href="#" class="nav-link collapsed"
             data-bs-toggle="collapse"
             data-bs-target="#collapseReports"
             aria-expanded="false"
             aria-controls="collapseReports">
            <i class="fas fa-chart-bar me-2"></i>
            Reports
            <i class="fas fa-angle-down ms-auto float-end sb-sidenav-collapse-arrow"></i>
          </a>
        </li>
        <div class="collapse" id="collapseReports">
          <ul class="nav flex-column mb-auto ms-4">
            <router-link class="nav-link" to="/reports/keywords-and-phrases">
              <i class="fas fa-file-alt me-2"></i>
              Keywords and Phrases
            </router-link>
          </ul>
        </div>

        <template v-if="isAdmin">
          <router-link class="nav-link" to="/users">
            <i class="fas fa-users me-2"></i>
            Users
          </router-link>
          <router-link class="nav-link" to="/backend">
            <i class="fas fa-server me-2"></i>
            System
          </router-link>
          <router-link class="nav-link" to="/backupRestore">
            <i class="fas fa-history me-2"></i>
            Bulk Backup/Restore
          </router-link>
          <router-link class="nav-link" to="/reindex">
            <i class="fas fa-history me-2"></i>
            Reindex
          </router-link>
        </template>

        <router-link class="nav-link" to="/about">
          <i class="fas fa-question me-2"></i>
          About
        </router-link>

        <li class="nav-item">
          <a href="#" class="nav-link collapsed" data-bs-toggle="collapse" data-bs-target="#collapseTutorials" aria-expanded="false" aria-controls="collapseTutorials">
            <i class="fas fa-book me-2"></i>
            Tutorials
            <i class="fas fa-angle-down ms-auto float-end sb-sidenav-collapse-arrow"></i>
          </a>
        </li>
        <div class="collapse" id="collapseTutorials">
          <ul class="nav flex-column mb-auto ms-4">
            <router-link class="nav-link" to="/tutorial/collection/create">
              Create Collection
            </router-link>
<!--            <router-link class="nav-link" to="/tutorial/collection/config">
              <i class="fas fa-chart-line me-2"></i>
              Configure Collection
            </router-link>
            <router-link class="nav-link" to="/tutorial/crawl-shedule/create">
              <i class="fas fa-link me-2"></i>
              Create Crawl Schedules
            </router-link>
            <router-link class="nav-link" to="/tutorial/crawl-shedule/config">
              <i class="fas fa-link me-2"></i>
              Create Crawl Schedules
            </router-link>
            <router-link class="nav-link" to="/tutorial/crawling">
              <i class="fas fa-link me-2"></i>
              Crawling
            </router-link>
            <router-link class="nav-link" to="/tutorial/indexing">
              <i class="fas fa-link me-2"></i>
              Indexing
            </router-link>-->
          </ul>
        </div>
        <router-link class="nav-link" to="/faq">
          <i class="fas fa-question me-2"></i>
          FAQ
        </router-link>
      </div>
    </div>
    <div class="sb-sidenav-footer">
      <div class="small">Logged in as:</div>
      {{ currentUser.username }}
    </div>
  </nav>
</template>
<script>
export default {
  name: 'SideNav',
  props: ['currentUser', 'isAdmin']
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped lang="scss">
// Import Bootstrap variables for use within theme
@import "~bootstrap/scss/functions.scss";
@import "~bootstrap/scss/variables.scss";

//
// Spacing
//

// Spacing variables for navigation
$topnav-base-height: 56px;
$sidenav-base-width: 225px;

//
// Navigation
//

// Z index variables
$zindex-content: 1037 !default;
$zindex-sidenav: 1038 !default;
$zindex-topnav: 1039 !default;

// Color variables for the sidenav

// -- Sidenav Dark
$sidenav-dark-bg: $gray-900;
$sidenav-dark-color: fade-out($white, 0.5);
$sidenav-dark-heading-color: fade-out($white, 0.75);
$sidenav-dark-link-color: fade-out($white, 0.5);
$sidenav-dark-link-active-color: $white;
$sidenav-dark-icon-color: fade-out($white, 0.75);
$sidenav-dark-footer-bg: $gray-800;

// -- Sidenav Light
$sidenav-light-bg: $gray-100;
$sidenav-light-color: $gray-900;
$sidenav-light-heading-color: $gray-500;
$sidenav-light-link-color: $sidenav-light-color;
$sidenav-light-link-active-color: $primary;
$sidenav-light-icon-color: $gray-500;
$sidenav-light-footer-bg: $gray-200;

// Color variables for the topnav
$topnav-dark-toggler-color: fade-out($white, 0.5);
$topnav-light-toggler-color: $gray-900;

// Import Bootstrap
@import "~bootstrap/scss/bootstrap.scss";

//
// Sidenav
//

// Base styling for the sidenav

.sb-sidenav {
  display: flex;
  flex-direction: column;
  height: 100%;
  flex-wrap: nowrap;

  .sb-sidenav-menu {
    flex-grow: 1;

    .nav {
      flex-direction: column;
      flex-wrap: nowrap;

      .sb-sidenav-menu-heading {
        padding: 1.75rem 1rem 0.75rem;
        font-size: 0.75rem;
        font-weight: bold;
        text-transform: uppercase;
      }

      .nav-link {
        display: flex;
        align-items: center;
        padding-top: 0.75rem;
        padding-bottom: 0.75rem;
        position: relative;

        .sb-nav-link-icon {
          font-size: 0.9rem;
        }

        .sb-sidenav-collapse-arrow {
          display: inline-block;
          margin-left: auto;
          transition: transform 0.15s ease;
        }

        &.collapsed {
          .sb-sidenav-collapse-arrow {
            transform: rotate(-90deg);
          }
        }
      }

      .sb-sidenav-menu-nested {
        margin-left: 1.5rem;
        flex-direction: column;
      }
    }
  }

  .sb-sidenav-footer {
    padding: 0.75rem;
    flex-shrink: 0;
  }
}

//
// Sidenav Dark
//

// Dark theme for sidenav
// Append .sb-sidenav-dark to .sb-sidenav to use

.sb-sidenav-dark {
  background-color: $sidenav-dark-bg;
  color: $sidenav-dark-color;

  .sb-sidenav-menu {
    .sb-sidenav-menu-heading {
      color: $sidenav-dark-heading-color;
    }

    .nav-link {
      color: $sidenav-dark-link-color;

      .sb-nav-link-icon {
        color: $sidenav-dark-icon-color;
      }

      .sb-sidenav-collapse-arrow {
        color: $sidenav-dark-icon-color;
      }

      &:hover {
        color: $sidenav-dark-link-active-color;
      }

      &.active {
        color: $sidenav-dark-link-active-color;

        .sb-nav-link-icon {
          color: $sidenav-dark-link-active-color;
        }
      }
    }
  }

  .sb-sidenav-footer {
    background-color: $sidenav-dark-footer-bg;
  }
}

</style>
