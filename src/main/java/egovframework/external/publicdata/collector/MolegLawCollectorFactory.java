package egovframework.external.publicdata.collector;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 형사법령 44건 각각에 대한 {@link MolegCriminalLawCollector} 인스턴스를 생성한다.
 * {@link KmaLocationCollectorFactory}와 동일한 이유로 팩토리 패턴 사용 - 44개를 개별
 * {@code @Component} 빈으로 등록하는 대신 팩토리 하나로 관리. 법령이 추가/제외되면
 * {@code moleg-criminal-laws.csv}만 고치면 됨 - 코드 변경 불필요.
 */
@Component
public class MolegLawCollectorFactory {

    private final LawSourcePort lawSourcePort;
    private final List<MolegLaw> laws;

    public MolegLawCollectorFactory(LawSourcePort lawSourcePort, MolegLawListLoader lawListLoader) {
        this.lawSourcePort = lawSourcePort;
        this.laws = lawListLoader.all();
    }

    public List<PublicDataCollector> allLawCollectors() {
        return laws.stream()
            .<PublicDataCollector>map(law -> new MolegCriminalLawCollector(lawSourcePort, law))
            .toList();
    }
}
