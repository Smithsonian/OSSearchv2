import axiosInstance from "./api";
import TokenService from "./token.service";
import EventBus from "../common/EventBus";
import apiErrorToast from "../common/apiErrorToast";
import router from '../router';

const setup = (store) => {
    axiosInstance.interceptors.request.use(
        (config) => {
            const token = TokenService.getLocalAccessToken();
            if (token) {
                config.headers["Authorization"] = 'Bearer ' + token;  // for Spring Boot back-end
                // config.headers["x-access-token"] = token; // for Node.js Express back-end
            }
            // change the url scheme from http to https
            config.url = config.url.replace('http://127.0.0.1:8484', process.env.VUE_APP_API_BASE_URL)
            return config;
        },
        (error) => {
            return Promise.reject(error);
        }
    );

    axiosInstance.interceptors.response.use(
        (res) => {
            return res;
        },
        async (err) => {
            const originalConfig = err.config;

            if (err.response?.status === 401 && !originalConfig._retry) {
                originalConfig._retry = true;

                try {
                    const rs = await axiosInstance.post("/auth/refreshtoken", {refreshToken: TokenService.getLocalRefreshToken()});
                    const {accessToken} = rs.data;
                    store.dispatch('auth/refreshToken', accessToken);
                    TokenService.updateLocalAccessToken(accessToken);
                    return axiosInstance(originalConfig);
                } catch (_error) {
                    // Store current path before logout
                    const currentPath = router.currentRoute.value.path;
                    if (currentPath !== '/login') {
                        console.log('Storing redirect path:', currentPath);
                        sessionStorage.setItem('redirectPath', currentPath);
                    }

                    // Dispatch logout event
                    EventBus.dispatch("logout");
                    return Promise.reject(_error);
                }
            }

            // Global user-facing notification for failed requests (403,
            // network, 5xx, ...). Components no longer alert/logout in
            // per-component error watchers.
            apiErrorToast(err);
            return Promise.reject(err);
        }
    );
};

export default setup;