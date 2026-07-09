import type {DashboardData} from "../types.ts";
import {feedApi} from "./api.ts";

const cache = new Map<string, Promise<DashboardData>>();

export function getDashboardResource(activeUserId: string): Promise<DashboardData> {
    const existing = cache.get(activeUserId);
    if (existing) {
        return existing
    }

    const promise = feedApi.getDashboardData(activeUserId)
        .catch((error) => {
            cache.delete(activeUserId);
            throw error;
        });
    cache.set(activeUserId, promise);
    return promise;
}

export function invalidateDashboardResource(activeUserId: string) {
    cache.delete(activeUserId);
}