/**
 * FlagShip Admin Console Interactive Controller (FR1-FR10, QA5)
 */
document.addEventListener('DOMContentLoaded', () => {
    // State
    const state = {
        projectKey: 'default',
        environmentKey: 'production',
        apiKey: 'fs_dev_bootstrap_key',
        serverUrl: 'http://localhost:8080',
        activeTab: 'flags',
        filter: 'all',
        searchQuery: '',
        flags: [],
        segments: [],
        approvals: [],
        history: [],
        experiments: [],
        staleFlags: []
    };

    // DOM Elements
    const projectSelect = document.getElementById('projectSelect');
    const envSelect = document.getElementById('envSelect');
    const apiKeyInput = document.getElementById('apiKeyInput');
    const flagsTableBody = document.getElementById('flagsTableBody');
    const flagSearch = document.getElementById('flagSearch');
    const chips = document.querySelectorAll('.chip');
    const navItems = document.querySelectorAll('.nav-item');
    const tabPanes = document.querySelectorAll('.tab-pane');
    const syncStatus = document.getElementById('syncStatus');
    const serverUrlLabel = document.getElementById('serverUrlLabel');

    // Drawer & Modals
    const evalDrawer = document.getElementById('evalDrawer');
    const btnLiveTestDrawer = document.getElementById('btnLiveTestDrawer');
    const closeDrawer = document.getElementById('closeDrawer');
    const createFlagModal = document.getElementById('createFlagModal');
    const btnNewFlagModal = document.getElementById('btnNewFlagModal');
    const closeCreateModal = document.getElementById('closeCreateModal');
    const btnCancelCreate = document.getElementById('btnCancelCreate');
    const btnSubmitCreate = document.getElementById('btnSubmitCreate');
    const btnExecuteEval = document.getElementById('btnExecuteEval');
    const evalResultBox = document.getElementById('evalResultBox');

    // Initialize
    init();

    async function init() {
        bindEvents();
        await detectServerUrl();
        await refreshDashboard();
    }

    async function detectServerUrl() {
        try {
            const resp = await fetch('/admin/api/config');
            if (resp.ok) {
                const data = await resp.json();
                if (data.serverUrl) {
                    state.serverUrl = data.serverUrl;
                    serverUrlLabel.textContent = state.serverUrl;
                }
            }
        } catch (e) {
            console.warn('Could not auto-detect server URL from backend, using default', e);
        }
    }

    function bindEvents() {
        // Tab switching
        navItems.forEach(item => {
            item.addEventListener('click', () => {
                const tab = item.dataset.tab;
                switchTab(tab);
            });
        });

        // Project / Env change
        projectSelect.addEventListener('change', (e) => {
            state.projectKey = e.target.value;
            refreshDashboard();
        });

        envSelect.addEventListener('change', (e) => {
            state.environmentKey = e.target.value;
            refreshDashboard();
        });

        apiKeyInput.addEventListener('change', (e) => {
            state.apiKey = e.target.value;
            refreshDashboard();
        });

        // Search & Filter
        flagSearch.addEventListener('input', (e) => {
            state.searchQuery = e.target.value.toLowerCase();
            renderFlags();
        });

        chips.forEach(chip => {
            chip.addEventListener('click', () => {
                chips.forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                state.filter = chip.dataset.filter;
                renderFlags();
            });
        });

        // Drawer
        btnLiveTestDrawer.addEventListener('click', () => {
            populateEvalFlagSelect();
            evalDrawer.classList.add('open');
        });
        closeDrawer.addEventListener('click', () => evalDrawer.classList.remove('open'));

        // Modal
        btnNewFlagModal.addEventListener('click', () => createFlagModal.classList.add('open'));
        closeCreateModal.addEventListener('click', () => createFlagModal.classList.remove('open'));
        btnCancelCreate.addEventListener('click', () => createFlagModal.classList.remove('open'));
        btnSubmitCreate.addEventListener('click', handleCreateFlag);

        // Inline evaluation execution
        btnExecuteEval.addEventListener('click', handleExecuteEval);
    }

    function switchTab(tabName) {
        state.activeTab = tabName;
        navItems.forEach(i => i.classList.toggle('active', i.dataset.tab === tabName));
        tabPanes.forEach(p => p.classList.toggle('active', p.id === `tab-${tabName}`));
    }

    async function refreshDashboard() {
        showSyncBadge('loading', 'Syncing...');
        try {
            await Promise.all([
                loadSnapshot(),
                loadApprovals(),
                loadHistory(),
                loadExperiments(),
                loadStaleFlags()
            ]);
            showSyncBadge('live', 'Live Sync');
        } catch (err) {
            console.warn('Backend offline or error, falling back to simulated data', err);
            loadSimulatedData();
            showSyncBadge('offline', 'Demo Mode');
        }
    }

    function showSyncBadge(type, text) {
        const dot = syncStatus.querySelector('.sync-dot');
        const txt = syncStatus.querySelector('.sync-text');
        txt.textContent = text;
        dot.className = `sync-dot ${type}`;
    }

    async function apiFetch(endpoint, options = {}) {
        const url = `${state.serverUrl}/api/v1/projects/${state.projectKey}/environments/${state.environmentKey}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
            'X-API-Key': state.apiKey,
            ...(options.headers || {})
        };
        return fetch(url, { ...options, headers });
    }

    async function loadSnapshot() {
        const resp = await apiFetch('/snapshot');
        if (!resp.ok) throw new Error(`Snapshot failed: ${resp.status}`);
        const data = await resp.json();
        state.flags = Object.values(data.flags || {});
        state.segments = Object.values(data.segments || {});
        renderFlags();
        renderSegments();
    }

    async function loadApprovals() {
        try {
            const resp = await apiFetch('/change-control/requests');
            if (resp.ok) {
                state.approvals = await resp.json();
            } else {
                state.approvals = [];
            }
        } catch (e) {
            state.approvals = [];
        }
        renderApprovals();
    }

    async function loadHistory() {
        try {
            const resp = await apiFetch('/change-control/audit');
            if (resp.ok) {
                state.history = await resp.json();
            } else {
                state.history = [];
            }
        } catch (e) {
            state.history = [];
        }
        renderHistory();
    }

    async function loadExperiments() {
        try {
            const resp = await apiFetch('/experiments');
            if (resp.ok) {
                state.experiments = await resp.json();
            } else {
                state.experiments = [];
            }
        } catch (e) {
            state.experiments = [];
        }
        renderExperiments();
    }

    async function loadStaleFlags() {
        try {
            const resp = await apiFetch('/flags/stale');
            if (resp.ok) {
                state.staleFlags = await resp.json();
            } else {
                state.staleFlags = [];
            }
        } catch (e) {
            state.staleFlags = [];
        }
        renderStaleFlags();
    }

    function loadSimulatedData() {
        state.flags = [
            {
                key: 'checkout-v2',
                name: 'Instant Checkout V2',
                type: 'BOOLEAN',
                enabled: true,
                killSwitch: false,
                rolloutPercentage: 50.0,
                version: 4,
                owner: 'sani@flagship.io',
                stale: false
            },
            {
                key: 'recommendation-engine-algo',
                name: 'ML Recommendation Model',
                type: 'STRING',
                enabled: true,
                killSwitch: false,
                rolloutPercentage: 20.0,
                version: 2,
                owner: 'ml-team@flagship.io',
                stale: false
            },
            {
                key: 'payment-gateway-stripe',
                name: 'Emergency Stripe Killswitch',
                type: 'BOOLEAN',
                enabled: false,
                killSwitch: true,
                rolloutPercentage: 0.0,
                version: 7,
                owner: 'fintech-team@flagship.io',
                stale: false
            },
            {
                key: 'legacy-coupon-banner',
                name: 'Legacy Black Friday Banner',
                type: 'BOOLEAN',
                enabled: true,
                killSwitch: false,
                rolloutPercentage: 100.0,
                version: 1,
                owner: 'growth@flagship.io',
                stale: true
            }
        ];

        state.segments = [
            {
                key: 'enterprise-beta-users',
                name: 'Enterprise Beta Users',
                description: 'Users on enterprise tier with beta opt-in',
                includedUsers: ['corp-1', 'corp-2', 'corp-3']
            },
            {
                key: 'eu-gdpr-audience',
                name: 'EU GDPR Jurisdiction',
                description: 'Users located in EU geographic boundary',
                includedUsers: []
            }
        ];

        state.approvals = [
            {
                id: 'req-prod-101',
                flagKey: 'checkout-v2',
                author: 'developer-bob',
                reason: 'Increase rollout from 20% to 50% for peak sale',
                requestedAt: new Date(Date.now() - 3600000).toISOString(),
                status: 'PENDING',
                diff: 'rolloutPercentage: 20.0% -> 50.0%'
            }
        ];

        state.history = [
            {
                id: 'audit-004',
                flagKey: 'checkout-v2',
                version: 4,
                author: 'alice@flagship.io',
                action: 'UPDATE_FLAG',
                reason: 'Rollout incremented to 50%',
                timestamp: new Date(Date.now() - 1800000).toISOString()
            },
            {
                id: 'audit-003',
                flagKey: 'payment-gateway-stripe',
                version: 7,
                author: 'ops-lead',
                action: 'TRIGGER_KILL_SWITCH',
                reason: 'Upstream gateway outage mitigation',
                timestamp: new Date(Date.now() - 7200000).toISOString()
            }
        ];

        state.experiments = [
            {
                experimentKey: 'checkout-v2-experiment',
                flagKey: 'checkout-v2',
                metricName: 'purchase_completed',
                variations: [
                    { key: 'control', exposures: 50200, conversions: 2510, conversionRate: 0.050 },
                    { key: 'variant-v2', exposures: 49800, conversions: 2888, conversionRate: 0.058 }
                ],
                srmChiSquare: 1.60,
                srmPValue: 0.206,
                srmDetected: false,
                zScore: 5.62,
                zTestPValue: 0.00001,
                statisticallySignificant: true
            }
        ];

        state.staleFlags = [
            {
                key: 'legacy-coupon-banner',
                owner: 'growth@flagship.io',
                reason: '100% rolled out for > 14 days without configuration mutations',
                lastEvaluatedAt: new Date(Date.now() - 15 * 86400000).toISOString(),
                fullRolloutSince: new Date(Date.now() - 22 * 86400000).toISOString()
            },
            {
                key: 'unused-search-v1',
                owner: 'search-team@flagship.io',
                reason: 'No evaluations recorded across clients in past 30 days',
                lastEvaluatedAt: new Date(Date.now() - 35 * 86400000).toISOString(),
                fullRolloutSince: null
            }
        ];

        renderFlags();
        renderSegments();
        renderApprovals();
        renderHistory();
        renderExperiments();
        renderStaleFlags();
    }

    function renderFlags() {
        const query = state.searchQuery;
        const filter = state.filter;

        const filtered = state.flags.filter(f => {
            const matchesQuery = !query ||
                f.key.toLowerCase().includes(query) ||
                (f.name && f.name.toLowerCase().includes(query)) ||
                (f.owner && f.owner.toLowerCase().includes(query));

            if (!matchesQuery) return false;

            if (filter === 'active') return f.enabled && !f.killSwitch;
            if (filter === 'killswitch') return f.killSwitch;
            if (filter === 'stale') return f.stale;
            return true;
        });

        flagsTableBody.innerHTML = filtered.map(f => {
            const isKillSwitched = f.killSwitch;
            const statusBadge = isKillSwitched
                ? '<span class="badge badge-danger">KILL SWITCHED</span>'
                : (f.enabled ? '<span class="badge badge-success">ACTIVE</span>' : '<span class="badge badge-warning">DISABLED</span>');

            const rolloutPct = f.rolloutPercentage || 0;

            return `
                <tr>
                    <td>
                        <strong>${escapeHtml(f.name || f.key)}</strong>
                        <br><code style="color:var(--text-muted);font-size:0.75rem">${escapeHtml(f.key)}</code>
                    </td>
                    <td><span class="badge badge-type">${f.type}</span></td>
                    <td>${statusBadge}</td>
                    <td>
                        ${rolloutPct}%
                        <div class="progress-container">
                            <div class="progress-bar" style="width: ${rolloutPct}%;"></div>
                        </div>
                    </td>
                    <td>
                        <button class="killswitch-btn ${isKillSwitched ? 'armed' : 'disarmed'}" 
                                onclick="window.toggleKillSwitch('${escapeHtml(f.key)}', ${!isKillSwitched})">
                            ${isKillSwitched ? '🚨 DISARM KILL SWITCH' : '⚡ ARM KILL SWITCH'}
                        </button>
                    </td>
                    <td><span style="font-family:var(--font-mono)">v${f.version || 1}</span></td>
                    <td><small style="color:var(--text-secondary)">${escapeHtml(f.owner || 'unassigned')}</small></td>
                    <td>
                        <button class="btn btn-secondary btn-sm" onclick="window.openEvalDrawer('${escapeHtml(f.key)}')">Test</button>
                    </td>
                </tr>
            `;
        }).join('');
    }

    function renderSegments() {
        const container = document.getElementById('segmentsGrid');
        container.innerHTML = state.segments.map(s => `
            <div class="card">
                <div class="card-header">
                    <h4>${escapeHtml(s.name || s.key)}</h4>
                    <span class="badge badge-type">${escapeHtml(s.key)}</span>
                </div>
                <p style="color:var(--text-secondary);font-size:0.85rem;margin-bottom:12px;">
                    ${escapeHtml(s.description || 'No description provided')}
                </p>
                <div class="stat-item">
                    <span class="val">${(s.includedUsers || []).length}</span>
                    <span class="lbl">Explicit Whitelisted Users</span>
                </div>
            </div>
        `).join('');
    }

    function renderApprovals() {
        const container = document.getElementById('approvalsList');
        const countBadge = document.getElementById('pendingApprovalsCount');
        countBadge.textContent = state.approvals.length;

        if (state.approvals.length === 0) {
            container.innerHTML = `
                <div class="card" style="text-align:center;color:var(--text-muted);padding:40px;">
                    🛡️ No pending approvals for environment <strong>${escapeHtml(state.environmentKey)}</strong>. All production changes are peer-reviewed and signed.
                </div>
            `;
            return;
        }

        container.innerHTML = state.approvals.map(req => `
            <div class="card" style="margin-bottom:16px;">
                <div class="card-header">
                    <div>
                        <h4>Approval Request for: <code>${escapeHtml(req.flagKey)}</code></h4>
                        <small style="color:var(--text-muted)">Requested by ${escapeHtml(req.author)} on ${new Date(req.requestedAt).toLocaleString()}</small>
                    </div>
                    <span class="badge badge-warning">Requires 2nd Approver</span>
                </div>
                <div style="background:var(--bg-main);padding:12px;border-radius:var(--radius-sm);margin:12px 0;">
                    <p style="font-size:0.85rem;margin-bottom:6px;"><strong>Reason:</strong> ${escapeHtml(req.reason)}</p>
                    <code style="font-size:0.75rem;color:#93c5fd;">${escapeHtml(req.diff || 'Configuration mutation')}</code>
                </div>
                <div style="display:flex;justify-content:flex-end;gap:10px;">
                    <button class="btn btn-secondary btn-sm" onclick="window.rejectApproval('${escapeHtml(req.id)}')">Reject</button>
                    <button class="btn btn-primary btn-sm" onclick="window.approveRequest('${escapeHtml(req.id)}')">Approve &amp; Apply</button>
                </div>
            </div>
        `).join('');
    }

    function renderHistory() {
        const timeline = document.getElementById('historyTimeline');
        timeline.innerHTML = state.history.map(item => `
            <div class="timeline-item">
                <div class="timeline-dot"></div>
                <div class="timeline-card">
                    <div style="display:flex;justify-content:space-between;align-items:flex-start;">
                        <div>
                            <strong>${escapeHtml(item.action || 'MUTATION')}</strong> on <code>${escapeHtml(item.flagKey)}</code> (v${item.version})
                            <div style="font-size:0.75rem;color:var(--text-muted);margin-top:2px;">
                                By ${escapeHtml(item.author || 'system')} &bull; ${new Date(item.timestamp).toLocaleString()}
                            </div>
                        </div>
                        <button class="btn btn-secondary btn-sm" onclick="window.rollbackToVersion('${escapeHtml(item.flagKey)}', ${item.version})">
                            ↺ Rollback to v${item.version}
                        </button>
                    </div>
                    <div style="font-size:0.85rem;color:var(--text-secondary);margin-top:8px;">
                        ${escapeHtml(item.reason || 'Flag configuration update')}
                    </div>
                </div>
            </div>
        `).join('');
    }

    function renderExperiments() {
        const container = document.getElementById('experimentsContainer');
        container.innerHTML = state.experiments.map(exp => {
            const isSrm = exp.srmDetected;
            const isSignificant = exp.statisticallySignificant;

            return `
                <div class="card">
                    <div class="card-header">
                        <h4>${escapeHtml(exp.experimentKey || exp.flagKey)}</h4>
                        <span class="badge ${isSignificant ? 'badge-success' : 'badge-warning'}">
                            ${isSignificant ? '★ Statistically Significant' : 'Collecting Data'}
                        </span>
                    </div>

                    <div style="font-size:0.8rem;color:var(--text-muted);margin-bottom:12px;">
                        Primary Goal Metric: <code>${escapeHtml(exp.metricName || 'conversion')}</code>
                    </div>

                    <table class="data-table" style="margin-bottom:16px;">
                        <thead>
                            <tr>
                                <th>Variation</th>
                                <th>Exposures</th>
                                <th>Conversions</th>
                                <th>Rate</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${(exp.variations || []).map(v => `
                                <tr>
                                    <td><strong>${escapeHtml(v.key)}</strong></td>
                                    <td>${v.exposures.toLocaleString()}</td>
                                    <td>${v.conversions.toLocaleString()}</td>
                                    <td><strong>${(v.conversionRate * 100).toFixed(2)}%</strong></td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>

                    <div class="stat-grid">
                        <div class="stat-item">
                            <span class="val" style="color:${isSrm ? 'var(--danger)' : 'var(--success)'}">
                                ${isSrm ? 'MISMATCH' : 'PASSED'}
                            </span>
                            <span class="lbl">SRM Chi-Square (p = ${exp.srmPValue?.toFixed(4)})</span>
                        </div>
                        <div class="stat-item">
                            <span class="val" style="color:${isSignificant ? 'var(--success)' : 'var(--text-primary)'}">
                                z = ${exp.zScore?.toFixed(2)}
                            </span>
                            <span class="lbl">Two-Proportion Z-Test</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    }

    function renderStaleFlags() {
        const tbody = document.getElementById('staleTableBody');
        const countSpan = document.getElementById('staleFlagsCount');
        const summaryCount = document.getElementById('staleSummaryCount');

        countSpan.textContent = state.staleFlags.length;
        summaryCount.textContent = state.staleFlags.length;

        tbody.innerHTML = state.staleFlags.map(sf => `
            <tr>
                <td><code>${escapeHtml(sf.key)}</code></td>
                <td><small>${escapeHtml(sf.owner || 'unassigned')}</small></td>
                <td><span class="badge badge-danger">${escapeHtml(sf.reason)}</span></td>
                <td><small>${sf.lastEvaluatedAt ? new Date(sf.lastEvaluatedAt).toLocaleDateString() : 'Never'}</small></td>
                <td><small>${sf.fullRolloutSince ? new Date(sf.fullRolloutSince).toLocaleDateString() : 'N/A'}</small></td>
                <td>
                    <button class="btn btn-danger btn-sm" onclick="window.deprecateFlag('${escapeHtml(sf.key)}')">Mark Cleaned</button>
                </td>
            </tr>
        `).join('');
    }

    // Interactive Actions
    window.toggleKillSwitch = async (flagKey, enableKillSwitch) => {
        try {
            const resp = await apiFetch(`/flags/${flagKey}/kill-switch?enabled=${enableKillSwitch}`, {
                method: 'POST'
            });
            if (resp.ok) {
                alert(`Kill switch for '${flagKey}' has been ${enableKillSwitch ? 'ARMED' : 'DISARMED'}. Changes propagated within 5s.`);
                refreshDashboard();
            } else {
                throw new Error(`Server returned HTTP ${resp.status}`);
            }
        } catch (e) {
            // Simulated toggle
            const flag = state.flags.find(f => f.key === flagKey);
            if (flag) {
                flag.killSwitch = enableKillSwitch;
                renderFlags();
                alert(`[DEMO] Kill switch for '${flagKey}' updated to: ${enableKillSwitch}`);
            }
        }
    };

    window.approveRequest = async (requestId) => {
        const approver = prompt('Enter your approver username (Two-Person rule):', 'senior-architect');
        if (!approver) return;

        try {
            const resp = await apiFetch(`/change-control/requests/${requestId}/approve?approver=${encodeURIComponent(approver)}`, {
                method: 'POST'
            });
            if (resp.ok) {
                alert('Change request approved and atomically applied to production!');
                refreshDashboard();
            } else {
                throw new Error(`HTTP ${resp.status}`);
            }
        } catch (e) {
            state.approvals = state.approvals.filter(a => a.id !== requestId);
            renderApprovals();
            alert(`[DEMO] Request ${requestId} approved by ${approver}.`);
        }
    };

    window.rejectApproval = (requestId) => {
        state.approvals = state.approvals.filter(a => a.id !== requestId);
        renderApprovals();
        alert(`Request ${requestId} rejected.`);
    };

    window.rollbackToVersion = async (flagKey, targetVersion) => {
        if (!confirm(`Are you sure you want to rollback '${flagKey}' to version ${targetVersion}?`)) return;

        try {
            const resp = await apiFetch(`/change-control/flags/${flagKey}/rollback?targetVersion=${targetVersion}`, {
                method: 'POST'
            });
            if (resp.ok) {
                alert(`Successfully rolled back '${flagKey}' to version ${targetVersion}!`);
                refreshDashboard();
            } else {
                throw new Error(`HTTP ${resp.status}`);
            }
        } catch (e) {
            alert(`[DEMO] Rollback command dispatched for '${flagKey}' -> v${targetVersion}`);
        }
    };

    window.deprecateFlag = (flagKey) => {
        state.staleFlags = state.staleFlags.filter(sf => sf.key !== flagKey);
        renderStaleFlags();
        alert(`Flag '${flagKey}' marked as deprecated/cleaned up.`);
    };

    window.openEvalDrawer = (flagKey) => {
        populateEvalFlagSelect();
        const select = document.getElementById('evalFlagSelect');
        if (select) select.value = flagKey;
        evalDrawer.classList.add('open');
    };

    function populateEvalFlagSelect() {
        const select = document.getElementById('evalFlagSelect');
        select.innerHTML = state.flags.map(f => `
            <option value="${escapeHtml(f.key)}">${escapeHtml(f.name || f.key)} (${f.key})</option>
        `).join('');
    }

    async function handleExecuteEval() {
        const flagKey = document.getElementById('evalFlagSelect').value;
        const userKey = document.getElementById('evalUserKey').value;
        let attributes = {};
        try {
            attributes = JSON.parse(document.getElementById('evalAttributes').value);
        } catch (e) {
            alert('Invalid JSON in context attributes');
            return;
        }

        try {
            const resp = await apiFetch(`/evaluate/${flagKey}`, {
                method: 'POST',
                body: JSON.stringify({ userKey, attributes })
            });
            if (resp.ok) {
                const res = await resp.json();
                showEvalResult(res.variationKey, res.value, res.reason);
                return;
            }
        } catch (e) {
            console.warn('Backend eval failed, falling back to client evaluation preview', e);
        }

        // Fallback demo evaluation
        const flag = state.flags.find(f => f.key === flagKey);
        const val = flag ? (flag.killSwitch ? 'false (killswitch)' : 'true') : 'unknown';
        showEvalResult(flagKey + '-val', val, flag?.killSwitch ? 'KILL_SWITCH' : 'DEFAULT');
    }

    function showEvalResult(varKey, value, reason) {
        document.getElementById('resVarKey').textContent = varKey;
        document.getElementById('resValue').textContent = JSON.stringify(value);
        document.getElementById('resReason').textContent = reason;
        evalResultBox.style.display = 'block';
    }

    async function handleCreateFlag() {
        const key = document.getElementById('newFlagKey').value.trim();
        const name = document.getElementById('newFlagName').value.trim();
        const type = document.getElementById('newFlagType').value;
        const owner = document.getElementById('newFlagOwner').value.trim();
        const rolloutPercentage = parseFloat(document.getElementById('newFlagRollout').value) || 0;

        if (!key) {
            alert('Flag Key is required');
            return;
        }

        const payload = {
            key,
            name: name || key,
            type,
            owner: owner || 'unassigned',
            enabled: true,
            rolloutPercentage,
            variations: type === 'BOOLEAN' ? [
                { key: 'true', type: 'BOOLEAN', value: true },
                { key: 'false', type: 'BOOLEAN', value: false }
            ] : [{ key: 'default', type, value: 'default' }],
            defaultVariationKey: type === 'BOOLEAN' ? 'true' : 'default',
            offVariationKey: type === 'BOOLEAN' ? 'false' : 'default'
        };

        try {
            const resp = await apiFetch('/flags', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            if (resp.ok) {
                alert(`Flag '${key}' successfully created!`);
                createFlagModal.classList.remove('open');
                refreshDashboard();
                return;
            }
        } catch (e) {
            console.warn('Backend create failed, simulating', e);
        }

        state.flags.push({ ...payload, killSwitch: false, version: 1 });
        createFlagModal.classList.remove('open');
        renderFlags();
        alert(`[DEMO] Flag '${key}' created.`);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
});
