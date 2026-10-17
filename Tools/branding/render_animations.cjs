// Run in CI with lottie-web and Playwright to exercise the real vector renderer.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const output = path.resolve(process.argv[2]);
    fs.mkdirSync(output, { recursive: true });
    const browser = await chromium.launch();
    try {
        const page = await browser.newPage({ viewport: { width: 1100, height: 1100 } });
        await page.setContent('<style>html,body{margin:0}#animation{width:512px;height:512px}</style><div id="animation"></div>');
        await page.addScriptTag({ path: require.resolve('lottie-web/build/player/lottie.min.js') });
        for (const name of ['plane_logo_plain', 'qr_code_logo', 'qr_code_logo_2']) {
            const data = JSON.parse(fs.readFileSync(path.join(__dirname, '../../TMessagesProj/src/main/res/raw', name + '.json'), 'utf8'));
            assert.equal(data.fr, 60);
            assert.equal(data.w, 512);
            assert.equal(data.h, 512);
            assert.equal(data.ip, 20);
            assert.equal(data.op, 74);
            assert.deepEqual(data.assets, []);
            await page.evaluate(data => new Promise(resolve => {
                if (window.animation) window.animation.destroy();
                const container = document.getElementById('animation');
                container.style.width = container.style.height = '512px';
                window.animation = lottie.loadAnimation({ container, renderer: 'svg', loop: false, autoplay: false, animationData: data });
                window.animation.addEventListener('DOMLoaded', resolve);
            }), data);
            const snapshots = [];
            for (const frame of [0, 27, 53]) {
                await page.evaluate(frame => window.animation.goToAndStop(frame, true), frame);
                snapshots.push(await page.locator('#animation').screenshot({ path: path.join(output, `${name}-${frame}.png`), omitBackground: true }));
            }
            assert.notDeepEqual(snapshots[0], snapshots[1], `${name} must animate`);
            assert.notDeepEqual(snapshots[1], snapshots[2], `${name} must animate through its full timeline`);
            await page.evaluate(() => {
                const container = document.getElementById('animation');
                container.style.width = container.style.height = '1024px';
                window.animation.resize();
                window.animation.goToAndStop(27, true);
            });
            await page.locator('#animation').screenshot({ path: path.join(output, `${name}-1024.png`), omitBackground: true });
            if (name === 'qr_code_logo') {
                await page.evaluate(() => {
                    const container = document.getElementById('animation');
                    container.style.width = container.style.height = '512px';
                    window.animation.resize();
                });
                for (let frame = 0; frame < data.op - data.ip; frame++) {
                    await page.evaluate(frame => window.animation.goToAndStop(frame, true), frame);
                    await page.locator('#animation').screenshot({ path: path.join(output, `frame-${String(frame).padStart(2, '0')}.png`), omitBackground: true });
                }
            }
        }
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
