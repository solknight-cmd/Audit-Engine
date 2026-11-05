// Shared client for the dashboard pages: API calls, CSRF, the live log stream and display helpers.
// Anything that came from a document or the AI model is untrusted: render it with VaultApi.escapeHtml.
const VaultApi = (() => {

    function csrfToken() {
        const cookie = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
        return cookie ? decodeURIComponent(cookie.split('=')[1]) : '';
    }

    async function request(path, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers || {});
        if (method !== 'GET') {
            headers.set('X-XSRF-TOKEN', csrfToken());
        }

        const response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });

        if (response.status === 401) {
            // Session expired or never logged in
            window.location.href = '/Login.html';
            throw new Error('Not logged in');
        }

        const isJson = (response.headers.get('Content-Type') || '').includes('application/json');
        const body = isJson ? await response.json() : await response.text();
        if (!response.ok) {
            const message = (isJson && body && body.error) ? body.error : `Request failed (${response.status})`;
            throw new Error(message);
        }
        return body;
    }

    function me() {
        return request('/api/v1/me');
    }

    function vault() {
        return request('/api/v1/audits/vault');
    }

    function metrics() {
        return request('/api/v1/metrics');
    }

    function upload(file, documentType) {
        const form = new FormData();
        form.append('file', file);
        form.append('documentType', documentType);
        return request('/api/v1/audits/upload', { method: 'POST', body: form });
    }

    async function logout() {
        try {
            await request('/api/v1/auth/logout', { method: 'POST' });
        } finally {
            window.location.href = '/Login.html?logout=true';
        }
    }

    /** Live pipeline log for the user's company. Calls onMessage({time, message}) for each line. */
    function stream(onMessage) {
        const source = new EventSource('/api/v1/forensic-stream');
        source.addEventListener('forensic-log', event => {
            try {
                onMessage(JSON.parse(event.data));
            } catch (e) {
                onMessage({ time: '', message: event.data });
            }
        });
        return source;
    }

    /** The stored AuditReport for a vault record, or null for records without one. */
    function report(record) {
        try {
            const parsed = JSON.parse(record.aiAuditReport);
            return parsed && parsed.status ? parsed : null;
        } catch (e) {
            return null;
        }
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatAmount(value, figures) {
        if (value === null || value === undefined) {
            return 'Not verified';
        }
        const number = Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
        const currency = figures && figures.currency ? figures.currency + ' ' : '';
        const scale = figures && figures.scale && figures.scale !== 'units' ? ' ' + figures.scale : '';
        return currency + number + scale;
    }

    // Display names for the amounts each document type is checked on (see DocumentType.java)
    const AMOUNT_LABELS = {
        total_assets: 'Total assets', total_liabilities: 'Total liabilities', total_equity: 'Total equity',
        revenue: 'Revenue', cost_of_sales: 'Cost of sales', gross_profit: 'Gross profit',
        profit_before_tax: 'Profit before tax', income_tax: 'Income tax', net_profit: 'Net profit',
        net_cash_from_operating: 'Operating cash flow', net_cash_from_investing: 'Investing cash flow',
        net_cash_from_financing: 'Financing cash flow', net_change_in_cash: 'Net change in cash',
        opening_cash: 'Opening cash', closing_cash: 'Closing cash',
        total_debits: 'Total debits', total_credits: 'Total credits'
    };

    /** [{name, label, value}] for every amount in a report's figures, in the order the server stored them. */
    function amounts(report) {
        const figures = (report && report.figures) || {};
        return Object.entries(figures.amounts || {}).map(([name, value]) => ({
            name, label: AMOUNT_LABELS[name] || name, value
        }));
    }

    /** One amount from a report, or null. */
    function amount(report, name) {
        const figures = (report && report.figures) || {};
        const value = (figures.amounts || {})[name];
        return value === undefined ? null : value;
    }

    function formatDate(isoString) {
        return isoString ? new Date(isoString).toLocaleString() : '--';
    }

    // Display text and Tailwind classes for each verdict
    const STATUS_STYLES = {
        PASSED: { label: 'Passed', text: 'text-emerald-400', badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20', dot: 'bg-emerald-500' },
        HIGH_LEVERAGE_RISK: { label: 'High leverage', text: 'text-amber-400', badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20', dot: 'bg-amber-500' },
        UNABLE_TO_VERIFY: { label: 'Unable to verify', text: 'text-slate-300', badge: 'bg-slate-500/10 text-slate-300 border-slate-500/20', dot: 'bg-slate-400' },
        ANOMALY_DETECTED: { label: 'Anomaly detected', text: 'text-red-400', badge: 'bg-red-500/10 text-red-400 border-red-500/20', dot: 'bg-red-500 pulse-anomaly' },
        NONE: { label: 'No audits yet', text: 'text-slate-400', badge: 'bg-slate-500/10 text-slate-400 border-slate-500/20', dot: 'bg-slate-500' }
    };

    function statusStyle(status) {
        return STATUS_STYLES[status] || { ...STATUS_STYLES.UNABLE_TO_VERIFY, label: status };
    }

    function statusBadge(status) {
        const style = statusStyle(status);
        return `<span class="inline-flex items-center gap-2 px-2 py-0.5 rounded border font-mono font-bold text-[11px] ${style.badge}">`
            + `<span class="w-2 h-2 rounded-full ${style.dot}"></span>${escapeHtml(style.label)}</span>`;
    }

    /** Fills the sidebar's user block (elements with these ids, when the page has them). */
    async function showCurrentUser() {
        try {
            const user = await me();
            const initials = (user.fullName || user.email).split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
            const set = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };
            set('currentUserInitials', initials);
            set('currentUserName', user.fullName || user.email);
            set('currentCompanyName', user.companyName);
            return user;
        } catch (e) {
            return null;
        }
    }

    return {
        request, me, vault, metrics, upload, logout, stream, report, amounts, amount,
        escapeHtml, formatAmount, formatDate, statusStyle, statusBadge, showCurrentUser
    };
})();
