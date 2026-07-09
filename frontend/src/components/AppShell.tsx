import {useNavigate, useSearchParams} from "react-router-dom";
import {useFeedmeDashboard} from "../hooks/use-feedme-dashboard.ts";
import {useState} from "react";
import {FiSearch} from "react-icons/fi";
import {UserCard} from "./UserCard.tsx";
import {FeedCard} from "./FeedCard.tsx";
import {getDeliverySourceLabel} from "../lib/feed-display.ts";

type AppShellProps = {
    activeUserId: string;
}

/*
left                middle                  right

                    dropdown user
card                Post                    dropdown user

follows             HomeFeed                User Feed

suggested

open Feed ---------------------------------> User feed

 */

export function AppSell({activeUserId}: AppShellProps) {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const {
        users,
        activeUser,
        searchQuery,
        setSearchQuery,
        filteredUsers,
        followingIds,
        selectedTimelineUser,
        selectTimelineUser,
        handleFollowToggle,
        postState,
        isPosting,
        submitPost,
        composeUser,
        composerUserId,
        setComposerUserId,
        homeFeed,
        selectedUserFeed,
        loadMoreHome,
        loadMoreSelectedUser
    } = useFeedmeDashboard(activeUserId);

    const [isSearchOpen, setIsSearchOpen] = useState(false);

    const [followBusyId, setFollowBusyId] = useState<string | null>(null);

    const followedUsers = filteredUsers.filter((user) => user.id !== activeUserId && followingIds.includes(user.id));

    const suggestedUsers = filteredUsers.filter((user) => user.id !== activeUserId && !followingIds.includes(user.id));

    const selectedTimelineSourceLabel =
        selectedUserFeed.items.length > 0 ? getDeliverySourceLabel(selectedUserFeed.items[0]) : null;

    function renderUserCards(userIdGroup: 'followed' | 'suggested') {
        const source = userIdGroup === 'followed' ? followedUsers : suggestedUsers;

        return source.map((user) => (
            <UserCard key={user.id}
                      user={user}
                      isSelectedTimelineUser={user.id === selectedTimelineUser.id}
                      isFollowing={followingIds.includes(user.id)}
                      isBusy={followBusyId === user.id}
                      onSelectTimelineUser={selectTimelineUser}
                      onToggleFollow={async (userId) => {
                          try {
                              setFollowBusyId(userId);
                              await handleFollowToggle(userId);
                          } finally {
                              setFollowBusyId(null);
                          }
                      }}/>
        ));
    }

    return <div className="page-shell">
        <header className="topbar">
            <div className="brand">
                <h1>FeedMe</h1>
                <p>Social Feed</p>
            </div>

            <div className="topbar-actions">
                <label className="toolbar-select-label">
                    Viewer
                    <select
                        className="toolbar-select"
                        value={activeUserId}
                        onChange={(event) => {
                            const params = new URLSearchParams(searchParams);
                            params.set('user', event.target.value);
                            navigate({search: params.toString()});
                        }}
                    >
                        {users.map((user) => (
                                <option key={user.id} value={user.id}>
                                    {user.name}
                                </option>
                            )
                        )}
                    </select>
                </label>
            </div>
        </header>

        <main className="workspace three-column">
            <section className="column-stack">
                <section className="panel roster-panel">
                    <div className="panel-header panel-header-search">
                        <div className="panel-title-with-search">
                            <p className="eyebrow">Following</p>
                            <div className="panel-title-row">
                                <h2>{activeUser.name}</h2>
                                <button
                                    aria-expanded={isSearchOpen}
                                    aria-label={isSearchOpen ? 'Hide Search' : 'Show search'}
                                    className="icon-button"
                                    type="button"
                                    onClick={() => setIsSearchOpen((previous) => !previous)}
                                >
                                    <FiSearch/>
                                </button>
                            </div>
                            {isSearchOpen ? (
                                <input
                                    aria-label="Search people"
                                    className="search-input"
                                    placeholder="Search"
                                    value={searchQuery}
                                    onChange={(event) => setSearchQuery(event.target.value)}
                                />
                            ) : null}
                        </div>
                    </div>

                    <div className="user-list">
                        {followedUsers.length > 0 ? renderUserCards('followed') :
                            <p className="empty-text">No followed users yet.</p>}
                    </div>
                </section>

                <section className="panel roster-panel">
                    <div className="panel-header">
                        <div>
                            <p className="eyebrow">Suggested</p>
                            <h2>Discover people</h2>
                        </div>
                    </div>
                    <div className="user-list">
                        {suggestedUsers.length > 0 ? renderUserCards('suggested') :
                            <p className="empty-text">No suggestions right now.</p>}
                    </div>
                </section>
            </section>

            <section className="column-stack">
                <section className="panel composer-panel">
                    <div className="panel-header">
                        <div>
                            <p className="eyebrow">Create post</p>
                            <h2>What's happening?</h2>
                        </div>
                    </div>

                    <form action={submitPost} className="composer-form">
                        <label className="composer-select-label">
                            Post as
                            <select
                                className="composer-select"
                                name="authorId"
                                value={composerUserId}
                                onChange={
                                    (event) => setComposerUserId(event.target.value)
                                }
                            >
                                {users.map((user) => (
                                    <option key={user.id} value={user.id}>
                                        {user.name} (@{user.handle})
                                    </option>
                                ))}
                            </select>
                        </label>
                        <textarea
                            name="content"
                            maxLength={200}
                            placeholder={`What's on your mind, ${composeUser.name.split(' ')[0]}?`}
                            rows={4}
                        />

                        <div className="composer-footer">
                            <p>{postState.error ?? 'Share an update'}</p>
                            <button className="primary-button" type="submit" disabled={isPosting}>
                                {isPosting ? 'Posting...' : 'Post'}
                            </button>
                        </div>
                    </form>
                </section>

                <section className="panel feed-panel">
                    <div className="panel-header">
                        <div>
                            <p className="eyebrow">Home timeline</p>
                            <h2>{activeUser.name}'s feed</h2>
                        </div>
                    </div>

                    <div className="feed-list">
                        {homeFeed.items.map((item) => (
                            <FeedCard item={item} key={item.postId}/>
                        ))}
                    </div>

                    <div className="feed-footer">
                        <p>{homeFeed.totalItems} posts</p>
                        <button
                            className="secondary-button"
                            type="button"
                            onClick={() => void loadMoreHome()}
                            disabled={!homeFeed.nextCursor}
                        >
                            {homeFeed.nextCursor ? 'Load more posts' : 'No more posts'}
                        </button>
                    </div>
                </section>
            </section>

            <section className="column-stack">
                <section className="panel feed-panel">
                    <div className="panel-header stacked">
                        <div className="panel-title-with-meta">
                            <div className="panel-meta-row">
                                <p className="eyebrow">User timeline</p>
                                {selectedTimelineSourceLabel
                                    ? <span className="source-pill">{selectedTimelineSourceLabel}</span>
                                    : null
                                }
                            </div>
                            <h2>{selectedTimelineUser.name}'s posts</h2>
                        </div>
                        <label className="toolbar-select-label full-width">
                            <select
                                className="toolbar-select"
                                value={selectedTimelineUser.id}
                                onChange={(event) => {
                                    const nextUser = users.find((user) => user.id === event.target.value);
                                    if (nextUser) {
                                        selectTimelineUser(nextUser);
                                    }
                                }}
                            >
                                {users.map((user) => (
                                    <option key={user.id} value={user.id}>{user.name}</option>
                                ))}
                            </select>
                        </label>
                    </div>

                    <div className="feed-list">
                        {selectedUserFeed.items.map((item) => (
                            <FeedCard item={item} key={item.postId} showSourcePill={false}/>
                        ))}
                    </div>

                    <div className="feed-footer">
                        <p>{selectedUserFeed.totalItems} posts</p>
                        <button
                            className="secondary-button"
                            type="button"
                            onClick={() => void loadMoreSelectedUser()}
                            disabled={!selectedUserFeed.nextCursor}
                        >
                            {selectedUserFeed.nextCursor ? 'Load more posts' : 'No more posts'}
                        </button>
                    </div>
                </section>
            </section>
        </main>
    </div>;
}


