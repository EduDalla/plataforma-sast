CREATE INDEX analyses_user_status_recent_idx
    ON analyses(user_id, status, created_at DESC, id DESC);

CREATE INDEX analyses_user_repository_status_recent_idx
    ON analyses(user_id, repository_owner, repository_name, status, created_at DESC, id DESC);
