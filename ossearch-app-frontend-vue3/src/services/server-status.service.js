import api from "./api"

class ServerStatusService {
    getServerStatus() {
        // Authenticated /api mirror of /actuator/health — the WAF blocks
        // /actuator/** when the UI is accessed through the load balancer.
        return api.get('/utils/health', {
            validateStatus: function (status) {
                return (status >= 200 && status < 300) || status === 503;
            }
        })
    }
    getSchedulerStatus() {
        return api.get('/scheduler/metaData', {
            validateStatus: function (status) {
                return (status >= 200 && status < 300) || status === 503;
            }
        })
    }
    getSolr() {
        return api.get('/utils/solr/count', {
            params: {all: false},
            validateStatus: function (status) {
                return (status >= 200 && status < 300) || status === 503;
            }
        })
    }
    getSolrCollectionCounts() {
        return api.get('/utils/solr/collection_counts', {
            validateStatus: function (status) {
                return (status >= 200 && status < 300) || status === 503;
            }
        })
    }
    getCrawlLogStats() {
        return api.get('/crawllog/search/getLatestCrawlLogStats', {
            params: {projection: 'crawlLogLatestStats'},
            validateStatus: function (status) {
                return (status >= 200 && status < 300) || status === 503;
            }
        })
    }
}

export default new ServerStatusService();