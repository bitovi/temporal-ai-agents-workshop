let currentConversationId = null;
let currentEventSource = null;
let remoteLaneActive = false;

// ── Adaptive layout: expand to 3 columns when A2A events arrive ─
function activateRemoteLane() {
    if (remoteLaneActive) return;
    remoteLaneActive = true;
    const grid = document.getElementById('swimlane-grid');
    grid.classList.remove('two-col');
    // Rename "Agent" → "Client Agent" to distinguish from remote
    const header = document.getElementById('client-lane-header');
    const walker = document.createTreeWalker(header, NodeFilter.SHOW_TEXT, null);
    while (walker.nextNode()) {
        if (walker.currentNode.textContent.trim() === 'Agent') {
            walker.currentNode.textContent = walker.currentNode.textContent.replace('Agent', 'Client Agent');
            break;
        }
    }
}

// ── Lane resolution ──────────────────────────────────────────
const LANE_ORDER = ['user', 'client', 'remote'];

function resolveLane(data) {
    if (data.lane) return data.lane;
    const t = data.type.toLowerCase();
    if (t === 'user_message' || t === 'connected') return 'user';
    if (t.startsWith('a2a_') && t !== 'a2a_task_submitted' && t !== 'a2a_task_resumed') return 'remote';
    return 'client';
}

function resolveTargetLane(data) {
    if (data.targetLane) return data.targetLane;
    const t = data.type.toLowerCase();
    if (t === 'user_message') return 'client';
    if (t === 'answer') return 'user';
    if (t === 'a2a_task_submitted' || t === 'a2a_task_resumed') return 'remote';
    if (t === 'a2a_input_required' || t === 'a2a_completed' || t === 'a2a_failed' || t === 'a2a_artifact') return 'client';
    return null;
}

function arrowDirection(lane, targetLane) {
    if (!targetLane) return null;
    const from = LANE_ORDER.indexOf(lane);
    const to = LANE_ORDER.indexOf(targetLane);
    if (from < 0 || to < 0 || from === to) return null;
    return to > from ? 'right' : 'left';
}

// ── Status-message → pill-state mapping ─────────────────────
const STATUS_TO_STATE = {
    'Thinking...': 'thinking',
    'Acting...': 'acting',
    'Observing...': 'observing',
    'Compacting...': 'compacting',
    'Working': 'working',
    'Awaiting Input': 'awaiting_input',
};

function setLaneStatus(lane, message, seq) {
    const container = document.getElementById('pills-' + lane);
    if (!container) return;
    // Reject stale out-of-order events
    if (typeof seq === 'number') {
        if (seq < (laneStatusSeq[lane] || -1)) return;
        laneStatusSeq[lane] = seq;
    }
    const state = message ? (STATUS_TO_STATE[message] || 'idle') : 'idle';
    container.querySelectorAll('.pill').forEach(p => {
        p.classList.toggle('active', p.dataset.state === state);
    });
}

const laneStatusSeq = { user: -1, client: -1, remote: -1 };

function clearAllLaneStatuses() {
    LANE_ORDER.forEach(l => setLaneStatus(l, ''));
    laneStatusSeq.user = laneStatusSeq.client = laneStatusSeq.remote = -1;
}

// ── SSE connection ───────────────────────────────────────────
function connectToConversationEvents(conversationId) {
    if (currentEventSource) { currentEventSource.close(); currentEventSource = null; }

    const es = new EventSource(`/events/${conversationId}`);
    currentEventSource = es;

    es.onopen = function () {
        document.getElementById('status').textContent = '✓ Connected to event stream';
    };
    es.onerror = function () {
        document.getElementById('status').textContent = '✗ Error connecting to event stream';
        clearAllLaneStatuses();
    };

    es.onmessage = function (event) {
        const data = JSON.parse(event.data);

        if (data.type === 'status') {
            setLaneStatus(data.lane || 'client', data.message, data.sequence);
            return;
        }

        if (data.type !== 'action') {
            setLaneStatus(resolveLane(data), '', data.sequence);
        }

        // Activate 3-column layout when any A2A event arrives
        if (data.type && data.type.toLowerCase().startsWith('a2a_')) {
            activateRemoteLane();
        }

        // a2a_working → show simple "Working" in header + render card in body
        if (data.type === 'a2a_working') {
            setLaneStatus('remote', 'Working', data.sequence);
        }

        // Show awaiting-input pill on remote lane
        if (data.type === 'a2a_input_required') {
            setLaneStatus('remote', 'Awaiting Input', data.sequence);
        }

        eventBuffer.push(data);
        if (bufferTimer) clearTimeout(bufferTimer);
        bufferTimer = setTimeout(flushEventBuffer, 100);
    };
}

