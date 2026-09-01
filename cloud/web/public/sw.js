const CACHE = "soccer-manager-v2";

self.addEventListener("install", event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(["/"])));
  self.skipWaiting();
});

self.addEventListener("activate", event => {
  event.waitUntil(Promise.all([
    self.clients.claim(),
    caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key)))),
  ]));
});

self.addEventListener("fetch", event => {
  const request = event.request;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  const cacheableSnapshot = url.pathname.endsWith("/snapshot");
  const navigation = request.mode === "navigate";
  if (!cacheableSnapshot && !navigation) return;
  event.respondWith(
    fetch(request).then(response => {
      if (response.ok) {
        const copy = response.clone();
        event.waitUntil(caches.open(CACHE).then(cache => cache.put(request, copy)));
      }
      return response;
    }).catch(async () => (await caches.match(request)) ?? (await caches.match("/")) ?? Response.error()),
  );
});
