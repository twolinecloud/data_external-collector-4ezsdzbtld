package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/** {@code kcais.tb_ext_disaster_msg} (긴급재난문자) - 멱등키 {@code (sn, facility_id)}. */
@Mapper
public interface DisasterMsgMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_disaster_msg
            (disaster_msg_id, sn, facility_id, matched_region_nm, crt_dtm, msg_cn,
             emrg_step_nm, dst_se_nm, rcptn_rgn_nm_raw, reg_de, mdfcn_de,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{sn}, #{facilityId}, #{matchedRegionNm}, #{crtDtm}, #{msgCn},
             #{emrgStepNm}, #{dstSeNm}, #{rcptnRgnNmRaw}, #{regDe}, #{mdfcnDe},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (sn, facility_id) DO UPDATE SET
            matched_region_nm = EXCLUDED.matched_region_nm,
            crt_dtm = EXCLUDED.crt_dtm,
            msg_cn = EXCLUDED.msg_cn,
            emrg_step_nm = EXCLUDED.emrg_step_nm,
            dst_se_nm = EXCLUDED.dst_se_nm,
            rcptn_rgn_nm_raw = EXCLUDED.rcptn_rgn_nm_raw,
            reg_de = EXCLUDED.reg_de,
            mdfcn_de = EXCLUDED.mdfcn_de,
            collect_dtm = EXCLUDED.collect_dtm,
            cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);
}
