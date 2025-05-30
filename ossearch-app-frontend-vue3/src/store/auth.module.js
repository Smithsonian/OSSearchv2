import AuthService from '../services/auth.service';
import router from '../router';

const user = JSON.parse(localStorage.getItem('user'));
const initialState = user ?
    {status: {loggedIn: true}, user} :
    {status: {loggedIn: false}, user: null};

export const auth = {
    namespaced: true,
    state: initialState,
    actions: {
        login({commit}, user) {
            return AuthService.login(user)
                .then(
                    user => {
                        commit('loginSuccess', user);
                        const redirectPath = sessionStorage.getItem('redirectPath') || '/';
                        sessionStorage.removeItem('redirectPath');
                        router.push(redirectPath);
                        return Promise.resolve(user);
                    },
                    error => {
                        commit('loginFailure');
                        return Promise.reject(error);
                    }
                );
        },
        logout({commit}) {
            AuthService.logout();
            commit('logout');
            const currentPath = router.currentRoute.value.path;
            if (currentPath !== '/login') {
                sessionStorage.setItem('redirectPath', currentPath);
            }
            router.push('/login');
        },
        register({commit}, user) {
            return AuthService.register(user)
                .then(
                    response => {
                        commit('registerSuccess');
                        return Promise.resolve(response.data);
                    },
                    error => {
                        commit('registerFailure');
                        return Promise.reject(error);
                    }
                );
        },
        refreshToken({commit}, accessToken) {
            commit('refreshToken', accessToken);
        }
    },
    mutations: {
        loginSuccess(state, user) {
            state.status.loggedIn = true;
            state.user = user;
        },
        loginFailure(state) {
            state.status.loggedIn = false;
            state.user = null;
        },
        logout(state) {
            state.status.loggedIn = false;
            state.user = null;
        },
        registerSuccess(state) {
            state.status.loggedIn = false;
        },
        registerFailure(state) {
            state.status.loggedIn = false;
        },
        refreshToken(state, accessToken) {
            state.status.loggedIn = true;
            state.user = {...state.user, accessToken: accessToken};
        },
        updateUsername(state, username) {
            state.user.username = username;
        }
    }
};
