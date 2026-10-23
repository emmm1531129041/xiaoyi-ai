const form = document.querySelector('#chat-form');
    const input = document.querySelector('#message');
    const messages = document.querySelector('#messages');
    const imageResults = document.querySelector('#image-results');
    const attachmentInput = document.querySelector('#attachment-input');
    const attachmentButton = document.querySelector('#attachment-button');
    const attachmentPreview = document.querySelector('#attachment-preview');
    let pendingAttachments = [];
    const messagePreviewUrls = new Set();
    const sendButton = document.querySelector('#send-button');
    const stopGenerationButton = document.querySelector('#stop-generation-button');
    const newChatButton = document.querySelector('#new-chat');
    const conversationList = document.querySelector('#conversation-list');
    const modeButtons = document.querySelectorAll('.mode-button');
    const conversationNavigator = document.querySelector('#conversation-navigator');
    const sidebar = document.querySelector('.sidebar');
    const sidebarResizer = document.querySelector('#sidebar-resizer');
    const savedSidebarWidth = Number(localStorage.getItem('xiaoyi-sidebar-width'));
    if (savedSidebarWidth >= 260 && savedSidebarWidth <= 280) sidebar.style.width = `${savedSidebarWidth}px`;

    sidebarResizer?.addEventListener('pointerdown', event => {
        event.preventDefault();
        document.body.classList.add('is-resizing');
        const resizeSidebar = moveEvent => {
            const width = Math.min(Math.max(moveEvent.clientX, 260), 280);
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
    let activeChatRequest = null;
    let chatRequestSequence = 0;
    const conversationScrollStorageKey = 'xiaoyi-conversation-scroll-positions';
    const conversationScrollPositions = (() => {
        try {
            return JSON.parse(localStorage.getItem(conversationScrollStorageKey) || '{}');
        } catch (error) {
            return {};
        }
    })();

    function saveConversationScrollPositions() {
        localStorage.setItem(conversationScrollStorageKey, JSON.stringify(conversationScrollPositions));
    }

    function rememberConversationScrollPosition() {
        if (!activeConversationId || document.body.classList.contains('image-mode')) return;
        conversationScrollPositions[activeConversationId] = messages.scrollTop;
        saveConversationScrollPositions();
    }

    function restoreConversationScrollPosition(conversationId) {
        if (!Object.hasOwn(conversationScrollPositions, conversationId)) return;
        const savedPosition = Number(conversationScrollPositions[conversationId]);
        const maximumScrollTop = Math.max(0, messages.scrollHeight - messages.clientHeight);
        const previousScrollBehavior = messages.style.scrollBehavior;
        messages.style.scrollBehavior = 'auto';
        messages.scrollTop = Number.isFinite(savedPosition)
            ? Math.min(Math.max(savedPosition, 0), maximumScrollTop)
            : 0;
        messages.style.scrollBehavior = previousScrollBehavior;
        updateActiveNavigatorMarker();
    }

    function escapeHtml(value) {
        return value.replace(/[&<>"]/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[character]);
    }

    function renderMarkdown(content) {
        if (window.marked && window.DOMPurify) {
            return window.DOMPurify.sanitize(window.marked.parse(content, { gfm: true, breaks: true }));
        }
        return escapeHtml(content).replace(/\n/g, '<br>');
    }

    function createAssistantText(content = '') {
        const text = document.createElement('div');
        text.className = 'assistant-markdown';
        updateAssistantText(text, content);
        return text;
    }

    function updateAssistantText(element, content) {
        element.dataset.raw = content;
        element.innerHTML = renderMarkdown(content);
    }

    function formatMessageTime(value) {
        const time = value ? new Date(value) : new Date();
        return Number.isNaN(time.getTime()) ? '' : time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    }

    function addStoredDocumentChip(bubble, filename) {
        const chip = document.createElement('div');
        chip.className = 'message-file-chip';
        chip.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h8l4 4V20a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 5 20V5a1.5 1.5 0 0 1 1-1.5Z"/><path d="M14 3.5V8h4"/></svg>';
        const name = document.createElement('span');
        name.textContent = filename;
        name.title = filename;
        chip.append(name);
        bubble.append(chip);
    }

    function addMessage(role, content = '', createdAt = null, attachments = [], shouldScroll = true) {
        const message = document.createElement('div');
        message.className = `message ${role}`;
        const bubble = document.createElement('div');
        bubble.className = 'bubble';

        if (role === 'assistant') {
            const avatar = document.createElement('div');
            avatar.className = 'assistant-avatar';
            avatar.innerHTML = '<svg class="assistant-sparkle" viewBox="0 0 24 24" aria-hidden="true"><path fill="#5146E5" d="M12 3.2C12.54 7.46 15.54 10.46 19.8 11c-4.26.54-7.26 3.54-7.8 7.8-.54-4.26-3.54-7.26-7.8-7.8 4.26-.54 7.26-3.54 7.8-7.8Z"/></svg>';
            const body = document.createElement('div');
            body.className = 'assistant-body';
            const meta = document.createElement('div');
            meta.className = 'assistant-meta';
            const name = document.createElement('strong');
            name.textContent = 'XiaoYi';
            const time = document.createElement('span');
            time.textContent = formatMessageTime(createdAt);
            meta.append(name, time);
            if (content) bubble.append(createAssistantText(content));
            body.append(meta, bubble);
            message.append(avatar, body);
            if (content) addAssistantActions(bubble, content);
        } else {
            bubble.classList.add('user-bubble');
            const storedDocument = content.match(/^\[文档分析：(.+?)]\s*/);
            const messageContent = storedDocument
                ? content.slice(storedDocument[0].length)
                : content.replace(/^\[图片识别]\s*/, '');
            const persistedAttachments = Array.isArray(attachments) ? attachments : [];
            if (persistedAttachments.length) {
                addPersistedAttachmentsToBubble(bubble, persistedAttachments);
            } else if (storedDocument) {
                addStoredDocumentChip(bubble, storedDocument[1]);
            }
            const text = document.createElement('div');
            text.className = 'user-text';
            text.textContent = messageContent;
            bubble.append(text);
            message.append(bubble);
        }

        messages.append(message);
        if (shouldScroll) messages.scrollTop = messages.scrollHeight;
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
        const host = bubble.closest('.assistant-body') || bubble;
        if (host.querySelector('.message-actions')) return;
        const actions = document.createElement('div');
        actions.className = 'message-actions';
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
        actions.append(copyButton, speechButton);
        host.append(actions);
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
        history.forEach(item => addMessage(item.role, item.content, item.createdAt, item.attachments, false));
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

            const originalTitle = conversation.title || '新会话';
            const documentMatch = originalTitle.match(/^(?:\[文档分析[：:]\s*(.+?)]|【文档分析[：:]\s*(.+?)】)/);
            const isDocument = Boolean(documentMatch) || /^(?:\[|【)?文档分析(?:]|】)?/.test(originalTitle);
            const imageRecognitionMatch = originalTitle.match(/^\[图片识别]\s*/);
            const imageGenerationMatch = originalTitle.match(/^\[(?:生图|图片生成)]\s*/);
            const type = isDocument ? 'document'
                : imageRecognitionMatch ? 'image-recognition'
                    : imageGenerationMatch ? 'image-generation'
                        : 'chat';
            const matchedPrefix = documentMatch || imageRecognitionMatch || imageGenerationMatch;
            const titleWithoutPrefix = originalTitle.slice((matchedPrefix || [''])[0].length).trim();
            const documentTitle = documentMatch
                ? (documentMatch[1] || documentMatch[2])
                : originalTitle.replace(/^(?:\[|【)?文档分析(?:]|】)?\s*/, '').trim();
            const displayTitle = type === 'document'
                ? `文档分析 · ${documentTitle || '文档分析'}`
                : type === 'image-recognition'
                    ? `图片识别 · ${titleWithoutPrefix || '图片识别'}`
                    : type === 'image-generation'
                        ? `图片生成 · ${titleWithoutPrefix || '图片生成'}`
                        : originalTitle;

            const selectButton = document.createElement('button');
            selectButton.type = 'button';
            selectButton.className = 'conversation-select';

            const icon = document.createElement('span');
            icon.className = conversation-icon ;
            icon.innerHTML = type === 'document'
                ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h8l4 4V20a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 5 20V5a1.5 1.5 0 0 1 1-1.5Z"/><path d="M14 3.5V8h4M8.5 12h7M8.5 15.5h5"/></svg>'
                : type === 'image-recognition'
                    ? '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8.2" cy="9" r="1.35"/><path d="m4.5 17 4.7-4.5 3.2 2.9 2.2-2.1 4.9 3.7"/></svg>'
                    : type === 'image-generation'
                        ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 1.25 4.75L18 9l-4.75 1.25L12 15l-1.25-4.75L6 9l4.75-1.25L12 3ZM18 15l.7 2.3L21 18l-2.3.7L18 21l-.7-2.3L15 18l2.3-.7L18 15Z"/></svg>'
                        : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 14.5a4 4 0 0 1-4 4H9l-4.5 3v-3.7A4 4 0 0 1 3 14.5v-7a4 4 0 0 1 4-4h9a4 4 0 0 1 4 4Z"/></svg>';
            const title = document.createElement('div');
            title.className = 'conversation-title';
            title.textContent = displayTitle || '新会话';
            title.title = displayTitle || '新会话';
            const time = document.createElement('div');
            time.className = 'conversation-time';
            time.textContent = new Date(conversation.updatedAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
            selectButton.append(icon, title, time);
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
        if (conversationId === activeConversationId) return;
        stopActiveChatGeneration();
        rememberConversationScrollPosition();
        activeConversationId = conversationId;
        localStorage.setItem('xiaoyi-active-conversation', conversationId);
        const response = await fetch(`/chat/conversations/${encodeURIComponent(conversationId)}/messages`);
        renderMessages(await response.json());
        restoreConversationScrollPosition(conversationId);
        await refreshConversations();
    }

    async function createConversation() {
        stopActiveChatGeneration();
        rememberConversationScrollPosition();
        const response = await fetch('/chat/conversations', { method: 'POST' });
        const conversation = await response.json();
        activeConversationId = conversation.id;
        localStorage.setItem('xiaoyi-active-conversation', activeConversationId);
        renderMessages([]);
        await refreshConversations();
        input.focus();
    }

    async function deleteConversation(conversationId) {
        if (conversationId === activeConversationId) stopActiveChatGeneration();
        const response = await fetch(`/chat/conversations/${encodeURIComponent(conversationId)}`, { method: 'DELETE' });
        if (!response.ok) {
            alert('删除失败，请稍后重试。');
            return;
        }

        delete conversationScrollPositions[conversationId];
        saveConversationScrollPositions();
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
        if (mode !== 'chat') stopActiveChatGeneration();
        activeMode = mode;
        const imageMode = mode === 'image';
        document.body.classList.toggle('image-mode', imageMode);
        messages.hidden = imageMode;
        imageResults.hidden = !imageMode;
        input.placeholder = imageMode ? '描述你想生成的图片' : '问问XiaoYi';
        attachmentButton.disabled = imageMode;
        if (imageMode) clearAttachments();
        sendButton.innerHTML = imageMode ? '生成图片' : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 19V5M6.5 10.5 12 5l5.5 5.5"/></svg>';
        sendButton.setAttribute('aria-label', imageMode ? '生成图片' : '发送消息');
        sendButton.title = imageMode ? '生成图片' : '发送消息';
        modeButtons.forEach(button => button.classList.toggle('active', button.dataset.mode === mode));
        setChatGenerationState(activeChatRequest);
        input.focus();
    }

    function setChatGenerationState(request) {
        const generating = Boolean(request);
        stopGenerationButton.classList.toggle('visible', generating && activeMode === 'chat');
        stopGenerationButton.disabled = !generating;
    }

    function stopActiveChatGeneration() {
        if (activeChatRequest && !activeChatRequest.controller.signal.aborted) {
            activeChatRequest.controller.abort();
            stopGenerationButton.disabled = true;
        }
    }


    const MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024;
    const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);
    const DOCUMENT_EXTENSIONS = new Set(['pdf', 'docx', 'pptx', 'txt', 'md']);

    function isImageAttachment(file) { return IMAGE_TYPES.has(file.type); }
    function extensionOf(file) { const parts = (file.name || '').split('.'); return parts.length > 1 ? parts.pop().toLowerCase() : ''; }
    function isSupportedAttachment(file) { return isImageAttachment(file) || DOCUMENT_EXTENSIONS.has(extensionOf(file)); }
    function normaliseAttachmentName(file) {
        if (file.name && file.name !== 'image.png') return file;
        const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);
        const extension = file.type === 'image/jpeg' ? 'jpg' : file.type === 'image/gif' ? 'gif' : file.type === 'image/webp' ? 'webp' : 'png';
        return new File([file], `pasted-image-${timestamp}.${extension}`, { type: file.type, lastModified: file.lastModified || Date.now() });
    }
    function attachmentKey(file) { return `${file.name}|${file.size}|${file.lastModified}`; }
    function addAttachment(file) {
        if (!file) return false;
        const attachment = normaliseAttachmentName(file);
        if (!isSupportedAttachment(attachment)) { alert('仅支持 JPG、PNG、WEBP、GIF、PDF、DOCX、PPTX、TXT 或 Markdown 文件。'); return false; }
        if (attachment.size > MAX_ATTACHMENT_SIZE) { alert(`“${attachment.name}” 超过 10MB，暂时无法上传。`); return false; }
        if (pendingAttachments.some(item => attachmentKey(item.file) === attachmentKey(attachment))) return false;
        const isImage = isImageAttachment(attachment);
        if (pendingAttachments.length && isImageAttachment(pendingAttachments[0].file) !== isImage) { alert('请将图片和文档分开发送，便于小艺使用合适的分析能力。'); return false; }
        pendingAttachments.push({ id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`, file: attachment, previewUrl: isImage ? URL.createObjectURL(attachment) : null });
        renderAttachmentPreview(); input.focus(); return true;
    }
    function removeAttachment(id) {
        const attachment = pendingAttachments.find(item => item.id === id);
        if (attachment?.previewUrl) URL.revokeObjectURL(attachment.previewUrl);
        pendingAttachments = pendingAttachments.filter(item => item.id !== id);
        renderAttachmentPreview();
    }
    function clearAttachments() {
        pendingAttachments.forEach(item => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl); });
        pendingAttachments = []; attachmentInput.value = ''; renderAttachmentPreview();
    }
    function renderAttachmentPreview() {
        attachmentPreview.replaceChildren(); attachmentPreview.hidden = pendingAttachments.length === 0;
        attachmentButton.classList.toggle('attached', pendingAttachments.length > 0);
        attachmentButton.title = pendingAttachments.length ? `已添加 ${pendingAttachments.length} 个附件` : '添加附件';
        pendingAttachments.forEach(attachment => {
            const card = document.createElement('div'); const image = isImageAttachment(attachment.file);
            card.className = `pending-attachment${image ? '' : ' document'}`; card.dataset.attachmentId = attachment.id;
            if (image) { const preview = document.createElement('img'); preview.src = attachment.previewUrl; preview.alt = `待发送：${attachment.file.name}`; card.append(preview); }
            else { const type = document.createElement('strong'); type.textContent = extensionOf(attachment.file).toUpperCase(); const name = document.createElement('span'); name.textContent = attachment.file.name; name.title = attachment.file.name; card.append(type, name); }
            const remove = document.createElement('button'); remove.type = 'button'; remove.setAttribute('aria-label', `移除 ${attachment.file.name}`); remove.title = '移除附件'; remove.textContent = '×'; remove.addEventListener('click', () => removeAttachment(attachment.id)); card.append(remove); attachmentPreview.append(card);
        });
    }
    function setAttachmentBusy(busy) {
        attachmentButton.disabled = busy || activeMode === 'image';
        attachmentPreview.querySelectorAll('button').forEach(button => button.disabled = busy);
        attachmentPreview.querySelectorAll('.pending-attachment').forEach(card => card.classList.toggle('uploading', busy));
    }
    function addPersistedAttachmentsToBubble(bubble, attachments) {
        [...attachments].reverse().forEach(attachment => {
            const file = { name: attachment.originalName };
            if (attachment.fileType?.startsWith('image/')) {
                const preview = document.createElement('img');
                preview.className = 'message-image-preview';
                preview.src = attachment.url;
                preview.alt = `已上传：${attachment.originalName}`;
                preview.loading = 'lazy';
                bubble.prepend(preview);
            } else {
                addDocumentAttachmentToBubble(bubble, file);
            }
        });
    }
    function addDocumentAttachmentToBubble(bubble, file) {
        const documentPreview = document.createElement('div');
        documentPreview.className = 'message-document-preview';
        const type = document.createElement('strong');
        type.textContent = file.name.split('.').pop().toUpperCase();
        const name = document.createElement('span');
        name.textContent = file.name;
        name.title = file.name;
        documentPreview.append(type, name);
        bubble.prepend(documentPreview);
    }

    function addPendingAttachmentsToBubble(bubble, attachments) {
        [...attachments].reverse().forEach(attachment => {
            if (isImageAttachment(attachment.file)) {
                const preview = document.createElement('img');
                const messagePreviewUrl = URL.createObjectURL(attachment.file);
                messagePreviewUrls.add(messagePreviewUrl);
                preview.className = 'message-image-preview';
                preview.src = messagePreviewUrl;
                preview.alt = `已上传：${attachment.file.name}`;
                bubble.prepend(preview);
            } else {
                addDocumentAttachmentToBubble(bubble, attachment.file);
            }
        });
    }

    async function analyzeAttachment(question) {
        const attachments = [...pendingAttachments]; if (!attachments.length) return;
        const files = attachments.map(item => item.file); const imageAttachment = files.every(isImageAttachment);
        const displayQuestion = question || (imageAttachment ? '请识别我上传的图片' : '请概括这些文档的重点');
        if (messages.querySelector('.empty')) messages.replaceChildren();
        const userBubble = addMessage('user', displayQuestion);
        addPendingAttachmentsToBubble(userBubble, attachments);
        clearAttachments();
        input.value = '';
        input.disabled = true;
        setAttachmentBusy(true);
        sendButton.disabled = true;
        stopCurrentSpeech();
        const answer = addMessage('assistant'); showSpinner(answer);
        const data = new FormData(); files.forEach(file => data.append('file', file)); data.append('prompt', displayQuestion);
        const endpoint = imageAttachment ? '/vision/recognize' : '/documents/analyze'; const failureLabel = imageAttachment ? '图片识别' : '文档分析';
        let receivedContent = false; let answerText = null;
        try {
            const response = await fetch(`${endpoint}?conversationId=${encodeURIComponent(activeConversationId)}`, { method: 'POST', body: data });
            if (!response.ok || !response.body) throw new Error(`请求失败：${response.status}`);
            const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
            while (true) { const { done, value } = await reader.read(); if (value) buffer += decoder.decode(value, { stream: true }); if (done) buffer += decoder.decode(); buffer = appendSseEvents(buffer, content => { if (!receivedContent) { answer.classList.remove('thinking'); answer.replaceChildren(); answerText = createAssistantText(''); answer.append(answerText); receivedContent = true; } updateAssistantText(answerText, `${answerText.dataset.raw || ''}${content}`); messages.scrollTop = messages.scrollHeight; }); if (done) break; }
        } catch (error) { answer.classList.remove('thinking'); answer.textContent = `${failureLabel}失败：${error.message}`; }
        finally { if (receivedContent) addAssistantActions(answer, () => answerText.dataset.raw || ''); clearAttachments(); input.disabled = false; setAttachmentBusy(false); sendButton.disabled = false; input.focus(); await refreshConversations(); }
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

    messages.addEventListener('scroll', () => {
        rememberConversationScrollPosition();
        updateActiveNavigatorMarker();
    });
    newChatButton.addEventListener('click', createConversation);
    modeButtons.forEach(button => button.addEventListener('click', () => setMode(button.dataset.mode)));
    attachmentButton.addEventListener('click', () => attachmentInput.click());
    attachmentInput.addEventListener('change', () => { [...attachmentInput.files].forEach(addAttachment); attachmentInput.value = ''; });
    input.addEventListener('paste', event => {
        const files = [...(event.clipboardData?.items || [])].filter(item => item.kind === 'file' && item.type.startsWith('image/')).map(item => item.getAsFile()).filter(Boolean);
        if (!files.length) return;
        event.preventDefault(); files.forEach(addAttachment);
    });
    let dragDepth = 0;
    const isFileDrag = event => [...(event.dataTransfer?.types || [])].includes('Files');
    form.addEventListener('dragenter', event => { if (!isFileDrag(event) || activeMode === 'image') return; event.preventDefault(); dragDepth += 1; form.classList.add('drag-over'); });
    form.addEventListener('dragover', event => { if (!isFileDrag(event) || activeMode === 'image') return; event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; form.classList.add('drag-over'); });
    form.addEventListener('dragleave', event => { if (!isFileDrag(event)) return; dragDepth = Math.max(0, dragDepth - 1); if (!dragDepth) form.classList.remove('drag-over'); });
    form.addEventListener('drop', event => { if (!isFileDrag(event) || activeMode === 'image') return; event.preventDefault(); dragDepth = 0; form.classList.remove('drag-over'); [...event.dataTransfer.files].filter(file => file.type.startsWith('image/')).forEach(addAttachment); });
    window.addEventListener('beforeunload', () => {
        pendingAttachments.forEach(item => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl); });
        messagePreviewUrls.forEach(url => URL.revokeObjectURL(url));
    });
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const question = input.value.trim();
        const attachedFiles = pendingAttachments;
        if (!question && !attachedFiles.length) return;
        if (activeMode === 'chat' && !activeConversationId) {
            try {
                await createConversation();
            } catch (error) {
                alert(`无法创建会话：${error.message}`);
                return;
            }
        }
        if (activeMode === 'image') {
            if (!question) return;
            await generateImage(question);
            return;
        }
        if (attachedFiles.length) {
            stopActiveChatGeneration();
            await analyzeAttachment(question);
            return;
        }
        if (messages.querySelector('.empty')) messages.replaceChildren();
        // A new instruction supersedes the visible stream. Its finally handler is
        // guarded by request identity, so it cannot reset this newer request.
        stopActiveChatGeneration();
        const request = { id: ++chatRequestSequence, controller: new AbortController() };
        activeChatRequest = request;
        setChatGenerationState(request);
        addMessage('user', question);
        input.value = '';
        stopCurrentSpeech();
        const answer = addMessage('assistant');
        showSpinner(answer);
        let receivedContent = false;
        let answerText = null;

        try {
            const response = await fetch(`/chat/stream?message=${encodeURIComponent(question)}&conversationId=${encodeURIComponent(activeConversationId)}`, {
                signal: request.controller.signal
            });
            if (!response.ok || !response.body) throw new Error(`请求失败：${response.status}`);
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            while (true) {
                const { done, value } = await reader.read();
                if (value) buffer += decoder.decode(value, { stream: true });
                if (done) buffer += decoder.decode();
                buffer = appendSseEvents(buffer, (content) => {
                    if (activeChatRequest !== request) return;
                    if (!receivedContent) {
                        answer.classList.remove('thinking');
                        answer.replaceChildren();
                        answerText = createAssistantText('');
                        answer.append(answerText);
                        receivedContent = true;
                    }
                    updateAssistantText(answerText, `${answerText.dataset.raw || ''}${content}`);
                    messages.scrollTop = messages.scrollHeight;
                });
                if (done) break;
            }
        } catch (error) {
            answer.classList.remove('thinking');
            answer.textContent = error.name === 'AbortError'
                ? '已停止生成。'
                : `抱歉，暂时无法获取回答：${error.message}`;
        } finally {
            if (receivedContent) addAssistantActions(answer, () => answerText.dataset.raw || '');
            if (activeChatRequest === request) {
                activeChatRequest = null;
                setChatGenerationState(null);
                input.focus();
                await refreshConversations();
            }
        }
    });

    stopGenerationButton.addEventListener('click', stopActiveChatGeneration);

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
