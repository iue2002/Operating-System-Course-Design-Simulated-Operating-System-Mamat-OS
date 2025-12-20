const AGENT_ENDPOINT = import.meta.env.VITE_AGENT_ENDPOINT || 'https://agentapi.baidu.com/assistant/getAnswer';
const AGENT_APP_ID = import.meta.env.VITE_AGENT_APP_ID;
const AGENT_SECRET_KEY = import.meta.env.VITE_AGENT_SECRET_KEY;
const AGENT_SOURCE = import.meta.env.VITE_AGENT_SOURCE || AGENT_APP_ID;

const buildUrl = () => {
    if (!AGENT_APP_ID || !AGENT_SECRET_KEY) {
        throw new Error('缺少智能体配置，请在 .env.local 中设置 VITE_AGENT_APP_ID 与 VITE_AGENT_SECRET_KEY');
    }
    const params = new URLSearchParams({
        appId: AGENT_APP_ID,
        secretKey: AGENT_SECRET_KEY,
    });
    return `${AGENT_ENDPOINT}?${params.toString()}`;
};

const toMessage = (items) => {
    if (!Array.isArray(items) || items.length === 0) return '';
    return items
        .map((entry) => {
            if (!entry) return '';
            if (typeof entry === 'string') return entry;
            const { dataType, data } = entry;
            if (!data) return '';
            if (typeof data === 'string') return data;
            if (dataType === 'markdown' && typeof data.text === 'string') return data.text;
            if (typeof data.text === 'string') return data.text;
            return JSON.stringify(data);
        })
        .filter(Boolean)
        .join('\n\n');
};

export const callAgent = async ({ prompt, threadId, openId }) => {
    const url = buildUrl();
    const payload = {
        message: {
            content: {
                type: 'text',
                value: {
                    showText: prompt,
                },
            },
        },
        source: AGENT_SOURCE,
        from: 'openapi',
        openId,
    };

    if (threadId) {
        payload.threadId = threadId;
    }

    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`智能体接口 HTTP ${response.status}：${errorText}`);
    }

    const json = await response.json();
    if (json.status !== 0) {
        throw new Error(json.message || '智能体接口返回异常');
    }

    const data = json.data || {};
    return {
        text: toMessage(data.content),
        threadId: data.threadId,
        references: data.referenceList || [],
    };
};
