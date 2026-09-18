(function (window, $) {
    'use strict';

    var refreshTimer;
    var refreshing = false;
    var refreshBeforeMs = 60 * 1000;

    function decodePayload(token) {
        try {
            var payload = token.split('.')[1]
                .replace(/-/g, '+')
                .replace(/_/g, '/');
            return JSON.parse(window.atob(payload));
        } catch (e) {
            return null;
        }
    }

    function scheduleRefresh() {
        window.clearTimeout(refreshTimer);
        var token = window.localStorage.getItem('token');
        var refreshToken = window.localStorage.getItem('refreshToken');
        var payload = token && decodePayload(token);
        if (!token || !refreshToken || !payload || !payload.exp) return;

        var delay = payload.exp * 1000 - Date.now() - refreshBeforeMs;
        refreshTimer = window.setTimeout(refreshAccessToken, Math.max(delay, 5000));
    }

    function refreshAccessToken() {
        if (refreshing) return;
        var refreshToken = window.localStorage.getItem('refreshToken');
        if (!refreshToken) return;

        refreshing = true;
        $.ajax({
            url: (window.ipURL || window.location.origin) + '/luckyjourney/login/refresh',
            method: 'post',
            contentType: 'application/json',
            data: JSON.stringify({refreshToken: refreshToken}),
            global: false
        }).done(function (res) {
            if (res && res.success && res.data && res.data.token) {
                window.localStorage.setItem('token', res.data.token);
                if (res.data.refreshToken) {
                    window.localStorage.setItem('refreshToken', res.data.refreshToken);
                }
            }
        }).fail(function (xhr) {
            if (xhr.status === 401) {
                window.localStorage.removeItem('token');
                window.localStorage.removeItem('refreshToken');
            }
        }).always(function () {
            refreshing = false;
            scheduleRefresh();
        });
    }

    // Keep requests on the latest access token after a refresh.
    $.ajaxPrefilter(function (options) {
        if (!options.headers) options.headers = {};
        var token = window.localStorage.getItem('token');
        if (token) options.headers.token = token;
    });

    window.LuckyJourneyAuth = {
        refresh: refreshAccessToken,
        schedule: scheduleRefresh
    };

    $(scheduleRefresh);
})(window, window.jQuery);
