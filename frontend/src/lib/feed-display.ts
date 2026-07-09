import type {FeedItem} from "../types.ts";

export function getDeliverySourceLabel(item: FeedItem): string | null {
    const strategy = item.deliveryStrategy.toLowerCase();

    if (strategy.includes('hybrid')) {
        return 'hybrid pull'
    }

    if (strategy.includes('fanout') || strategy.includes('fan-out')) {
        return 'fan-out';
    }

    return null;
}