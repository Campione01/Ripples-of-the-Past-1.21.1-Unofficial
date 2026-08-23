package com.github.standobyte.jojo.client.entityrender.stand.aura;

import java.util.IdentityHashMap;
import java.util.Map;

final class FrameRequestQueue<T> {
    private final Map<T, Request> requests = new IdentityHashMap<>();

    synchronized void queue(T target, Integer color) {
        if (target == null) {
            return;
        }
        if (color != null) {
            requests.put(target, new Request(color & 0xFFFFFF));
        }
        else {
            requests.putIfAbsent(target, new Request(null));
        }
    }

    synchronized Request consume(T target) {
        return requests.remove(target);
    }

    synchronized void clear() {
        requests.clear();
    }

    synchronized int sizeForTest() {
        return requests.size();
    }

    record Request(Integer color) {}
}
