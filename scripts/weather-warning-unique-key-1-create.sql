-- 기상특보 최종 테이블의 "같은 행" 기준을 바꾼다 (1단계: 새 유니크 인덱스 추가, 배포 전에 실행). 2026-10-26
--
-- 배경: 지금 키 (stn_id, tm_seq, facility_id)는 tm_seq 가 월을 넘어 재사용되면 새 통보문이 옛 통보문을 덮어쓰고(운영 1,483행),
--       수집이 10분마다 같은 행을 UPDATE 해서 collect_dtm 이 그날 마지막 수집 시각(23:50)으로 바뀐다.
-- 새 기준: (stn_id, tm_seq, tm_fc_dtm, facility_id, md5(raw_json)). 발표 시각이 다르면 다른 통보문, 내용이 같으면 같은 행.
--         코드(WeatherWarningMapper.insertIfAbsent)는 같은 행이면 넣지 않는다.
--
-- 새 인덱스는 옛 키보다 느슨해서 기존 19,250행과 충돌하지 않는다. 옛 제약 uq_weather_warning 은 여기서 건드리지 않는다 -
-- 지금 돌고 있는 이전 버전 코드가 ON CONFLICT (stn_id, tm_seq, facility_id)를 쓰기 때문이다. 코드 배포를 마친 뒤 2단계로 지운다.
--
-- 실행 (CONCURRENTLY 는 트랜잭션 안에서 못 쓰므로 BEGIN/COMMIT 없이):
--   PGOPTIONS='-c search_path=kcais' psql -v ON_ERROR_STOP=1 -f scripts/weather-warning-unique-key-1-create.sql
-- 끝나면 세션/포트포워드를 닫는다.

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_weather_warning_v2
    ON kcais.tb_ext_weather_warning (stn_id, tm_seq, tm_fc_dtm, facility_id, md5(raw_json::text));

-- 확인: indisvalid 가 t 여야 한다. f 이면(만드는 중 실패) DROP INDEX kcais.uq_weather_warning_v2; 후 다시 실행한다.
SELECT c.relname AS index_name, i.indisvalid AS valid, i.indisunique AS "unique"
  FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
 WHERE i.indrelid = 'kcais.tb_ext_weather_warning'::regclass
 ORDER BY c.relname;
