-- completion 도메인: 연속 참여(streak) 계산용. 사용자별 참여 날짜를 (user_id, completion_date) 순서로 읽는다.
-- 기존 유니크 (user_id, place_id, completion_date)는 place_id가 중간에 끼어 날짜 정렬/DISTINCT에 쓸 수 없다.
CREATE INDEX idx_completions_user_date ON completions (user_id, completion_date);
