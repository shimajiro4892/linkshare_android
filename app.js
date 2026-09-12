import {
  buildLineShareUrl as buildLineItShareUrl,
  buildXShareUrl,
  describeShareUrl,
  formatShareText,
  shareTitle
} from './share-utils.js';
import { parseSharedSearch } from './share-target.js';

const SLACK_NOTICE_MS = 800;
const LINE_PACKAGE = 'jp.naver.line.android';
const SLACK_PACKAGE = 'com.Slack';

const state = {
  url: '',
  shareUrl: '',
  shareable: false
};

const elements = {
  shareView: document.getElementById('share-view'),
  homeView: document.getElementById('home-view'),
  pageTitle: document.getElementById('page-title'),
  pageUrl: document.getElementById('page-url'),
  urlStatus: document.getElementById('url-status'),
  shareIcons: document.getElementById('share-icons'),
  xButton: document.getElementById('share-x'),
  lineButton: document.getElementById('share-line'),
  slackButton: document.getElementById('share-slack'),
  copyButton: document.getElementById('btn-copy'),
  moreButton: document.getElementById('btn-more'),
  diagnosticList: document.getElementById('diagnostic-list'),
  installTitle: document.getElementById('install-title'),
  installBody: document.getElementById('install-body'),
  installButton: document.getElementById('btn-install'),
  manualForm: document.getElementById('manual-form'),
  manualUrl: document.getElementById('manual-url'),
  manualTitle: document.getElementById('manual-title'),
  toast: document.getElementById('toast')
};

let toastTimer;
let shareTargetOpening = false;
let installPrompt = null;

function getTitle() {
  return elements.pageTitle.value.trim();
}

function getShareText() {
  return formatShareText(getTitle(), state.shareUrl);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function showToast(message) {
  clearTimeout(toastTimer);
  elements.toast.textContent = message;
  elements.toast.classList.add('show');
  toastTimer = setTimeout(() => elements.toast.classList.remove('show'), 2500);
}

function setShareButtonsEnabled(enabled) {
  for (const button of [
    elements.xButton,
    elements.lineButton,
    elements.slackButton,
    elements.copyButton,
    elements.moreButton
  ]) {
    button.disabled = !enabled;
  }
}

// ---- 共有先URL（Android向け） ----

// LINEは公式のURLスキーム。Androidではアプリリンクとして LINE アプリが開く。
function buildLineShareUrl(text) {
  return `https://line.me/R/share?text=${encodeURIComponent(text)}`;
}

function buildLineSchemeUrl(text) {
  return `line://msg/text/${encodeURIComponent(text)}`;
}

// Android Chrome の intent: URL。アプリが入っていれば直接開き、なければ fallback に飛ぶ。
function buildIntentUrl({ schemeUrl, packageName, fallbackUrl }) {
  const { protocol, href } = new URL(schemeUrl);
  const scheme = protocol.slice(0, -1);
  const rest = href.slice(protocol.length + 2); // "scheme://" を除いた部分
  const params = [
    `scheme=${scheme}`,
    `package=${packageName}`,
    fallbackUrl ? `S.browser_fallback_url=${encodeURIComponent(fallbackUrl)}` : ''
  ].filter(Boolean);
  return `intent://${rest}#Intent;${params.join(';')};end`;
}

function buildLineIntentUrl(text) {
  return buildIntentUrl({
    schemeUrl: buildLineSchemeUrl(text),
    packageName: LINE_PACKAGE,
    fallbackUrl: buildLineShareUrl(text)
  });
}

function buildSlackOpenUrl() {
  return 'slack://open';
}

function buildSlackIntentUrl() {
  return buildIntentUrl({
    schemeUrl: buildSlackOpenUrl(),
    packageName: SLACK_PACKAGE,
    fallbackUrl: 'https://app.slack.com/'
  });
}

// ---- 動作 ----

async function copyShareText(successMessage = 'クリップボードにコピーしました') {
  try {
    await navigator.clipboard.writeText(getShareText());
    showToast(successMessage);
    return true;
  } catch (error) {
    console.error('コピーに失敗しました。', error);
    showToast('コピーできませんでした');
    return false;
  }
}

async function systemShare() {
  if (!navigator.share) {
    showToast('この端末では使えません');
    return false;
  }
  try {
    // url を別に渡すとアプリによってタイトルが落ちるので、整形済みテキストだけを渡す。
    await navigator.share({ text: getShareText() });
    return true;
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('共有シートを開けませんでした。', error);
      showToast('共有シートを開けませんでした');
    }
    return false;
  }
}

// 同じウィンドウで遷移させる。Androidはユーザー操作直後の遷移でアプリリンクや
// カスタムスキームを処理するので、window.open ではなく location を使う。
function navigateTo(url) {
  window.location.assign(url);
}

