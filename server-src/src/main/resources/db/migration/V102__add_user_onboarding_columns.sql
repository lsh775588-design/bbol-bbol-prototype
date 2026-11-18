-- user 도메인: 온보딩의 연령대와 성별(명세: ageGroup, gender). NULL = 아직 입력 안 함, UNDISCLOSED = 비공개를 고름.
-- 값 검증은 애플리케이션(enum)이 한다. DB에는 평범한 VARCHAR로 둔다.
ALTER TABLE users ADD COLUMN age_group VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN gender VARCHAR(20) NULL;
