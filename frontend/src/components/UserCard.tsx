import type {UserProfile} from "../types.ts";

type UserCardProps = {
    user: UserProfile;
    isSelectedTimelineUser: boolean;
    isFollowing: boolean;
    isBusy: boolean;
    onSelectTimelineUser: (user: UserProfile) => void
    onToggleFollow: (userId: string) => Promise<void>;
}

export function UserCard({
                             user,
                             isSelectedTimelineUser,
                             isFollowing,
                             isBusy,
                             onSelectTimelineUser,
                             onToggleFollow
                         }: UserCardProps) {
    return (
        <article className={`user-card ${isSelectedTimelineUser ? 'active' : ''}`}>
            <div>
                <div className="user-heading">
                    <h3>{user.name}</h3>
                    {user.hotUser ? <span className="heat-badge">Hot</span> : null}
                </div>
                <p className="handle">@{user.handle}</p>
                <p className="bio">{user.bio}</p>
            </div>

            <div className="user-card-actions">
                <button className="ghost-button" type="button"
                        onClick={() => onSelectTimelineUser(user)}
                >
                    {isSelectedTimelineUser ? 'Viewing' : 'Open Feed'}
                </button>
                <button className="primary-button" type="button" disabled={isBusy}
                        onClick={() => void onToggleFollow(user.id)}
                >
                    {isBusy ? 'Updating...' : isFollowing ? 'Unfollow' : 'Follow'}
                </button>
            </div>
        </article>
    );
}