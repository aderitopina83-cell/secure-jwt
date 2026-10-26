'use strict';

// Pagina de teste. Repara: este JavaScript NUNCA ve o token JWT.
// O browser guarda-o num cookie HttpOnly e envia-o sozinho.

const logEl = document.getElementById('log');

function xsrf() {
    const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return m ? decodeURIComponent(m[1]) : '';
}

function log(title, status, note, body) {
    const li = document.createElement('li');
    const row = document.createElement('div');
    row.className = 'row';

    if (status !== null) {
        const s = document.createElement('span');
        s.className = 'status s' + String(status).charAt(0);
        s.textContent = status;
        row.appendChild(s);
    }
    const t = document.createElement('span');
    t.className = 'title';
    t.textContent = title;
    row.appendChild(t);
    li.appendChild(row);

    if (note) {
        const n = document.createElement('div');
        n.className = 'note';
        n.textContent = note;
        li.appendChild(n);
    }
    if (body) {
        const pre = document.createElement('pre');
        pre.textContent = body; // textContent: nunca interpretar HTML vindo da resposta
        li.appendChild(pre);
    }
    logEl.prepend(li);
}

async function call(method, url, { body, csrf = true } = {}) {
    const headers = {};
    if (method !== 'GET' && csrf) headers['X-XSRF-TOKEN'] = xsrf();
    if (body) headers['Content-Type'] = 'application/json';
    const res = await fetch(url, {
        method,
        credentials: 'same-origin',
        headers,
        body: body ? JSON.stringify(body) : undefined
    });
    const text = await res.text();
    let pretty = text;
    try { pretty = JSON.stringify(JSON.parse(text), null, 2); } catch (e) { /* nao e JSON */ }
    return { status: res.status, text: pretty };
}

const explain = {
    200: 'Autorizado.',
    204: 'Feito.',
    401: 'Recusado: sem token válido (não autenticado, expirado ou revogado).',
    403: 'Recusado: sem permissão, ou falta o token CSRF.',
    429: 'Bloqueado: demasiadas tentativas deste IP. Espera 1 minuto.'
};

const actions = {
    async me() {
        const r = await call('GET', '/api/me');
        log('GET /api/me', r.status, explain[r.status], r.text);
    },
    async admin() {
        const r = await call('GET', '/api/admin/ping');
        log('GET /api/admin/ping', r.status,
            r.status === 403 ? 'Autenticado, mas sem o papel ADMIN.' : explain[r.status], r.text);
    },
    async refresh() {
        const r = await call('POST', '/api/auth/refresh');
        log('POST /api/auth/refresh', r.status,
            r.status === 200 ? 'Novo access token e novo refresh token (o anterior deixou de valer).' : explain[r.status],
            r.text);
    },
    async logout() {
        const r = await call('POST', '/api/auth/logout');
        log('POST /api/auth/logout', r.status,
            'Token revogado no servidor. Carrega em "Ver os meus dados": deve dar 401.', null);
    },
    async logoutAll() {
        const r = await call('POST', '/api/session/logout-all');
        log('POST /api/session/logout-all', r.status,
            r.status === 204 ? 'Todas as sessões deste utilizador terminadas, em qualquer dispositivo.' : explain[r.status],
            null);
    },
    async readCookie() {
        const visible = document.cookie || '(nenhum)';
        const leaked = /__Host-access|__Secure-refresh/.test(document.cookie);
        log('document.cookie', null,
            leaked
                ? 'FALHA: o token está visível para JavaScript!'
                : 'Seguro: o JWT e o refresh token não aparecem (HttpOnly). Só se vê o XSRF-TOKEN, que é suposto.',
            visible);
    },
    async noCsrf() {
        const r = await call('POST', '/api/session/logout-all', { csrf: false });
        log('POST sem X-XSRF-TOKEN', r.status,
            r.status === 403 ? 'Seguro: um site malicioso não consegue fazer pedidos em teu nome.' : 'Inesperado.',
            null);
    },
    async bruteForce() {
        const username = document.getElementById('username').value;
        const results = [];
        for (let i = 1; i <= 6; i++) {
            const r = await call('POST', '/api/auth/login',
                { body: { username, password: 'senha-errada-' + i } });
            results.push('tentativa ' + i + ': ' + r.status);
        }
        log('6 senhas erradas', null,
            'Depois de 5 falhas a conta fica bloqueada 15 min: agora até a senha certa dá 401. '
            + 'Em dev, reinicia a app para desbloquear.',
            results.join('\n'));
    },
    clear() {
        logEl.replaceChildren();
    }
};

document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const r = await call('POST', '/api/auth/login', {
        body: {
            username: document.getElementById('username').value,
            password: document.getElementById('password').value
        }
    });
    document.getElementById('password').value = '';
    log('POST /api/auth/login', r.status,
        r.status === 200 ? 'Login feito. O token foi para um cookie HttpOnly: repara que não aparece aqui.' : explain[r.status],
        r.text);
});

document.querySelectorAll('[data-action]').forEach((btn) => {
    btn.addEventListener('click', () => actions[btn.dataset.action]().catch((err) => log('Erro de rede', null, String(err), null)));
});

// Obter o cookie XSRF-TOKEN ao abrir a pagina
fetch('/api/auth/csrf', { credentials: 'same-origin' })
    .then(() => log('Página pronta', null, 'Token CSRF obtido. Faz login para começar.', null))
    .catch((err) => log('Erro de rede', null, String(err), null));
