-- 검찰청 폐지·공소청/중대범죄수사청 신설(2026-10-02 시행)에 따른 대체 법령을 수집 대상에 추가하고,
-- 시행 예정이라 현행 조회가 비어 잘못 비활성화했던 1건을 되살린다. (2026-10-26)
--
-- 1. 015135 친일반민족행위자 재산의 국가귀속 등에 관한 특별법: 폐지가 아니라 2026-12-03 시행 예정 개정본이다.
--    active_yn 을 다시 'Y' 로 돌린다(시행일 전까지는 수집기가 시행 미도래로 자동 제외한다).
-- 2. 신규 9건(공소청 직제, 중대범죄수사청 법령 7건, 검사와 특별사법경찰관의 상호협력에 관한 규정).
--    법제처 lawSearch/lawService(eflaw, 이름 조회)로 모두 조회되는 것을 확인했다. 이미 있으면 건너뛴다.
--
-- 실행: PGOPTIONS='-c search_path=kcais' psql -v ON_ERROR_STOP=1 -f scripts/add-law-targets.sql
BEGIN;

UPDATE tb_ext_law_target SET active_yn = 'Y'
 WHERE law_id = '015135' AND doc_type_cd = 'LAW' AND active_yn = 'N';

INSERT INTO tb_ext_law_target
  (law_id, law_nm, mst, law_type_cd, ministry_nm, promulgation_de, effective_de, active_yn, doc_type_cd)
VALUES
  ('015090', '중대범죄수사청 조직 및 운영에 관한 법률', '290127', '법률', '중대범죄수사청', '20260929', '20261002', 'Y', 'LAW'),
  ('015190', '중대범죄수사청 조직 및 운영에 관한 법률 시행령', '290487', '대통령령', '중대범죄수사청', '20260929', '20261002', 'Y', 'LAW'),
  ('015192', '중대범죄수사청과 그 소속기관 직제', '288727', '대통령령', '중대범죄수사청,행정안전부', '20260825', '20261002', 'Y', 'LAW'),
  ('015218', '중대범죄수사청과 그 소속기관 직제 시행규칙', '289991', '행정안전부령', '중대범죄수사청', '20260923', '20261002', 'Y', 'LAW'),
  ('015193', '중대범죄수사청수사관 임용령', '288961', '대통령령', '중대범죄수사청', '20260825', '20261002', 'Y', 'LAW'),
  ('015230', '중대범죄수사청수사관 임용령 시행규칙', '291017', '행정안전부령', '중대범죄수사청', '20261002', '20261002', 'Y', 'LAW'),
  ('015231', '중대범죄수사청 사건사무규칙', '291031', '행정안전부령', '중대범죄수사청', '20261002', '20261002', 'Y', 'LAW'),
  ('015221', '공소청과 그 소속기관 직제', '290251', '대통령령', '행정안전부', '20260929', '20261002', 'Y', 'LAW'),
  ('015224', '검사와 특별사법경찰관의 상호협력에 관한 규정', '290601', '대통령령', '법무부', '20260929', '20261002', 'Y', 'LAW')
ON CONFLICT (law_id, doc_type_cd) DO NOTHING;

DO $$
DECLARE a int; b int; c int;
BEGIN
  SELECT count(*) INTO a FROM tb_ext_law_target WHERE active_yn = 'Y';
  SELECT count(*) INTO b FROM tb_ext_law_target WHERE active_yn = 'N';
  SELECT count(*) INTO c FROM tb_ext_law_target;
  IF a <> 496 OR b <> 4 OR c <> 500 THEN
    RAISE EXCEPTION '건수 불일치: 사용 %, 비활성 %, 전체 % (기대 496/4/500)', a, b, c;
  END IF;
END $$;

COMMIT;
