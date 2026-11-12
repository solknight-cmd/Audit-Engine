// Shared by team.html (a company admin's own company) and admin.html (the operator, any company):
// the user table, the invite form and the box that shows a new one-time link.
// Needs vault-api.js. All text from the server is escaped before it goes into the page.
const Accounts = (() => {
    const esc = value => VaultApi.escapeHtml(value);

    const ROLE_LABELS = {
        ROLE_AUDITOR: 'Auditor',
        ROLE_COMPANY_ADMIN: 'Company admin',
        ROLE_PLATFORM_ADMIN: 'Operator'
    };
    const STATUS_STYLES = {
        ACTIVE: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
        INVITED: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
        DISABLED: 'bg-slate-500/10 text-slate-400 border-slate-500/20'
    };
    const STATUS_LABELS = { ACTIVE: 'Active', INVITED: 'Invited', DISABLED: 'Disabled' };

    /**
     * Renders users into a <tbody>. apiBase is "/api/v1/team" or "/api/v1/admin/companies/{id}";
     * onChange is called after an action so the page can reload the list.
     */
    function renderUsers(tbody, users, apiBase, currentEmail, onChange) {
        if (users.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" class="p-6 text-center text-slate-500 font-mono text-xs">No users yet.</td></tr>`;
            return;
        }
        tbody.innerHTML = '';
        users.forEach(user => {
            const isMe = user.email.toLowerCase() === (currentEmail || '').toLowerCase();
            const row = document.createElement('tr');
            row.className = 'border-b border-white/5 hover:bg-white/5 transition-colors';
            row.innerHTML = `
                <td class="p-4"><div class="font-bold text-white">${esc(user.fullName)}${isMe ? ' <span class="text-slate-500 font-normal">(you)</span>' : ''}</div>
                    <div class="text-slate-400 font-mono text-[11px]">${esc(user.email)}</div></td>
                <td class="p-4 text-slate-300">${esc(ROLE_LABELS[user.role] || user.role)}</td>
                <td class="p-4"><span class="px-2 py-0.5 rounded border font-mono text-[11px] ${STATUS_STYLES[user.status] || ''}">${esc(STATUS_LABELS[user.status] || user.status)}</span></td>
                <td class="p-4 text-slate-400 font-mono text-[11px]">${user.lastLoginAt ? esc(VaultApi.formatDate(user.lastLoginAt)) : 'Never'}</td>
                <td class="p-4 text-right whitespace-nowrap space-x-3"></td>`;
            const actions = row.lastElementChild;
            if (!isMe) {
                if (user.status !== 'DISABLED') {
                    actions.append(actionButton(user.status === 'INVITED' ? 'New invite link' : 'Password reset link', async () => {
                        showLink(await VaultApi.request(`${apiBase}/users/${user.id}/reset-link`, { method: 'POST' }));
                        onChange();
                    }));
                    actions.append(actionButton('Disable', async () => {
                        if (!confirm(`Disable ${user.email}? They'll be signed out and can't sign in until enabled again.`)) return;
                        await VaultApi.request(`${apiBase}/users/${user.id}/disable`, { method: 'POST' });
                        onChange();
                    }, 'text-red-400'));
                } else {
                    actions.append(actionButton('Enable', async () => {
                        await VaultApi.request(`${apiBase}/users/${user.id}/enable`, { method: 'POST' });
                        onChange();
                    }));
                }
            }
            tbody.appendChild(row);
        });
    }

    function actionButton(label, handler, colour = 'text-[#D4AF37]') {
        const button = document.createElement('button');
        button.className = `${colour} text-xs font-bold hover:underline`;
        button.textContent = label;
        button.addEventListener('click', async () => {
            button.disabled = true;
            try {
                await handler();
            } catch (e) {
                alert(e.message);
            } finally {
                button.disabled = false;
            }
        });
        return button;
    }

    /**
     * Wires an invite form (fields named email, fullName, role) to POST {apiBase}/users.
     * apiBase can be a function, for pages where the target company changes.
     */
    function wireInviteForm(form, apiBase, onDone) {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const error = form.querySelector('[data-error]');
            error.textContent = '';
            const base = typeof apiBase === 'function' ? apiBase() : apiBase;
            try {
                const result = await VaultApi.request(`${base}/users`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        email: form.email.value, fullName: form.fullName.value, role: form.role.value
                    })
                });
                form.reset();
                showLink(result);
                onDone();
            } catch (e) {
                error.textContent = e.message;
            }
        });
    }

    /** Shows a new one-time link with Copy and Email buttons. It can't be looked up again later. */
    function showLink(result) {
        const isInvite = result.purpose === 'INVITE';
        const subject = isInvite ? 'Your Vault Core account' : 'Reset your Vault Core password';
        const body = (isInvite
            ? 'You have been invited to Vault Core. Open this link to choose your password:'
            : 'Open this link to choose a new Vault Core password:')
            + `\n\n${result.link}\n\nThe link works once and expires ${VaultApi.formatDate(result.expiresAt)}.`;

        let dialog = document.getElementById('linkDialog');
        if (!dialog) {
            dialog = document.createElement('div');
            dialog.id = 'linkDialog';
            dialog.className = 'fixed inset-0 z-50 bg-[#060913]/80 backdrop-blur-sm flex items-center justify-center p-4';
            document.body.appendChild(dialog);
        }
        dialog.innerHTML = `
            <div class="w-full max-w-lg bg-[#111827] border border-white/10 rounded-xl p-6 space-y-4 shadow-2xl">
                <h3 class="text-sm font-bold text-white">${isInvite ? 'Invitation link' : 'Password reset link'} for ${esc(result.email)}</h3>
                <p class="text-xs text-slate-400">Send this link to them yourself. It works once and expires ${esc(VaultApi.formatDate(result.expiresAt))}.
                   It won't be shown again; create a new one if it's lost.</p>
                <input readonly class="w-full bg-[#0b0f19] border border-white/10 rounded p-2 text-[11px] font-mono text-slate-200" value="${esc(result.link)}">
                <div class="flex gap-2 justify-end">
                    <button data-copy class="px-3 py-1.5 bg-[#D4AF37] text-slate-950 text-xs font-bold rounded">Copy link</button>
                    <a data-mail class="px-3 py-1.5 bg-white/5 border border-white/10 text-slate-200 text-xs font-bold rounded">Open email</a>
                    <button data-close class="px-3 py-1.5 text-slate-400 text-xs">Done</button>
                </div>
            </div>`;
        dialog.querySelector('[data-mail]').href =
            `mailto:${encodeURIComponent(result.email)}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
        dialog.querySelector('[data-copy]').addEventListener('click', async event => {
            const input = dialog.querySelector('input');
            input.select();
            try { await navigator.clipboard.writeText(result.link); } catch (e) { document.execCommand('copy'); }
            event.target.textContent = 'Copied';
        });
        dialog.querySelector('[data-close]').addEventListener('click', () => dialog.remove());
    }

    return { renderUsers, wireInviteForm, showLink, ROLE_LABELS };
})();
