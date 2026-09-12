// Web Share Target で受け取ったパラメータから、共有するタイトルとURLを決める。
//
// Android の共有シートから渡るデータはアプリごとにばらつきがある。
// Chrome は title と url を分けて渡すが、多くのアプリは text に「タイトル\nURL」や
// URLだけを詰めて渡す。どの形でも同じ結果になるように、URLは全パラメータから探す。

const URL_PATTERN = /https?:\/\/[^\s<>"']+/i;
// 文末の句読点や閉じ括弧はURLの一部ではないことが多いので落とす。
const TRAILING_PUNCTUATION = /[.,;:!?)\]}」』】）。、]+$/;

function findUrl(value) {
  const match = value.match(URL_PATTERN);
  return match ? match[0].replace(TRAILING_PUNCTUATION, '') : '';
}

function stripUrl(value, url) {
  return url ? value.split(url).join(' ') : value;
}

function firstLine(value) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.length > 0) || '';
}

export function parseSharedData({ title = '', text = '', url = '' } = {}) {
  const received = Boolean(title || text || url);

  // url パラメータを優先し、なければ text、最後に title から探す。
  let sharedUrl = '';
  for (const candidate of [url, text, title]) {
    sharedUrl = findUrl(candidate);
    if (sharedUrl) {
      break;
    }
  }

  // タイトルは title パラメータを優先する。ないときは text からURLを除いた残りを使う。
  // title 自身がURLだけのときはタイトルとして意味がないので text 側に回す。
  let sharedTitle = firstLine(stripUrl(title, sharedUrl));
  if (!sharedTitle) {
    sharedTitle = firstLine(stripUrl(text, sharedUrl));
  }

  return { received, title: sharedTitle, url: sharedUrl };
}

export function parseSharedSearch(search) {
  const params = new URLSearchParams(search);
  return parseSharedData({
    title: params.get('title') || '',
    text: params.get('text') || '',
    url: params.get('url') || ''
  });
}
