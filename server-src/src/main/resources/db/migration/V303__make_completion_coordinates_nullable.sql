-- completion 도메인: 회원 탈퇴 시 제출 좌표(개인정보)를 지우려고 NULL을 허용한다(결정 D4: 기록과 포인트는 통계용으로 남기고 개인정보는 삭제).
-- 활성 사용자의 새 기록은 계속 값이 들어간다(서비스가 항상 채운다). 읽는 코드는 없다(이상 징후 탐지용으로만 남겨 둔 값).
ALTER TABLE completions ALTER COLUMN lat DROP NOT NULL;
ALTER TABLE completions ALTER COLUMN lng DROP NOT NULL;
ALTER TABLE completions ALTER COLUMN accuracy_m DROP NOT NULL;
