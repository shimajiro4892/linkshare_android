const FALLBACK_TITLE = '(タイトルなし)';

export function shareTitle(title) {
  return title || FALLBACK_TITLE;
}

export function isShareableUrl(value) {
  try {
    return ['http:', 'https:'].includes(new URL(value).protocol);
  } catch {
    return false;
  }
}

// Amazonストアのドメインだけを対象にする。amazon.jobs のような非ストアドメインや、
// www.amazon.co.jp.example.com のような偽装ドメインを除外するためサフィックスで判定する。
const AMAZON_STORE_SUFFIXES = [
  'amazon.ae',
  'amazon.ca',
  'amazon.cn',
  'amazon.co.jp',
  'amazon.co.uk',
  'amazon.co.za',
  'amazon.com',
  'amazon.com.au',
  'amazon.com.be',
  'amazon.com.br',
  'amazon.com.mx',
  'amazon.com.tr',
  'amazon.de',
  'amazon.eg',
  'amazon.es',
  'amazon.fr',
  'amazon.ie',
  'amazon.in',
  'amazon.it',
  'amazon.nl',
  'amazon.pl',
  'amazon.sa',
  'amazon.se',
  'amazon.sg'
];

// asinクエリを信頼できる商品ページのパス。/hz/mobile/mission?asin=... のような
// 商品ページ以外のURLを /dp/ に書き換えてしまわないように限定する。
const AMAZON_ASIN_QUERY_PATHS = [
  /^\/dp(?:\/|$)/i,
  /^\/gp\/product(?:\/|$)/i,
  /^\/gp\/aw\/d(?:\/|$)/i,
  /^\/gp\/offer-listing(?:\/|$)/i
];

const AMAZON_ASIN_PATHS = [
  /\/dp\/([A-Z0-9]{10})(?:[/?#]|$)/i,
  /\/gp\/product\/([A-Z0-9]{10})(?:[/?#]|$)/i,
  /\/gp\/aw\/d\/([A-Z0-9]{10})(?:[/?#]|$)/i,
  /\/exec\/obidos\/ASIN\/([A-Z0-9]{10})(?:[/?#]|$)/i
];

function isAmazonHost(hostname) {
  const host = hostname.toLowerCase();
  return AMAZON_STORE_SUFFIXES.some(
    (suffix) => host === suffix || host.endsWith(`.${suffix}`)
  );
}

function getAmazonAsin(url) {
  for (const pattern of AMAZON_ASIN_PATHS) {
    const match = url.pathname.match(pattern);
    if (match) {
      return match[1].toUpperCase();
    }
  }

  if (!AMAZON_ASIN_QUERY_PATHS.some((pattern) => pattern.test(url.pathname))) {
    return null;
  }

  const asin = url.searchParams.get('asin');
  return /^[A-Z0-9]{10}$/i.test(asin || '') ? asin.toUpperCase() : null;
}

function normalizeAmazonUrl(url) {
  if (!isAmazonHost(url.hostname)) {
    return url.href;
  }

  const asin = getAmazonAsin(url);
  if (!asin) {
    return url.href;
  }

  const normalized = new URL(url.origin);
  normalized.pathname = `/dp/${asin}`;

  const affiliateTag = url.searchParams.get('tag');
  if (affiliateTag) {
    normalized.searchParams.set('tag', affiliateTag);
  }

  // バリエーション指定は落とすと共有先で別の商品が開くため残す。
  const variation = url.searchParams.get('th');
  if (variation) {
    normalized.searchParams.set('th', variation);
    const variationSelected = url.searchParams.get('psc');
    if (variationSelected) {
      normalized.searchParams.set('psc', variationSelected);
    }
  }

  // フラグメントは他サイトのURLと同様に維持する（#customerReviews など）。
  normalized.hash = url.hash;

  return normalized.href;
}

export function normalizeShareUrl(value) {
  try {
    const url = new URL(value);
    if (!isShareableUrl(url.href)) {
      return value;
    }
    return normalizeAmazonUrl(url);
  } catch {
    return value;
  }
}

// 表示用のURLと状態メッセージをまとめて決める。popup側はこれを描画するだけにする。
export function describeShareUrl(value) {
  if (!isShareableUrl(value)) {
    return {
      shareable: false,
      shareUrl: value,
      status: 'このページは共有できません'
    };
  }

  const shareUrl = normalizeShareUrl(value);
  return {
    shareable: true,
    shareUrl,
    // 生の文字列ではなくURL正規形と比べる。大文字ホストや既定ポートの
    // 違いだけで「整理しました」と表示しないため。
    status: shareUrl === new URL(value).href
      ? '元のURLを使用します'
      : '共有用にURLを整理しました'
  };
}

export function formatShareText(title, url) {
  return `${shareTitle(title)}\n${url}`;
}

export function buildXShareUrl(title, url) {
  const params = new URLSearchParams({
    text: shareTitle(title),
    url
  });
  return `https://x.com/intent/tweet?${params}`;
}

export function buildLineShareUrl(title, url) {
  const params = new URLSearchParams({
    url,
    text: shareTitle(title)
  });
  return `https://social-plugins.line.me/lineit/share?${params}`;
}
