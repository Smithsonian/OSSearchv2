import EventBus from "./EventBus";

// One global place that turns failed API responses into user-facing toasts.
// Wired into the axios response interceptor so individual components no
// longer need per-component error watchers (the old copy-pasted watchers
// treated any 403 as an invalid session and force-logged the user out).
const recent = new Map();
const DEDUPE_WINDOW_MS = 5000;

export default function apiErrorToast(err) {
    const status = err.response?.status;

    // 401s belong to the token-refresh flow in the interceptor, and /auth/*
    // failures (signin, refreshtoken, logout) surface through the auth UI.
    if (status === 401) return;
    if (err.config?.url && String(err.config.url).includes('/auth/')) return;

    let type = 'danger';
    let msg;
    if (!err.response) {
        msg = 'Cannot reach the backend server. Check your connection or try again shortly.';
    } else if (status === 403) {
        const data = err.response.data;
        const serverMsg = (typeof data === 'object' && data !== null && data.message) ? data.message : null;
        // A 403 means the session is valid but the action is not allowed —
        // possibly blocked by the WAF in front of the load balancer. It must
        // never be treated as a reason to log the user out.
        msg = serverMsg
            ? 'Access denied: ' + serverMsg
            : 'Request blocked (403): you may not have permission for this action, or a security policy blocked the request.';
        type = 'warning';
    } else {
        const data = err.response.data;
        const serverMsg = (typeof data === 'object' && data !== null && data.message) ? data.message : err.message;
        msg = 'ERROR: ' + (serverMsg || ('Request failed with status ' + status));
    }

    // Parallel requests failing together (e.g. a dashboard load against a
    // down backend) should produce one toast, not a popup storm.
    const now = Date.now();
    const last = recent.get(msg);
    if (last && now - last < DEDUPE_WINDOW_MS) return;
    recent.set(msg, now);
    if (recent.size > 50) {
        for (const [key, t] of recent) {
            if (now - t > DEDUPE_WINDOW_MS) recent.delete(key);
        }
    }

    EventBus.dispatch('toast', {type, msg});
}
