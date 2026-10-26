-- 기상특보 최종 테이블의 "같은 행" 기준을 바꾼다 (2단계: 옛 유니크 제약 삭제, 새 코드를 배포하고 정상 기동을 확인한 뒤에 실행). 2026-10-26
--
-- 새 코드(WeatherWarningMapper.insertIfAbsent)가 떠 있어야 한다. 이전 버전 코드는 ON CONFLICT (stn_id, tm_seq, facility_id)를 써서
-- 이 제약이 없으면 기상특보 적재가 오류가 난다. 이 제약이 남아 있는 동안에는 tm_seq 가 재사용된 새 통보문이 건너뛰어진다
-- (오류는 아님). 지우면 그 통보문이 정상적으로 별도 행이 된다.
--
-- 실행:
--   PGOPTIONS='-c search_path=kcais' psql -v ON_ERROR_STOP=1 -f scripts/weather-warning-unique-key-2-drop-old.sql
-- 되돌리기: ALTER TABLE kcais.tb_ext_weather_warning ADD CONSTRAINT uq_weather_warning UNIQUE (stn_id, tm_seq, facility_id);
--          (새 키로 같은 (stn_id, tm_seq, facility_id)의 행이 둘 이상 생긴 뒤에는 실패한다)

BEGIN;

-- 새 인덱스가 정상일 때만 지운다
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
     WHERE i.indrelid = 'kcais.tb_ext_weather_warning'::regclass
       AND c.relname = 'uq_weather_warning_v2' AND i.indisvalid AND i.indisunique
  ) THEN
    RAISE EXCEPTION 'uq_weather_warning_v2 가 없거나 유효하지 않음 - 1단계를 먼저 실행하세요';
  END IF;
END $$;

ALTER TABLE kcais.tb_ext_weather_warning DROP CONSTRAINT uq_weather_warning;

COMMIT;