async function openShareTarget(url, beforeOpen) {
  if (shareTargetOpening) {
    return;
  }

  shareTargetOpening = true;
  setShareButtonsEnabled(false);

  const ready = beforeOpen ? await beforeOpen() : true;
  if (ready) {
    navigateTo(url);
  }

  // 遷移しなかった（アプリが開かない等）場合に操作を戻せるように、少し待ってから有効化する。
  await delay(1500);
  shareTargetOpening = false;
  setShareButtonsEnabled(true);
}

function copyThenOpenSlack(url) {
  return openShareTarget(url, async () => {
    if (!await copyShareText('コピーしました。Slackに貼り付けてください')) {
      return false;
    }
    await delay(SLACK_NOTICE_MS);
    return true;
  });
}

// ---- 診断メニュー（LINE の開き方を切り分ける） ----

function renderDiagnostics() {
  const items = [
    { label: 'https://line.me/R/share（現行）', run: () => navigateTo(buildLineShareUrl(getShareText())) },
    { label: 'line://msg/text/', run: () => navigateTo(buildLineSchemeUrl(getShareText())) },
    { label: 'intent://（アプリ指定、なければ1へ）', run: () => navigateTo(buildLineIntentUrl(getShareText())) },
    { label: 'LINE it!（拡張機能と同じ）', run: () => navigateTo(buildLineItShareUrl(getTitle(), state.shareUrl)) },
    { label: 'Androidの共有シート', run: () => { void systemShare(); } },
    { label: 'Slack を intent:// で開く', run: () => { void copyThenOpenSlack(buildSlackIntentUrl()); } }
  ];

  elements.diagnosticList.replaceChildren(...items.map(({ label, run }) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = label;
    button.addEventListener('click', run);
    const item = document.createElement('li');
    item.append(button);
    return item;
  }));
}

// ---- 画面 ----

function showShareView(shared) {
  elements.shareView.hidden = false;
  elements.homeView.hidden = true;

  const { shareable, shareUrl, status } = describeShareUrl(shared.url);
  state.url = shared.url;
  state.shareUrl = shareUrl;
  state.shareable = shareable;

  elements.pageTitle.value = shared.title;
  elements.pageUrl.textContent = shareUrl || '(URLなし)';
  elements.urlStatus.textContent = shared.url ? status : '共有されたデータにURLが含まれていません';

  if (!shareable) {
    elements.pageUrl.removeAttribute('href');
    elements.urlStatus.classList.add('error');
    return;
  }

  elements.pageUrl.href = shareUrl;
  elements.moreButton.hidden = !navigator.share;
  setShareButtonsEnabled(true);
  renderDiagnostics();
}

function isStandalone() {
  return window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
}

function showHomeView() {
  elements.shareView.hidden = true;
  elements.homeView.hidden = false;

  if (isStandalone()) {
    elements.installTitle.textContent = '1. インストール済み';
    elements.installBody.textContent = 'このアプリはインストールされています。Chromeの共有メニューに「リンクシェア」が表示されます。';
  }
}

function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) {
    return;
  }
  navigator.serviceWorker.register('sw.js').catch((error) => {
    console.error('Service Workerを登録できませんでした。', error);
  });
}

// ---- イベント ----

elements.xButton.addEventListener('click', () => {
  void openShareTarget(buildXShareUrl(getTitle(), state.shareUrl));
});

elements.lineButton.addEventListener('click', () => {
  void openShareTarget(buildLineShareUrl(getShareText()));
});

elements.slackButton.addEventListener('click', () => {
  void copyThenOpenSlack(buildSlackOpenUrl());
});

elements.copyButton.addEventListener('click', () => {
  void copyShareText();
});

elements.moreButton.addEventListener('click', () => {
  void systemShare();
});

window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault();
  installPrompt = event;
  elements.installButton.hidden = false;
});

elements.installButton.addEventListener('click', async () => {
  if (!installPrompt) {
    return;
  }
  const prompt = installPrompt;
  installPrompt = null;
  elements.installButton.hidden = true;
  await prompt.prompt();
});

window.addEventListener('appinstalled', () => {
  showToast('インストールしました。共有メニューから使えます');
  elements.installButton.hidden = true;
});

elements.manualForm.addEventListener('submit', (event) => {
  event.preventDefault();
  const params = new URLSearchParams({
    title: elements.manualTitle.value.trim(),
    url: elements.manualUrl.value.trim()
  });
  window.location.assign(`./?${params}`);
});

// ---- 起動 ----

const shared = parseSharedSearch(window.location.search);
if (shared.received) {
  showShareView(shared);
} else {
  showHomeView();
}
registerServiceWorker();
