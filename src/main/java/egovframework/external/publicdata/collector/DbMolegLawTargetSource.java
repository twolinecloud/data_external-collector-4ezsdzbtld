package egovframework.external.publicdata.collector;

import egovframework.external.publicdata.collector.mapper.LawTargetMapper;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code public-data.moleg.law-target-source=db}일 때 활성화 - {@code tb_ext_law_target}
 * (admin-db)에서 매 호출마다 최신 목록을 조회한다. 관리자 콘솔에서 목록을 바꾸면 앱 재시작
 * 없이 다음 수집 틱부터 바로 반영된다.
 *
 * <p><b>DB 조회 실패는 절대 위로 전파하지 않는다</b>(2026-08-21) - 이 목록은 매일 새벽 5시
 * 법령 수집 스케줄러가 참조하는데, admin-db가 일시적으로 불안정해도(커넥션 슬롯 고갈 등
 * 이 세션에서 반복 실측됨) 그날 수집만 0건으로 건너뛰고 넘어가야지, 스케줄러나 앱 전체가
 * 죽으면 안 된다(사용자 확정, 2026-08-21).</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.moleg", name = "law-target-source", havingValue = "db")
@RequiredArgsConstructor
public class DbMolegLawTargetSource implements MolegLawTargetSource {

    private static final Logger logger = LogManager.getLogger(DbMolegLawTargetSource.class);

    private final LawTargetMapper mapper;

    @Override
    public List<MolegLaw> current() {
        try {
            return mapper.selectActiveTargets().stream()
                .map(this::toMolegLaw)
                .toList();
        } catch (Exception e) {
            logger.warn("[COLLECT] tb_ext_law_target 조회 실패 - 이번 틱은 0건으로 건너뜀 (admin-db 연결 확인 필요): {}",
                e.getMessage());
            return List.of();
        }
    }

    private MolegLaw toMolegLaw(Map<String, Object> row) {
        return new MolegLaw(
            str(row.get("lawId")), str(row.get("lawName")), str(row.get("mst")), str(row.get("lawType")),
            str(row.get("promulgationDate")), str(row.get("effectiveDate")), str(row.get("ministry")));
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