function setConversationId(id) {
    currentConversationId = id;
    const el = document.getElementById('conversationId');
    const link = document.getElementById('temporalUiLink');
    if (id) {
        el.textContent = id;
        link.href = `http://localhost:8233/namespaces/default/workflows/${encodeURIComponent('agent-workflow-' + id)}`;
        link.style.display = 'inline';
    } else {
        el.textContent = 'No active conversation';
        link.style.display = 'none';
    }
}

let eventBuffer = [];
let bufferTimer = null;

function getLabelText(data) {
    const a2aAgent = data.agentName || '';
    const t = data.type.toLowerCase();
    const map = {
        a2a_task_submitted: a2aAgent ? `Submitting task to ${a2aAgent}` : 'Task Submitted',
        a2a_task_resumed: a2aAgent ? `Resuming task with ${a2aAgent}` : 'Task Resumed',
        a2a_input_required: a2aAgent ? `${a2aAgent} needs input` : 'Input Required',
        a2a_working: a2aAgent ? `${a2aAgent}: Update` : 'A2A Update',
        a2a_artifact: a2aAgent ? `Artifact from ${a2aAgent}` : 'Artifact',
        a2a_completed: a2aAgent ? `${a2aAgent} completed` : 'Completed',
        a2a_failed: a2aAgent ? `${a2aAgent} failed` : 'Failed',
    };
    if (map[t]) return map[t];
    return data.type.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

function humanizeA2aMessage(msg) {
    if (!msg) return msg;
    // Match tool-call pattern: → tool_name({...})
    const match = msg.match(/^→\s*(\w+)\((.+)\)$/s);
    if (!match) return msg;
    const toolName = match[1];
    const paramsStr = match[2];
    // Convert snake_case to readable words, capitalize first letter
    const words = toolName.replace(/_/g, ' ');
    const humanName = words.charAt(0).toUpperCase() + words.slice(1);
    // Format params as readable key-value pairs
    let paramDesc = '';
    try {
        const params = JSON.parse(paramsStr);
        const entries = Object.entries(params);
        if (entries.length) {
            paramDesc = ' (' + entries.map(([k, v]) => `${k.replace(/_/g, ' ')}: ${v}`).join(', ') + ')';
        }
    } catch { }
    return humanName + paramDesc;
}

// ── Build event card ─────────────────────────────────────────
function buildEventCard(data) {
    const typeClass = data.type.toLowerCase().replace(/[^a-z0-9]/g, '_');
    const lane = resolveLane(data);
    const target = resolveTargetLane(data);
    const arrow = arrowDirection(lane, target);

    const div = document.createElement('div');
    let cls = `event ${typeClass}`;
    if (target) cls += ' directional';
    if (arrow) cls += ` arrow-${arrow}`;
    div.className = cls;

    const lbl = document.createElement('span');
    lbl.className = 'event-label';
    lbl.textContent = getLabelText(data);
    div.appendChild(lbl);

    const msgEl = document.createElement('div');
    msgEl.className = 'event-message';

    if (data.type.toLowerCase() === 'answer' && typeof marked !== 'undefined') {
        msgEl.innerHTML = marked.parse(data.message);
    } else if (data.type === 'a2a_artifact') {
        const card = document.createElement('div');
        card.className = 'artifact-card';
        const header = document.createElement('div');
        header.className = 'artifact-header';
        header.textContent = '📄 ' + (data.title || 'Artifact');
        card.appendChild(header);
        const table = document.createElement('table');
        table.className = 'artifact-table';
        const skipKeys = new Set(['type', 'message', 'timestamp', 'sequence', 'workflowId', 'title', 'lane', 'targetLane', 'agentName']);
        for (const [key, val] of Object.entries(data)) {
            if (skipKeys.has(key)) continue;
            const row = document.createElement('tr');
            const kc = document.createElement('td');
            kc.textContent = key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
            const vc = document.createElement('td');
            vc.textContent = typeof val === 'object' ? JSON.stringify(val) : String(val);
            row.appendChild(kc); row.appendChild(vc); table.appendChild(row);
        }
        card.appendChild(table);
        msgEl.appendChild(card);
    } else if (data.type === 'a2a_working') {
        msgEl.textContent = humanizeA2aMessage(data.message);
    } else {
        msgEl.textContent = data.message;
    }

    div.appendChild(msgEl);
    return { element: div, lane };
}

// ── Create a 3-column event row ──────────────────────────────
function createEventRow(eventCard, lane) {
    const row = document.createElement('div');
    row.className = 'event-row';
    for (const l of LANE_ORDER) {
        const cell = document.createElement('div');
        cell.className = `lane-cell ${l}`;
        if (l === lane) cell.appendChild(eventCard);
        row.appendChild(cell);
    }
    return row;
}

// ── Flush buffered events ────────────────────────────────────
function flushEventBuffer() {
    if (eventBuffer.length === 0) return;

    eventBuffer.sort((a, b) => {
        const td = (a.timestamp || 0) - (b.timestamp || 0);
        return td !== 0 ? td : (a.sequence || 0) - (b.sequence || 0);
    });

    eventBuffer = eventBuffer.filter((ev, i, arr) => {
        if (ev.type !== 'a2a_working') return true;
        const next = arr[i + 1];
        return !(next && next.type === 'a2a_input_required');
    });

    const body = document.getElementById('swimlane-body');
    const emptyEl = document.getElementById('events-empty');
    if (emptyEl) emptyEl.remove();

    eventBuffer.forEach(data => {
        const { element, lane } = buildEventCard(data);
        body.appendChild(createEventRow(element, lane));
    });

    body.scrollTop = body.scrollHeight;
    eventBuffer = [];
}

// ── Conversation management ──────────────────────────────────
async function createConversation() {
    if (currentConversationId) {
        await fetch(`/api/conversations/${currentConversationId}/exit`, { method: 'POST' });
    }
    if (bufferTimer) { clearTimeout(bufferTimer); bufferTimer = null; }
    eventBuffer = [];
    clearAllLaneStatuses();
    // Reset to 2-column mode for new conversation
    remoteLaneActive = false;
    document.getElementById('swimlane-grid').classList.add('two-col');
    const header = document.getElementById('client-lane-header');
    const walker = document.createTreeWalker(header, NodeFilter.SHOW_TEXT, null);
    while (walker.nextNode()) {
        if (walker.currentNode.textContent.trim() === 'Client Agent') {
            walker.currentNode.textContent = walker.currentNode.textContent.replace('Client Agent', 'Agent');
            break;
        }
    }
    const res = await fetch('/api/conversations', { method: 'POST' });
    const data = await res.json();
    setConversationId(data.conversationId);
    connectToConversationEvents(data.conversationId);
    document.getElementById('messagePanel').style.display = 'block';
    document.body.classList.add('has-message-panel');
    document.getElementById('swimlane-body').innerHTML =
        '<div id="events-empty">No activity yet.<br>Start a conversation and send a message to see the agent\'s steps here.</div>';
    document.getElementById('messageInput').focus();
}

async function sendMessage() {
    if (!currentConversationId) { alert('Please select or create a conversation'); return; }
    const name = 'user@example.com';
    const message = document.getElementById('messageInput').value.trim();
    if (!message) { alert('Please enter a message'); return; }
    await fetch(`/api/conversations/${currentConversationId}/message`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, message })
    });
    document.getElementById('messageInput').value = '';
    document.getElementById('messageInput').focus();
}

async function exitConversation() {
    if (!currentConversationId) return;
    const res = await fetch(`/api/conversations/${currentConversationId}/exit`, { method: 'POST' });
    const data = await res.json();
    if (currentEventSource) { currentEventSource.close(); currentEventSource = null; }
    clearAllLaneStatuses();
    currentConversationId = null;
    document.getElementById('messagePanel').style.display = 'none';
    document.body.classList.remove('has-message-panel');

    const endCard = document.createElement('div');
    endCard.className = 'event system';
    const endLabel = document.createElement('span');
    endLabel.className = 'event-label';
    endLabel.textContent = 'Conversation Ended ⚙️';
    const endMsg = document.createElement('div');
    endMsg.className = 'event-message';
    endMsg.textContent = 'Usage: ' + JSON.stringify(data.usage);
    endCard.appendChild(endLabel);
    endCard.appendChild(endMsg);
    document.getElementById('swimlane-body').appendChild(createEventRow(endCard, 'client'));
}

async function compactConversation() {
    if (!currentConversationId) return;
    await fetch(`/api/conversations/${currentConversationId}/compact`, { method: 'POST' });
}