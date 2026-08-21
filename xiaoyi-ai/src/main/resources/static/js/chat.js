const form = document.querySelector('#chat-form');
    const input = document.querySelector('#message');
    const messages = document.querySelector('#messages');
    const imageResults = document.querySelector('#image-results');
    const sendButton = document.querySelector('#send-button');
    const newChatButton = document.querySelector('#new-chat');
    const conversationList = document.querySelector('#conversation-list');
    const modeButtons = document.querySelectorAll('.mode-button');
    const conversationNavigator = document.querySelector('#conversation-navigator');
    const sidebar = document.querySelector('.sidebar');
    const sidebarResizer = document.querySelector('#sidebar-resizer');
    const savedSidebarWidth = Number(localStorage.getItem('xiaoyi-sidebar-width'));
    if (savedSidebarWidth >= 230 && savedSidebarWidth <= 460) sidebar.style.width = `${savedSidebarWidth}px`;

    sidebarResizer.addEventListener('pointerdown', event => {
        event.preventDefault();
        document.body.classList.add('is-resizing');
        const resizeSidebar = moveEvent => {
            const maximumWidth = Math.min(460, window.innerWidth - 320);
            const width = Math.min(Math.max(moveEvent.clientX, 210), maximumWidth);
            sidebar.style.width = `${width}px`;
        };
        const stopResizing = () => {
            document.body.classList.remove('is-resizing');
            localStorage.setItem('xiaoyi-sidebar-width', String(Math.round(sidebar.getBoundingClientRect().width)));
            document.removeEventListener('pointermove', resizeSidebar);
        };
        document.addEventListener('pointermove', resizeSidebar);
        document.addEventListener('pointerup', stopResizing, { once: true });
    });
    let navigatorFrame = null;

    function setNavigatorPreviewActive(activeIndex) {
        conversationNavigator?.querySelectorAll('.navigator-marker, .navigator-preview-item')
            .forEach(item => item.classList.toggle('active', Number(item.dataset.questionIndex) === activeIndex));
    }

    function updateConversationNavigator() {
        if (!conversationNavigator) return;
        const questionItems = [...messages.querySelectorAll('.message.user')]
            .filter(message => message.querySelector('.bubble')?.textContent.trim());
        conversationNavigator.replaceChildren();
        conversationNavigator.hidden = questionItems.length === 0;
        if (conversationNavigator.hidden) return;

        const previewPanel = document.createElement('div');
        previewPanel.className = 'navigator-preview-panel';
        questionItems.forEach((message, index) => {
            const question = message.querySelector('.bubble').textContent.replace(/\s+/g, ' ').trim();
            const marker = document.createElement('button');
            marker.type = 'button';
            marker.className = 'navigator-marker user';
            marker.dataset.questionIndex = index;
            marker.title = `定位到问题 ${index + 1}`;
            marker.setAttribute('aria-label', `定位到问题 ${index + 1}：${question}`);
            marker.addEventListener('mouseenter', () => setNavigatorPreviewActive(index));
            marker.addEventListener('focus', () => setNavigatorPreviewActive(index));
            marker.addEventListener('click', () => message.scrollIntoView({ behavior: 'smooth', block: 'center' }));

            const previewItem = document.createElement('button');
            previewItem.type = 'button';
            previewItem.className = 'navigator-preview-item';
            previewItem.dataset.questionIndex = index;
            previewItem.textContent = question;
            previewItem.title = question;
            previewItem.addEventListener('mouseenter', () => setNavigatorPreviewActive(index));
            previewItem.addEventListener('focus', () => setNavigatorPreviewActive(index));
            previewItem.addEventListener('click', () => message.scrollIntoView({ behavior: 'smooth', block: 'center' }));

            conversationNavigator.append(marker);
            previewPanel.append(previewItem);
        });
        conversationNavigator.append(previewPanel);
        updateActiveNavigatorMarker();
    }

    function updateActiveNavigatorMarker() {
        if (!conversationNavigator || conversationNavigator.hidden) return;
        const questionItems = [...messages.querySelectorAll('.message.user')]
            .filter(message => message.querySelector('.bubble')?.textContent.trim());
        const focusLine = messages.scrollTop + messages.clientHeight * .42;
        let activeIndex = 0;
        questionItems.forEach((message, index) => {
            if (message.offsetTop <= focusLine) activeIndex = index;
        });
        setNavigatorPreviewActive(activeIndex);
    }
    function scheduleConversationNavigator() {
        if (navigatorFrame) cancelAnimationFrame(navigatorFrame);
        navigatorFrame = requestAnimationFrame(() => {
            navigatorFrame = null;
            updateConversationNavigator();
        });
    }
    let activeConversationId = null;
    let activeMode = 'chat';

    function addMessage(role, content = '') {
        const message = document.createElement('div');
        message.className = `message ${role}`;
        const avatar = document.createElement('div');
        avatar.className = 'avatar';
        avatar.textContent = role === 'user' ? '我' : '艺';
        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        bubble.textContent = content;
        message.append(avatar, bubble);
        if (role === 'assistant' && content) addAssistantActions(bubble, content);
        messages.append(message);
        messages.scrollTop = messages.scrollHeight;
        scheduleConversationNavigator();
        return bubble;
    }

    const speakerIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5.5 10.25v3.5c0 .7.56 1.25 1.25 1.25H9l4.4 3.45c.66.52 1.6.05 1.6-.8V6.35c0-.85-.94-1.32-1.6-.8L9 9H6.75c-.69 0-1.25.56-1.25 1.25Z"/><path d="M17.25 9.25a4.2 4.2 0 0 1 0 5.5"/></svg>';
    const stopIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="7.5" y="7.5" width="9" height="9" rx="1.5"/></svg>';
    const copyIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="8" y="8" width="10" height="10" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>';
    const copiedIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4.2 4.2L19 6.5"/></svg>';
    let activeAudio = null;
    let activeSpeechButton = null;
    let activeAutoSpeechSession = null;
    let speechRequestId = 0;

    function resolveText(textOrSupplier) {
        return typeof textOrSupplier === 'function' ? textOrSupplier() : textOrSupplier;
    }

    function addAssistantActions(bubble, textOrSupplier) {
        if (bubble.querySelector('.speech-button')) return;
        const copyButton = document.createElement('button');
        copyButton.type = 'button';
        copyButton.className = 'copy-button';
        copyButton.innerHTML = copyIcon;
        copyButton.title = '复制回复';
        copyButton.setAttribute('aria-label', '复制回复');
        copyButton.addEventListener('click', () => copyReply(resolveText(textOrSupplier), copyButton));

        const speechButton = document.createElement('button');
        speechButton.type = 'button';
        speechButton.className = 'speech-button';
        speechButton.innerHTML = speakerIcon;
        speechButton.title = '朗读这条回复';
        speechButton.setAttribute('aria-label', '朗读这条回复');
        speechButton.addEventListener('click', () => playSpeech(resolveText(textOrSupplier), speechButton));
        bubble.append(copyButton, speechButton);
    }

    async function copyReply(text, button) {
        try {
            await navigator.clipboard.writeText(text);
            button.classList.add('copied');
            button.innerHTML = copiedIcon;
            button.title = '已复制';
        } catch (error) { alert(`复制失败：${error.message}`); }
    }

    function resetSpeechButton(button) {
        button.classList.remove('playing');
        button.innerHTML = speakerIcon;
        button.title = '朗读这条回复';
        button.setAttribute('aria-label', '朗读这条回复');
        button.disabled = false;
    }

    function stopCurrentSpeech() {
        if (activeAutoSpeechSession) {
            activeAutoSpeechSession.stopped = true;
            activeAutoSpeechSession.abortController.abort();
            activeAutoSpeechSession.queue.length = 0;
        }
        if (activeAudio) {
            activeAudio.pause();
            activeAudio.currentTime = 0;
        }
        if (activeSpeechButton) resetSpeechButton(activeSpeechButton);
        activeAudio = null;
        activeSpeechButton = null;
        activeAutoSpeechSession = null;
    }

    async function requestSpeechUrl(text, signal) {
        const response = await fetch('/speech/synthesize', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text }),
            signal
        });
        if (!response.ok) throw new Error(`语音生成失败：${response.status}`);
        const { audioUrl } = await response.json();
        return audioUrl;
    }

    function startAutoSpeechSession(button) {
        stopCurrentSpeech();
        const session = { button, queue: [], index: 0, playing: false, completed: false, stopped: false, abortController: new AbortController() };
        activeAutoSpeechSession = session;
        activeSpeechButton = button;
        button.classList.add('playing');
        button.innerHTML = stopIcon;
        button.title = '停止朗读';
        button.setAttribute('aria-label', '停止朗读');
        return session;
    }

    function enqueueSpeechSentence(session, text) {
        const sentence = text.trim();
        if (sentence) session.queue.push({ text: sentence, audioUrl: null, loading: false, failed: false, fetchAttempts: 0 });
    }

    function prefetchSpeechSentence(session, index) {
        const item = session.queue[index];
        if (!item || item.audioUrl || item.loading || item.failed || session.stopped) return;
        item.loading = true;
        item.fetchAttempts += 1;
        requestSpeechUrl(item.text, session.abortController.signal)
            .then(audioUrl => {
                if (activeAutoSpeechSession !== session || session.stopped) return;
                item.loading = false;
                item.audioUrl = audioUrl;
                playNextSpeechSentence(session);
            })
            .catch(error => {
                if (activeAutoSpeechSession !== session || session.stopped || error.name === 'AbortError') return;
                item.loading = false;
                if (item.fetchAttempts < 2) {
                    window.setTimeout(() => prefetchSpeechSentence(session, index), 450);
                    return;
                }
                item.failed = true;
                playNextSpeechSentence(session);
            });
    }

    function completeAutoSpeechSession(session) {
        if (activeAutoSpeechSession !== session) return;
        activeAutoSpeechSession = null;
        activeAudio = null;
        activeSpeechButton = null;
        resetSpeechButton(session.button);
    }

    function playNextSpeechSentence(session) {
        if (activeAutoSpeechSession !== session || session.stopped || session.playing) return;
        while (session.index < session.queue.length && session.queue[session.index].failed) session.index += 1;
        const item = session.queue[session.index];
        if (!item) {
            if (session.completed) completeAutoSpeechSession(session);
            return;
        }
        if (!item.audioUrl) {
            prefetchSpeechSentence(session, session.index);
            return;
        }
        session.playing = true;
        const audio = new Audio(item.audioUrl);
        activeAudio = audio;
        audio.addEventListener('ended', () => {
            if (activeAutoSpeechSession !== session) return;
            session.index += 1;
            session.playing = false;
            activeAudio = null;
            playNextSpeechSentence(session);
        }, { once: true });
        audio.addEventListener('error', () => {
            if (activeAutoSpeechSession !== session) return;
            session.playing = false;
            activeAudio = null;
            item.audioUrl = null;
            item.failed = true;
            playNextSpeechSentence(session);
        }, { once: true });
        prefetchSpeechSentence(session, session.index + 1);
        audio.play().catch(() => {
            if (activeAutoSpeechSession !== session) return;
            session.playing = false;
            activeAudio = null;
            item.failed = true;
            playNextSpeechSentence(session);
        });
    }
    function splitSpeechSentences(text) {
        const segments = text.match(/[^。！？!?]+[。！？!?]?/g) || [];
        return segments.map(segment => segment.trim()).filter(Boolean);
    }

    function playSpeech(text, button) {
        if (activeSpeechButton === button) {
            speechRequestId += 1;
            stopCurrentSpeech();
            return;
        }
        const sentences = splitSpeechSentences(text);
        if (!sentences.length) return;
        speechRequestId += 1;
        const session = startAutoSpeechSession(button);
        sentences.forEach(sentence => enqueueSpeechSentence(session, sentence));
        session.completed = true;
        playNextSpeechSentence(session);
    }

    function renderMessages(history) {
        messages.replaceChildren();
        if (!history.length) {
            const empty = document.createElement('div');
            empty.className = 'empty';
            empty.textContent = '你好，我是 XiaoYi。\n开始一段新的对话吧～';
            messages.append(empty);
            scheduleConversationNavigator();
            return;
        }
        history.forEach(item => addMessage(item.role, item.content));
        scheduleConversationNavigator();
    }

    function renderImageEmpty() {
        imageResults.replaceChildren();
        const empty = document.createElement('div');
        empty.className = 'image-empty';
        empty.innerHTML = '<strong>百炼生图</strong>描述你想看到的画面，例如：\n“水彩风格的江南雨巷，傍晚，有一只橘猫”。';
        imageResults.append(empty);
    }

    function addImageCard(prompt) {
        if (imageResults.querySelector('.image-empty')) imageResults.replaceChildren();
        const card = document.createElement('article');
        card.className = 'image-card';
        const promptText = document.createElement('p');
        promptText.className = 'image-prompt';
        promptText.textContent = prompt;
        const preview = document.createElement('div');
        preview.className = 'image-preview';
        showImageLoading(preview);
        card.append(promptText, preview);
        imageResults.append(card);
        imageResults.scrollTop = imageResults.scrollHeight;
        return { card, preview };
    }

    function renderConversations(conversations) {
        conversationList.replaceChildren();
        conversations.forEach(conversation => {
            const item = document.createElement('div');
            item.className = `conversation ${conversation.id === activeConversationId ? 'active' : ''}`;

            const selectButton = document.createElement('button');
            selectButton.type = 'button';
            selectButton.className = 'conversation-select';
            const title = document.createElement('div');
            title.className = 'conversation-title';
            title.textContent = conversation.title;
            const time = document.createElement('div');
            time.className = 'conversation-time';
            time.textContent = new Date(conversation.updatedAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
            selectButton.append(title, time);
            selectButton.addEventListener('click', () => selectConversation(conversation.id));

            const deleteButton = document.createElement('button');
            deleteButton.type = 'button';
            deleteButton.className = 'delete-conversation';
            deleteButton.textContent = '×';
            deleteButton.title = '删除会话';
            deleteButton.setAttribute('aria-label', '删除会话');
            deleteButton.addEventListener('click', () => deleteConversation(conversation.id));

            item.append(selectButton, deleteButton);
            conversationList.append(item);
        });
    }

    async function refreshConversations() {
        const response = await fetch('/chat/conversations');
        if (!response.ok) throw new Error(`加载会话失败：${response.status}`);
        const conversations = await response.json();
        renderConversations(conversations);
        return conversations;
    }

    async function selectConversation(conversationId) {
        activeConversationId = conversationId;
        localStorage.setItem('xiaoyi-active-conversation', conversationId);
        const response = await fetch(`/chat/conversations/${encodeURIComponent(conversationId)}/messages`);
        renderMessages(await response.json());
        await refreshConversations();
    }

    async function createConversation() {
        const response = await fetch('/chat/conversations', { method: 'POST' });
        const conversation = await response.json();
        activeConversationId = conversation.id;
        localStorage.setItem('xiaoyi-active-conversation', activeConversationId);
        renderMessages([]);
        await refreshConversations();
        input.focus();
    }

    async function deleteConversation(conversationId) {
        if (sendButton.disabled) return;
        const response = await fetch(`/chat/conversations/${encodeURIComponent(conversationId)}`, { method: 'DELETE' });
        if (!response.ok) {
            alert('删除失败，请稍后重试。');
            return;
        }

        const conversations = await refreshConversations();
        if (conversationId !== activeConversationId) return;

        const nextConversation = conversations[0];
        if (nextConversation) {
            await selectConversation(nextConversation.id);
        } else {
            await createConversation();
        }
    }

    function setMode(mode) {
        activeMode = mode;
        const imageMode = mode === 'image';
        document.body.classList.toggle('image-mode', imageMode);
        messages.hidden = imageMode;
        imageResults.hidden = !imageMode;
        input.placeholder = imageMode ? '描述你想生成的图片' : '问问XiaoYi';
        sendButton.innerHTML = imageMode ? '生成图片' : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 19V5M6.5 10.5 12 5l5.5 5.5"/></svg>';
        sendButton.setAttribute('aria-label', imageMode ? '生成图片' : '发送消息');
        sendButton.title = imageMode ? '生成图片' : '发送消息';
        modeButtons.forEach(button => button.classList.toggle('active', button.dataset.mode === mode));
        input.focus();
    }
    function showSpinner(container) {
        container.classList.add('thinking');
        const thinkingDot = document.createElement('div');
        thinkingDot.className = 'thinking-dot';
        container.replaceChildren(thinkingDot);
    }

    function showImageLoading(container) {
        const loading = document.createElement('div');
        loading.className = 'image-loading';
        const spinner = document.createElement('div');
        spinner.className = 'spinner';
        const text = document.createElement('span');
        text.textContent = '正在生成图片，通常需要 1～3 分钟…';
        loading.append(spinner, text);
        container.replaceChildren(loading);
    }

    function appendSseEvents(buffer, onContent) {
        const events = buffer.split(/\r?\n\r?\n/);
        const remaining = events.pop();
        for (const event of events) {
            const data = event.split(/\r?\n/)
                .filter(line => line.startsWith('data:'))
                .map(line => line.slice(5).trimStart())
                .join('\n');
            if (data) onContent(data);
        }
        return remaining;
    }

    async function generateImage(prompt) {
        const { card, preview } = addImageCard(prompt);
        input.value = '';
        input.disabled = true;
        sendButton.disabled = true;

        try {
            const response = await fetch('/images/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt })
            });
            if (!response.ok) throw new Error(`请求失败：${response.status}`);

            const result = await response.json();
            const image = document.createElement('img');
            image.src = result.imageUrl;
            image.alt = prompt;
            preview.replaceChildren(image);

            const link = document.createElement('a');
            link.className = 'image-link';
            link.href = result.imageUrl;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            link.textContent = '在新标签页打开原图';
            card.append(link);
        } catch (error) {
            preview.replaceChildren();
            const message = document.createElement('span');
            message.className = 'image-error';
            message.textContent = `生成失败：${error.message}`;
            preview.append(message);
        } finally {
            input.disabled = false;
            sendButton.disabled = false;
            input.focus();
            imageResults.scrollTop = imageResults.scrollHeight;
        }
    }

    messages.addEventListener('scroll', updateActiveNavigatorMarker);
    newChatButton.addEventListener('click', createConversation);
    modeButtons.forEach(button => button.addEventListener('click', () => setMode(button.dataset.mode)));

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const question = input.value.trim();
if (!question) return;
        if (activeMode === 'chat' && !activeConversationId) {
            try {
                await createConversation();
            } catch (error) {
                alert(`无法创建会话：${error.message}`);
                return;
            }
        }
        if (activeMode === 'image') {
            await generateImage(question);
            return;
        }

        if (messages.querySelector('.empty')) messages.replaceChildren();
        addMessage('user', question);
        input.value = '';
        input.disabled = true;
        sendButton.disabled = true;
        stopCurrentSpeech();
        const answer = addMessage('assistant');
        showSpinner(answer);
        let receivedContent = false;
        let answerText = null;

        try {
            const response = await fetch(`/chat/stream?message=${encodeURIComponent(question)}&conversationId=${encodeURIComponent(activeConversationId)}`);
            if (!response.ok || !response.body) throw new Error(`请求失败：${response.status}`);
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            while (true) {
                const { done, value } = await reader.read();
                if (value) buffer += decoder.decode(value, { stream: true });
                if (done) buffer += decoder.decode();
                buffer = appendSseEvents(buffer, (content) => {
                    if (!receivedContent) {
                        answer.classList.remove('thinking');
                        answer.replaceChildren();
                        answerText = document.createElement('span');
                        answerText.className = 'assistant-text';
                        answer.append(answerText);
                        receivedContent = true;
                    }
                    answerText.textContent += content;
                    messages.scrollTop = messages.scrollHeight;
                });
                if (done) break;
            }
        } catch (error) {
            answer.classList.remove('thinking');
            answer.textContent = `抱歉，暂时无法获取回答：${error.message}`;
        } finally {
            if (receivedContent) addAssistantActions(answer, () => answerText.textContent);
            input.disabled = false;
            sendButton.disabled = false;
            input.focus();
            await refreshConversations();
        }
    });

    (async () => {
        renderImageEmpty();
        try {
            const conversations = await refreshConversations();
            const savedConversationId = localStorage.getItem('xiaoyi-active-conversation');
            const current = conversations.find(item => item.id === savedConversationId) || conversations[0];
            if (current) await selectConversation(current.id);
            else await createConversation();
        } catch (error) {
            console.error('初始化会话失败：', error);
            renderMessages([]);
        }
    })();
