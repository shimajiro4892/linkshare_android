// インストール要件を満たすための最小限のService Worker。
// ネットワーク優先で取得し、オフライン時だけキャッシュを返す。更新のたびにバージョンを
// 上げる必要はない（常に最新をネットワークから取り、成功したらキャッシュを差し替える）。
const CACHE_NAME = 'link-share-shell';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) {
    return;
  }

  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME);
    try {
      const response = await fetch(request);
      if (response.ok) {
        // 共有パラメータ付きのURLも index.html と同じ内容なので、クエリを無視して1つだけ保存する。
        await cache.put(cacheKey(request), response.clone());
      }
      return response;
    } catch (error) {
      const cached = await cache.match(cacheKey(request));
      if (cached) {
        return cached;
      }
      throw error;
    }
  })());
});

function cacheKey(request) {
  const url = new URL(request.url);
  url.search = '';
  return url.href;
}
