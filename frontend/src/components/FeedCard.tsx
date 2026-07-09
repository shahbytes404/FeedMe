import type {FeedItem} from "../types.ts";
import {getDeliverySourceLabel} from "../lib/feed-display.ts";
import {formatRelativeTime} from "../lib/format.ts";

type FeedCardProps = {
    item: FeedItem;
    showSourcePill?: boolean
}

export function FeedCard({item, showSourcePill = true}: FeedCardProps) {
    const deliverySourceLabel = getDeliverySourceLabel(item);

    return (
        <article className="feed-card">
            <div className="feed-card-header">
                <div>
                    <h3>{item.authorName}</h3>
                    <p>
                        @{item.authorHandle} - {formatRelativeTime(item.createdAt)}
                    </p>
                </div>
                {showSourcePill
                && deliverySourceLabel ? <span className="source-pill">{deliverySourceLabel}</span> : null
                }
            </div>
            <p className="feed-content">{item.content}</p>
        </article>
    )
}