(() => {
    const account = document.querySelector('#account');
    const password = document.querySelector('#password');
    const passwordToggle = document.querySelector('.password-toggle');
    const form = document.querySelector('.login-card');
    const hint = document.querySelector('.demo-hint');
    const get = (id) => document.getElementById(id);
    const scene = {
        purple: get('char-purple'), black: get('char-black'), orange: get('char-orange'), yellow: get('char-yellow'),
        purpleEyes: get('purple-eyes'), blackEyes: get('black-eyes'), orangeEyes: get('orange-eyes'), yellowEyes: get('yellow-eyes'),
        yellowMouth: get('yellow-mouth')
    };
    let mouseX = 0;
    let mouseY = 0;
    let isTyping = false;
    let isLookingAtEachOther = false;
    let isPurpleBlinking = false;
    let isBlackBlinking = false;
    let isPurplePeeking = false;
    let showPassword = false;
    let typingTimer = 0;
    let peekTimer = 0;
    let peekCloseTimer = 0;

    const randomBlink = () => Math.random() * 4000 + 3000;
    const setPosition = (element, left, top) => { element.style.left = `${left}px`; element.style.top = `${top}px`; };
    const positionOf = (element) => {
        const rect = element.getBoundingClientRect();
        const deltaX = mouseX - (rect.left + rect.width / 2);
        const deltaY = mouseY - (rect.top + rect.height / 3);
        return { faceX: Math.max(-15, Math.min(15, deltaX / 20)), faceY: Math.max(-10, Math.min(10, deltaY / 30)), bodySkew: Math.max(-6, Math.min(6, -deltaX / 120)) };
    };
    const lookAt = (element, maxDistance, forceX, forceY) => {
        if (forceX !== undefined && forceY !== undefined) return { x: forceX, y: forceY };
        const rect = element.getBoundingClientRect();
        const deltaX = mouseX - (rect.left + rect.width / 2);
        const deltaY = mouseY - (rect.top + rect.height / 2);
        const distance = Math.min(Math.hypot(deltaX, deltaY), maxDistance);
        const angle = Math.atan2(deltaY, deltaX);
        return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance };
    };
    const paintEye = (eyeId, pupilId, size, pupilSize, maxDistance, blinking, forceX, forceY) => {
        const eye = get(eyeId);
        const pupil = get(pupilId);
        eye.style.width = `${size}px`;
        eye.style.height = blinking ? '2px' : `${size}px`;
        pupil.style.width = `${pupilSize}px`;
        pupil.style.height = `${pupilSize}px`;
        pupil.style.display = blinking ? 'none' : 'block';
        if (!blinking) {
            const point = lookAt(eye, maxDistance, forceX, forceY);
            pupil.style.transform = `translate(${point.x}px, ${point.y}px)`;
        }
    };
    const paintPupil = (id, maxDistance, forceX, forceY) => {
        const pupil = get(id);
        const point = lookAt(pupil, maxDistance, forceX, forceY);
        pupil.style.transform = `translate(${point.x}px, ${point.y}px)`;
    };
    const updateScene = () => {
        const purple = positionOf(scene.purple);
        const black = positionOf(scene.black);
        const orange = positionOf(scene.orange);
        const yellow = positionOf(scene.yellow);
        const length = password.value.length;
        const isShowingPassword = length > 0 && showPassword;
        const isHidingPassword = length > 0 && !showPassword;

        scene.purple.style.height = isTyping || isHidingPassword ? '440px' : '400px';
        scene.purple.style.transform = isShowingPassword ? 'skewX(0deg)' : (isTyping || isHidingPassword ? `skewX(${purple.bodySkew - 12}deg) translateX(40px)` : `skewX(${purple.bodySkew}deg)`);
        setPosition(scene.purpleEyes, isShowingPassword ? 20 : (isLookingAtEachOther ? 55 : 45 + purple.faceX), isShowingPassword ? 35 : (isLookingAtEachOther ? 65 : 40 + purple.faceY));
        const purpleX = isShowingPassword ? (isPurplePeeking ? 4 : -4) : (isLookingAtEachOther ? 3 : undefined);
        const purpleY = isShowingPassword ? (isPurplePeeking ? 5 : -4) : (isLookingAtEachOther ? 4 : undefined);
        paintEye('purple-eye-l', 'purple-pupil-l', 18, 7, 5, isPurpleBlinking, purpleX, purpleY);
        paintEye('purple-eye-r', 'purple-pupil-r', 18, 7, 5, isPurpleBlinking, purpleX, purpleY);

        scene.black.style.transform = isShowingPassword ? 'skewX(0deg)' : (isLookingAtEachOther ? `skewX(${black.bodySkew * 1.5 + 10}deg) translateX(20px)` : ((isTyping || isHidingPassword) ? `skewX(${black.bodySkew * 1.5}deg)` : `skewX(${black.bodySkew}deg)`));
        setPosition(scene.blackEyes, isShowingPassword ? 10 : (isLookingAtEachOther ? 32 : 26 + black.faceX), isShowingPassword ? 28 : (isLookingAtEachOther ? 12 : 32 + black.faceY));
        const blackX = isShowingPassword ? -4 : (isLookingAtEachOther ? 0 : undefined);
        const blackY = isShowingPassword ? -4 : (isLookingAtEachOther ? -4 : undefined);
        paintEye('black-eye-l', 'black-pupil-l', 16, 6, 4, isBlackBlinking, blackX, blackY);
        paintEye('black-eye-r', 'black-pupil-r', 16, 6, 4, isBlackBlinking, blackX, blackY);

        scene.orange.style.transform = isShowingPassword ? 'skewX(0deg)' : `skewX(${orange.bodySkew}deg)`;
        setPosition(scene.orangeEyes, isShowingPassword ? 50 : 82 + orange.faceX, isShowingPassword ? 85 : 90 + orange.faceY);
        paintPupil('orange-pupil-l', 5, isShowingPassword ? -5 : undefined, isShowingPassword ? -4 : undefined);
        paintPupil('orange-pupil-r', 5, isShowingPassword ? -5 : undefined, isShowingPassword ? -4 : undefined);

        scene.yellow.style.transform = isShowingPassword ? 'skewX(0deg)' : `skewX(${yellow.bodySkew}deg)`;
        setPosition(scene.yellowEyes, isShowingPassword ? 20 : 52 + yellow.faceX, isShowingPassword ? 35 : 40 + yellow.faceY);
        paintPupil('yellow-pupil-l', 5, isShowingPassword ? -5 : undefined, isShowingPassword ? -4 : undefined);
        paintPupil('yellow-pupil-r', 5, isShowingPassword ? -5 : undefined, isShowingPassword ? -4 : undefined);
        setPosition(scene.yellowMouth, isShowingPassword ? 10 : 40 + yellow.faceX, isShowingPassword ? 88 : 88 + yellow.faceY);
    };
    const startBlink = (name) => {
        window.setTimeout(() => {
            if (name === 'purple') isPurpleBlinking = true; else isBlackBlinking = true;
            updateScene();
            window.setTimeout(() => {
                if (name === 'purple') isPurpleBlinking = false; else isBlackBlinking = false;
                updateScene();
                startBlink(name);
            }, 150);
        }, randomBlink());
    };
    const schedulePeek = () => {
        window.clearTimeout(peekTimer);
        window.clearTimeout(peekCloseTimer);
        isPurplePeeking = false;
        if (!(password.value.length > 0 && showPassword)) return;
        peekTimer = window.setTimeout(() => {
            isPurplePeeking = true;
            updateScene();
            peekCloseTimer = window.setTimeout(() => {
                isPurplePeeking = false;
                updateScene();
                schedulePeek();
            }, 800);
        }, Math.random() * 3000 + 2000);
    };
    const beginTyping = () => {
        isTyping = true;
        isLookingAtEachOther = true;
        window.clearTimeout(typingTimer);
        typingTimer = window.setTimeout(() => { isLookingAtEachOther = false; updateScene(); }, 800);
        updateScene();
    };

    document.addEventListener('mousemove', (event) => { mouseX = event.clientX; mouseY = event.clientY; updateScene(); });
    account.addEventListener('focus', beginTyping);
    account.addEventListener('blur', () => { isTyping = false; isLookingAtEachOther = false; updateScene(); });
    account.addEventListener('input', beginTyping);
    password.addEventListener('input', () => { schedulePeek(); updateScene(); });
    passwordToggle.addEventListener('click', () => {
        showPassword = !showPassword;
        password.type = showPassword ? 'text' : 'password';
        passwordToggle.setAttribute('aria-pressed', String(showPassword));
        passwordToggle.setAttribute('aria-label', showPassword ? '隐藏密码' : '显示密码');
        schedulePeek();
        updateScene();
    });
    form.addEventListener('submit', (event) => { event.preventDefault(); hint.textContent = '登录功能将在后续接入账号服务。'; });
    startBlink('purple');
    startBlink('black');
    updateScene();
})();