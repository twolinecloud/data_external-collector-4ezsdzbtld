-- 법령 대상 목록 정리 (2026-10-23): 공식 명칭으로 정정 15건 + 폐지/대체된 법령 5건 비활성화.
-- 대상은 법제처 lawService.do 에서 ID(행정규칙은 MST)로 읽기 전용 조회해 확인했다. 행은 지우지 않는다.
-- 실행: PGOPTIONS='-c search_path=kcais' psql -v ON_ERROR_STOP=1 -f scripts/cleanup-law-target.sql

BEGIN;
UPDATE tb_ext_law_target SET law_nm = '검사, 공소청 소속 직원 및 특별사법경찰관리 등의 개인정보 처리에 관한 규정' WHERE law_id = '012832' AND doc_type_cd = 'LAW' AND law_nm = '검찰 및 특별사법경찰관리 등의 개인정보 처리에 관한 규정';
UPDATE tb_ext_law_target SET law_nm = '공소청근무 규칙' WHERE law_id = '006201' AND doc_type_cd = 'LAW' AND law_nm = '검찰근무 규칙';
UPDATE tb_ext_law_target SET law_nm = '공소청보고사무규칙' WHERE law_id = '006202' AND doc_type_cd = 'LAW' AND law_nm = '검찰보고사무규칙';
UPDATE tb_ext_law_target SET law_nm = '공소청보존사무규칙' WHERE law_id = '006203' AND doc_type_cd = 'LAW' AND law_nm = '검찰보존사무규칙';
UPDATE tb_ext_law_target SET law_nm = '공소청사건사무규칙' WHERE law_id = '006204' AND doc_type_cd = 'LAW' AND law_nm = '검찰사건사무규칙';
UPDATE tb_ext_law_target SET law_nm = '공소청압수물사무규칙' WHERE law_id = '006205' AND doc_type_cd = 'LAW' AND law_nm = '검찰압수물사무규칙';
UPDATE tb_ext_law_target SET law_nm = '공소청 관인 관리 규칙' WHERE law_id = '006208' AND doc_type_cd = 'LAW' AND law_nm = '검찰청 관인 관리 규칙';
UPDATE tb_ext_law_target SET law_nm = '광역공소청 검사의 지방공소청 소재지에서의 사무처리에 관한 규칙' WHERE law_id = '006269' AND doc_type_cd = 'LAW' AND law_nm = '고등검찰청 검사의 지방검찰청 소재지에서의 사무처리에 관한 규칙';
UPDATE tb_ext_law_target SET law_nm = '공소청 검사급 이상 검사의 범위에 관한 규정' WHERE law_id = '010391' AND doc_type_cd = 'LAW' AND law_nm = '대검찰청 검사급 이상 검사의 보직범위에 관한 규정';
UPDATE tb_ext_law_target SET law_nm = '공소청의 위치와 각급 공소청 및 지청의 명칭 및 위치에 관한 규정' WHERE law_id = '003301' AND doc_type_cd = 'LAW' AND law_nm = '대검찰청의 위치와 각급 검찰청의 명칭 및 위치에 관한 규정';
UPDATE tb_ext_law_target SET law_nm = '공소청 인권보호사무규칙' WHERE law_id = '013600' AND doc_type_cd = 'LAW' AND law_nm = '인권보호수사규칙';
UPDATE tb_ext_law_target SET law_nm = '자유형등에 관한 공소청집행사무규칙' WHERE law_id = '006206' AND doc_type_cd = 'LAW' AND law_nm = '자유형등에 관한 검찰집행사무규칙';
UPDATE tb_ext_law_target SET law_nm = '재산형등에 관한 공소청집행사무규칙' WHERE law_id = '006207' AND doc_type_cd = 'LAW' AND law_nm = '재산형 등에 관한 검찰 집행사무규칙';
UPDATE tb_ext_law_target SET law_nm = '검사와 특별사법경찰관의 상호협력에 관한 규칙' WHERE law_id = '013978' AND doc_type_cd = 'LAW' AND law_nm = '특별사법경찰관리에 대한 검사의 수사지휘 및 특별사법경찰관리의 수사준칙에 관한 규칙';
UPDATE tb_ext_law_target SET law_nm = '교정정책자문위원회 운영규정' WHERE law_id = '86352' AND doc_type_cd = 'ADMIN_RULE' AND law_nm = '교정정책자문위원회 운영 규정';

UPDATE tb_ext_law_target SET active_yn = 'N' WHERE law_id = '013872' AND doc_type_cd = 'LAW' AND law_nm = '검사의 수사개시 범죄 범위에 관한 규정';
UPDATE tb_ext_law_target SET active_yn = 'N' WHERE law_id = '002132' AND doc_type_cd = 'LAW' AND law_nm = '검찰청 사무기구에 관한 규정';
UPDATE tb_ext_law_target SET active_yn = 'N' WHERE law_id = '001286' AND doc_type_cd = 'LAW' AND law_nm = '검찰청법';
UPDATE tb_ext_law_target SET active_yn = 'N' WHERE law_id = '010637' AND doc_type_cd = 'LAW' AND law_nm = '전문수사자문위원 운영규칙';
UPDATE tb_ext_law_target SET active_yn = 'N' WHERE law_id = '015135' AND doc_type_cd = 'LAW' AND law_nm = '친일반민족행위자 재산의 국가귀속 등에 관한 특별법';

DO $$
DECLARE a int; b int;
BEGIN
  SELECT count(*) INTO a FROM tb_ext_law_target WHERE active_yn = 'N';
  SELECT count(*) INTO b FROM tb_ext_law_target;
  IF a <> 5 OR b <> 491 THEN RAISE EXCEPTION '건수 불일치: 비활성 %, 전체 %', a, b; END IF;
END $$;

COMMIT;
